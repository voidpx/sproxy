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
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.NetworkChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.sz.sproxy.Context;
import org.sz.sproxy.Readable;
import org.sz.sproxy.State;
import org.sz.sproxy.StateManager;
import org.sz.sproxy.StatefulHandler;
import org.sz.sproxy.Writable;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Sam Zheng
 *
 */
@Slf4j
public abstract class NioConnection<C extends SelectableChannel & ByteChannel & NetworkChannel, H extends StatefulHandler<C, H>>
		implements NioChannelHandler<C>, StatefulHandler<C, H>, Readable, Writable {

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
	public synchronized void write(ByteBuffer buffer) throws IOException {
		outBuffers.add(buffer);
		flushOutput();
	}

	protected synchronized void flushOutput() throws IOException {
		while (!outBuffers.isEmpty()) {
			ByteBuffer b = outBuffers.peek();
			int r = b.remaining();
			if (r > 0) {
				int n = channel.write(b);
				if (n < r) {
					break;
				}
				outBuffers.remove(); // fully written
			} else {
				outBuffers.remove(); // bug
			}
		}
		if (!outBuffers.isEmpty()) {
			setWriteInterest();
		}
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
			context.getTaskExecutor().execute(closeThis());
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
		} else {
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
			handleInternal(ops);
			wakeup(ops);
			return 0; // other code not defined
		} finally {
			workers.remove(cur);
			synchronized (workers) {
				workers.notifyAll();
			}
		}
	}

	private synchronized void wakeup(int ops) {
		if (!key.isValid()) {
			return;
		}
		if ((ops & SelectionKey.OP_READ) > 0) { // read was masked out, restore it
			key.interestOpsOr(SelectionKey.OP_READ);
		}
		selector.wakeup();
	}
	
	protected static class ConnectionNotFinished extends IOException {

		private static final long serialVersionUID = 1L;
		
		public ConnectionNotFinished() {

		}
		
	}

	protected void handleInternal(int ops) throws IOException {
		if ((ops & SelectionKey.OP_CONNECT) > 0) {
			try {
				handleConnect();
			} catch (ConnectionNotFinished e) {
				if (key.isValid()) {
					key.interestOpsOr(SelectionKey.OP_CONNECT);
				}
				return;
			}
		}
		if ((ops & SelectionKey.OP_READ) > 0) {
			handleRead(ops);
		}
		flushOutput();
	}

	protected void handleConnect() throws IOException {

	}

	@SuppressWarnings("unchecked")
	protected void handleRead(int ops) throws IOException {
		Objects.requireNonNull(state, "unknown state!");
		state.process((H) this);
	}

}
