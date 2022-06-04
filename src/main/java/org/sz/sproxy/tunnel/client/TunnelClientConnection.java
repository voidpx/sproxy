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
package org.sz.sproxy.tunnel.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import org.sz.sproxy.Attachable;
import org.sz.sproxy.ChannelHandler;
import org.sz.sproxy.Context;
import org.sz.sproxy.SocksException;
import org.sz.sproxy.StateManager;
import org.sz.sproxy.Writable;
import org.sz.sproxy.impl.NioConnection;
import org.sz.sproxy.impl.Utils;
import org.sz.sproxy.tunnel.Crypto;
import org.sz.sproxy.tunnel.Tunnel;
import org.sz.sproxy.tunnel.TunnelContext;
import org.sz.sproxy.tunnel.TunneledConnection;
import org.sz.sproxy.tunnel.secure.SecuredConnectionHelper;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Sam Zheng
 *
 */
@Slf4j
public class TunnelClientConnection extends NioConnection<SocketChannel, TunnelClientConnection>
		implements TunnelClient, Tunnel, Attachable {
	
	volatile boolean st = false;

	SecuredConnectionHelper helper;

	Map<Integer, TunneledConnection> proxied;
	
	InetSocketAddress addr;

	AtomicInteger channelId;

	@Getter
	TunnelClientCallback callback;

	Object attachment;
	
	TunnelClientSubst subst;
	
	@Getter
	@Setter
	int id;

	public TunnelClientConnection(Context context, TunnelClientCallback callback) throws IOException {
		super(context, SocketChannel.open());
		TunnelClientConfiguration config = (TunnelClientConfiguration) context.getConfiguration();
		key.interestOpsOr(SelectionKey.OP_CONNECT);
		channelId = new AtomicInteger(new Random().nextInt());
		this.callback = callback;
		helper = new SecuredConnectionHelper(channel::read, context);
		proxied = new ConcurrentHashMap<>();
		addr = new InetSocketAddress(config.getServerHost(), config.getServerPort());
		channel.connect(addr);
		selector.wakeup();

	}
	
	@Override
	protected StateManager createStateManager() {
		return new TunnelClientStateManager();
	}

	void setCrypto(Crypto crypto) {
		helper.setCrypto(crypto);
	}

	@Override
	protected void handleConnect() throws IOException {
		if (channel.finishConnect()) {
			moveTo(getStateManager().getInitState(), null);
		} else {
			throw new ConnectionNotFinished();
		}
	}
	
	public void connected() {
		callback.connected(this);
	}

	@Override
	public RelayedConnection tunnel(RelayedConnection connection) {
		int id = this.channelId.getAndIncrement();
		connection.setId(id);
		proxied.put(id, connection);
		callback.channelAdded(this, connection);
		return connection;
	}
	
	void renew() throws IOException {
		if (!getState().getName().equals(TunnelClientConnectedState.NAME)) {
			throw new SocksException("Invalid state");
		}
		callback.renewing(this);
		TunnelClientSubst st = new TunnelClientSubst(getContext(), addr, (t) -> {
			sendSTM(t);
		}, this);
		log.debug("creating substitution tunnel client {}", st);
	}
	
	synchronized void switchTunnel() {
		Objects.requireNonNull(subst, "subst is null");
		SelectionKey oldKey = key;
		SocketChannel oldChannel = channel;
		channel = subst.getChannel();
		key = subst.getKey();
		key.attach(this);
		helper.setChannel(channel::read);
		
		oldKey.attach(null);
		try {
			oldChannel.close();
		} catch (IOException e) {
			log.debug("Error closing old tunnel channel", e);
		}
		st = false;
		callback.connected(this);
		subst.switchDone();
		subst = null;
		notifyAll();
	}
	
	private synchronized void sendSTM(TunnelClientSubst s) {
		subst = s;
		try {
			getWriter(Tunnel.STM, this::write).write(ByteBuffer.wrap(new byte[0]));
			st = true;
		} catch (IOException e) {
			throw new SocksException(e);
		}
	}
	
	private synchronized void internalWrite(ByteBuffer buffer) throws IOException {
		while (st) {
			try {
				wait(500);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				log.debug("Interrupted while waiting for tunnel switching", e);
			}
		}
		super.write(buffer);
	}

	@Override
	public int read(ByteBuffer buffer) throws IOException {
		return helper.read(buffer);
	}

	@Override
	public synchronized void write(ByteBuffer buffer) throws IOException {
		if (buffer.remaining() == 0) {
			return;
		}
		helper.write(buffer, this::internalWrite);
	}

	@Override
	public Writable getPlainWriter() {
		return super::write;
	}

	@Override
	public TunneledConnection getTunneledConnection(int id) {
		return proxied.get(id);
	}

	@Override
	protected void closeInternal() throws IOException {
		log.info("closing tunnel client");
		List<TunneledConnection> list = new ArrayList<>(proxied.values());
		proxied.clear();
		list.forEach(TunneledConnection::close);
		log.info("tunnel client closed");
	}

	@Override
	public void close() {
		callback.closing(this);
		super.close();
	}

	@Override
	public void close(RelayedConnection conn) {
		closeProxied(conn.getId());
	}

	void closeProxied(int id) {
		if (!proxied.containsKey(id)) {
			return;
		}
		RelayedConnection conn = (RelayedConnection) proxied.get(id);
		try {
			TunnelClient.super.closeChannel(id);
		} finally {
			proxied.remove(id);
			callback.channelClosed(this, conn);
		}
	}
	
	@Override
	public void closeChannel(int id) {
		closeProxied(id);
	}

	@Override
	public void pump(RelayedConnection client) throws IOException {
		Utils.pump(context, client, getDataWriter(client.getId(), this::write), client::close);
	}

	public void connect(RelayedConnection client, ByteBuffer connInfo,
			BiConsumer<ChannelHandler<SocketChannel>, Writable> callback) throws IOException {
		client.attach(callback);
		getWriter(client, CONNECTRQ, this).write(connInfo);
	}
	
	@Override
	public int getRelayedCount() {
		return proxied.size();
	}

	@Override
	public void attach(Object attachment) {
		this.attachment = attachment;
	}

	@Override
	public Object getAttachment() {
		return attachment;
	}
	
	@Override
	public Executor getExecutor() {
		return ((TunnelContext)getContext()).getHighPrioExecutor();
	}
}
