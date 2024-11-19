/*
 * Copyright (c) 2022 Sam Zheng                                            
 *                                                                         
 * Licensed under the Apache License, Version 2.0 (the "License");         
 * you may not use this file except in compliance with the License.        
 * You may obtain a copy of the License at                                 
 *                                                                         
 *     http://www.apache.org/licenses/LICENSE-2.0                          
 *                                                                         
 * Unless required by applicable law or agreed to in writing, software     
 * distributed under the License is distributed on an "AS IS" BASIS,       
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and     
 * limitations under the License.                                          
 */
package org.sz.sproxy.impl;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.NetworkChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.sz.sproxy.Context;
import org.sz.sproxy.Flushable;
import org.sz.sproxy.Readable;
import org.sz.sproxy.State;
import org.sz.sproxy.StateManager;
import org.sz.sproxy.StatefulHandler;
import org.sz.sproxy.Writable;
import org.sz.sproxy.WriteDoneNoticeable;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Sam Zheng
 *
 */
@Slf4j
public abstract class NioConnection<C extends SelectableChannel & ByteChannel & NetworkChannel, H extends StatefulHandler<C, H>>
		implements NioChannelHandler<C>, StatefulHandler<C, H>, Readable, Writable, Flushable {

	ConcurrentHashMap<Thread, Thread> workers = new ConcurrentHashMap<>();

	AtomicBoolean closing = new AtomicBoolean(false);

	protected Queue<ByteBuffer> outBuffers;

	protected Context context;

	protected C channel;

	protected SelectionKey key;

	protected Selector selector;

	@Getter
	@Setter
	protected State<C, H> state;
	
	@Getter
	protected StateManager stateManager;
	
	private List<WriteDoneNoticeable> wns = new ArrayList<>();
	
	private volatile int nseq = 0;
	
	@Override
	public synchronized void writeDone(Writable w) {
		wakeup(WR.DONE, nseq);
		nseq++;
	}
	
	@Override
	public void addWN(WriteDoneNoticeable wn) {
		if (!wns.contains(wn)) {
			wns.add(wn);
		}
	}

	public NioConnection(Context context, C channel) throws IOException {
		this.context = context;
		this.channel = channel;
		stateManager = createStateManager();
		outBuffers = new LinkedList<>();
		selector = context.getSelector();
		channel.configureBlocking(false);
		key = channel.register(selector, SelectionKey.OP_READ, this);
	}
	
	protected abstract StateManager createStateManager();
	
	@SuppressWarnings("unchecked")
	@Override
	public State<C, H> moveTo(String state, Object initInfo) {
		return getStateManager().moveTo(state, (H)this, initInfo);
	}

	@Override
	public C getChannel() {
		return channel;
	}

	@Override
	public Context getContext() {
		return context;
	}

	@Override
	public int read(ByteBuffer buffer) throws IOException {
		return getChannel().read(buffer);
	}

	@Override
	public synchronized WR write(ByteBuffer buffer) throws IOException {
		outBuffers.add(buffer);
		return flushOutput();
	}

	protected synchronized WR flushOutput() throws IOException {
		WR ret = WR.DONE;
		while (!outBuffers.isEmpty()) {
			ByteBuffer b = outBuffers.peek();
			int r = b.remaining();
			if (r > 0) {
				int n = channel.write(b);
				if (n < r) {
					if (log.isDebugEnabled()) {
						StringWriter sw = new StringWriter();
						PrintWriter pw = new PrintWriter(sw);
						new Throwable().printStackTrace(pw);
						log.debug("NOT fully written to {}: out: {}, remaining: {}, trace:\n{}",
								channel, n, r - n, sw.toString());
					}
					ret = WR.AGAIN;
					break;
				}
				outBuffers.remove(); // fully written
			} else {
				if (log.isDebugEnabled()) {
					log.debug("b.remaining <= 0, BUG!");
				}
				outBuffers.remove(); // bug
			}
		}
		if (ret == WR.AGAIN) {
			setWriteInterest();
		}
		return ret;
	}

	private Runnable closeThis() {
		return Utils.EXEC_WITH_TH_NAME.apply("closing_" + getChannel().toString(), () -> {
			// wait until all workers finish
			while (!workers.isEmpty()) {
				synchronized (workers) {
					try {
						workers.wait();
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						log.debug("Interrupted while waiting for workers to shutdown for " + getChannel());
					}
				}
			}
			// now close
			try {
				channel.close();
				closeInternal();
			} catch (IOException e) {
				log.error("Error closing channel " + getChannel(), e);
			}
		});
	}

	@Override
	public void close() {
		if (!closing.getAndSet(true)) { // initiate closing
			this.getExecutor().execute(closeThis());
		}

	}

	protected void closeInternal() throws IOException {

	}

	private synchronized void setWriteInterest() {
		if (!key.isValid()) {
			return;
		}
		if (outBuffers.isEmpty()) {
			key.interestOpsAnd(~SelectionKey.OP_WRITE);
		} else if ((key.interestOps() & SelectionKey.OP_WRITE) == 0) {
			key.interestOpsOr(SelectionKey.OP_WRITE);
		}
	}

	@Override
	public int handle(int ops) throws IOException {
		if (closing.get()) {
			return -1; // closing
		}
		Thread cur = Thread.currentThread();
		workers.put(cur, cur);
		try {
			int seq = nseq;
			WR wr = handleInternal(ops);
			wakeup(wr, seq);
			return 0; // other code not defined
		} finally {
			workers.remove(cur);
			synchronized (workers) {
				workers.notifyAll();
			}
		}
	}

	private synchronized void wakeup(WR wr, int seq) {
		if (!key.isValid()) {
			return;
		}
		if (wr != null) {
			if (wr == WR.AGAIN && seq == nseq) { // write end jam, don't read until further notice
				key.interestOpsAnd(~SelectionKey.OP_READ);
			} else {
				if (log.isDebugEnabled()) {
					log.debug("wakeup: WR: {}, seq:{}, nseq: {}", wr, seq, nseq);
				}
				key.interestOpsOr(SelectionKey.OP_READ);
			}
		} else {
			if (log.isDebugEnabled()) {
				log.debug("NOT woken up to handle READ");
			}
		}
		selector.wakeup();
	}
	
	protected static class ConnectionNotFinished extends IOException {

		private static final long serialVersionUID = 1L;
		
		public ConnectionNotFinished() {

		}
		
	}

	protected WR handleInternal(int ops) throws IOException {
		if ((ops & SelectionKey.OP_WRITE) > 0) {
			if (log.isDebugEnabled()) {
				log.debug("flush remaining from last time");
			}
			WR wr = flushOutput();
			if (wr == WR.DONE) { // there was remaining, try flush first
				if (log.isDebugEnabled()) {
					log.debug("flushed remaining from last time, notifying {}", wns);
				}
				wns.forEach(w -> w.writeDone(this));
			} else {
				if (log.isDebugEnabled()) {
					log.debug("unable to flush remaining from last time, wait another round");
				}
			}
		}
		if ((ops & SelectionKey.OP_CONNECT) > 0) {
			try {
				handleConnect();
			} catch (ConnectionNotFinished e) {
				if (key.isValid()) {
					key.interestOpsOr(SelectionKey.OP_CONNECT);
				}
				return WR.DONE;
			}
		}
		WR wr = null;
		if ((ops & SelectionKey.OP_READ) > 0) {
			wr = handleRead(ops);
		}
		flushOutput();
		return wr;
	}

	protected void handleConnect() throws IOException {

	}

	@SuppressWarnings("unchecked")
	protected WR handleRead(int ops) throws IOException {
		Objects.requireNonNull(state, "unknown state!");
		return state.process((H) this);
	}

	@Override
	public WR flush() throws IOException {
		return flushOutput();
	}
}
