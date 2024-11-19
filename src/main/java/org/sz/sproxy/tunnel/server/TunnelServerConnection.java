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
package org.sz.sproxy.tunnel.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import org.sz.sproxy.ChannelHandler;
import org.sz.sproxy.Context;
import org.sz.sproxy.StateManager;
import org.sz.sproxy.Writable;
import org.sz.sproxy.impl.NioConnection;
import org.sz.sproxy.tunnel.Crypto;
import org.sz.sproxy.tunnel.Tunnel;
import org.sz.sproxy.tunnel.TunneledConnection;
import org.sz.sproxy.tunnel.secure.SecuredConnectionHelper;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Sam Zheng
 *
 */
@Slf4j
public class TunnelServerConnection extends NioConnection<SocketChannel, TunnelServerConnection> implements Tunnel {
	
	private static final AtomicInteger ID = new AtomicInteger(new Random().nextInt());
	
	volatile TunnelServerConnection newTunnel;

	SecuredConnectionHelper helper;

	Map<Integer, TunneledConnection> remotes;
	
	private volatile long lastActive;
	
	private int maxIdle;
	
	@Getter
	private int id;
	
	public TunnelServerConnection(Context context, SocketChannel channel) throws IOException {
		super(context, channel);
		helper = new SecuredConnectionHelper(channel::read, context);
		remotes = new ConcurrentHashMap<>();
		id = ID.getAndIncrement();
		lastActive = System.currentTimeMillis();
		maxIdle = getContext().getConfiguration().getInt("max_idle_time", 5 * 60 * 1000); // 5 min idle
		context.getConnectionListeners().forEach(l -> l.connectionEstablished(this));
		moveTo(getStateManager().getInitState(), null);
	}

	@Override
	protected StateManager createStateManager() {
		return new TunnelServerStateManager();
	}
	
	public void setCrypto(Crypto crypto) {
		helper.setCrypto(crypto);
	}

	@Override
	public int read(ByteBuffer buffer) throws IOException {
		int r = helper.read(buffer);
		lastActive = System.currentTimeMillis();
		return r;
	}
	
	private synchronized WR internalWrite(ByteBuffer buffer) throws IOException {
		return super.write(buffer);
	}
	
	@Override
	public synchronized WR write(ByteBuffer buffer) throws IOException {
		if (buffer.remaining() == 0) {
			return WR.DONE;
		}
		WR ret = helper.write(buffer, this::internalWrite);
		lastActive = System.currentTimeMillis();
		return ret;
	}
	
	@Override
	public Writable getPlainWriter() {
		return super::write;
	}
	
	public void connected(TunneledConnection remote) throws IOException {
		log.debug("remote connected: {}", remote.getId());
		remotes.put(remote.getId(), remote);
		addWN(remote);
		getWriter(remote, CONNECTRP, this::write).write(ByteBuffer.wrap(new byte[0]));
	}
	
	@Override
	public TunneledConnection getTunneledConnection(int id) {
		return remotes.get(id);
	}
	
	void closeTunneled(int id) {
		if (!remotes.containsKey(id)) {
			return;
		}
		try {
			Tunnel.super.closeChannel(id);
		} finally {
			log.debug("channel {} closed", id);
			remotes.remove(id);
		}
	}
	
	@Override
	public void close() {
		context.getConnectionListeners().forEach(l -> l.connectionClosing(this));
		super.close();
	}
	
	@Override
	public void closeChannel(int id) {
		closeTunneled(id);
	}

	public void connectRemote(InetSocketAddress address,
			BiConsumer<ChannelHandler<SocketChannel>, Writable> connected, Object ctx) throws IOException {
		int channelId = (int) ctx;
		log.debug("connecting remote, id: {}, address: {}", channelId, address);
		new ServerRemoteConnection(context, this, address, connected, channelId);
	}

	@Override
	public void closeInternal() {
		log.info("closing tunnel server connection");
		List<TunneledConnection> list = new ArrayList<>(remotes.values());
		remotes.clear();
		list.forEach(TunneledConnection::close);
		log.info("tunnel server connection closed");
	}

	boolean isAlive() {
		long t = System.currentTimeMillis();
//		log.debug("checking liveness, last active: {}, now: {}", new Date(lastActive), new Date(t));
		boolean r = t - lastActive <= maxIdle;
//		log.debug("still active: {}", r);
		return r;
	}
}
