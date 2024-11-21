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
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.sz.sproxy.Attachable;
import org.sz.sproxy.ChannelHandler;
import org.sz.sproxy.Context;
import org.sz.sproxy.StateManager;
import org.sz.sproxy.Writable;
import org.sz.sproxy.impl.NioConnection;
import org.sz.sproxy.impl.Utils;
import org.sz.sproxy.tunnel.Crypto;
import org.sz.sproxy.tunnel.Tunnel;
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
	
	SecuredConnectionHelper helper;

	Map<Integer, TunneledConnection> proxied;
	
	InetSocketAddress addr;

	AtomicInteger channelId;

	@Getter
	TunnelClientCallback callback;

	Object attachment;
	
	@Getter
	@Setter
	int id;
	
	private volatile boolean connected;

	public TunnelClientConnection(Context context, TunnelClientCallback callback) throws IOException {
		super(context, SocketChannel.open());
		TunnelClientConfiguration config = (TunnelClientConfiguration) context.getConfiguration();
		key.interestOpsOr(SelectionKey.OP_CONNECT);
		channelId = new AtomicInteger(new Random().nextInt());
		this.callback = callback;
		helper = new SecuredConnectionHelper(channel::read, context);
		proxied = new ConcurrentHashMap<>();
		addr = new InetSocketAddress(config.getServerHost(), config.getServerPort());
		log.debug("starting tunnel connection: {}", channel);
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
		try {
			if (channel.finishConnect()) {
				moveTo(getStateManager().getInitState(), null);
			} else {
				throw new ConnectionNotFinished();
			}
		} catch (IOException e) {
			callback.connectError(this);
			throw e;
		}
	}
	
	void connected() {
		callback.connected(this);
		connected = true;
	}
	
	@Override
	public boolean isConnected() {
		return connected;
	}

	@Override
	public RelayedConnection tunnel(RelayedConnection connection) {
		int id = this.channelId.getAndIncrement();
		connection.setId(id);
		proxied.put(id, connection);
		callback.channelAdded(this, connection);
		addWN(connection);
		log.debug("successfully tunneled connection: {}", connection.getChannel());
		return connection;
	}
	
	private synchronized WR internalWrite(ByteBuffer buffer) throws IOException {
		return super.write(buffer);
	}

	@Override
	public int read(ByteBuffer buffer) throws IOException {
		return helper.read(buffer);
	}

	@Override
	public synchronized WR write(ByteBuffer buffer) throws IOException {
		if (buffer.remaining() == 0) {
			return WR.DONE;
		}
		return helper.write(buffer, this::internalWrite);
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
		super.close();
		callback.closing(this);
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
		removeWN(conn);
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
	public WR pump(RelayedConnection client) throws IOException {
		return Utils.pump(context, client, getDataWriter(client.getId(), this::write), client::close);
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
	
	void cleanupOrphaned() {
		List<TunneledConnection> dead = proxied.entrySet().stream().map(e -> e.getValue())
				.filter(c -> !c.getChannel().isOpen()).collect(Collectors.toList());
		if (log.isDebugEnabled() && !dead.isEmpty()) {
			log.debug("clean up orphaned connetions from tunnel: {}", dead);
		}
		dead.forEach(c -> c.close());
	}
}
