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
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Collectors;

import org.sz.sproxy.Context;
import org.sz.sproxy.SocksException;
import org.sz.sproxy.tunnel.TunneledConnection;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Sam Zheng
 *
 */
@Slf4j
public class TunnelPoolImpl implements TunnelPool, Runnable {
	private static final int TUN_IDLE_TIME = 1 * 60 * 1000;

	private static final int MAX_CONN = 20;

	private static final int RELAY_THRESHOLD = 1;

	private static class TunnelInfo {
		@SuppressWarnings("unused")
		volatile long connected;
		volatile long idle = Long.MAX_VALUE;

		TunnelInfo(long t) {
			connected = t;
		}
	}

	PriorityQueue<TunnelClient> connections;

	Set<TunnelClient> pendingConnections;

	int size;

	Context context;

	TunnelClientConfiguration config;
	
	public TunnelPoolImpl(Context context) {
		this(context, ((TunnelClientConfiguration) context.getConfiguration()).getMaxConnections(MAX_CONN));
	}

	public TunnelPoolImpl(Context context, int size) {
		this.size = size;
		this.context = context;
		config = (TunnelClientConfiguration) context.getConfiguration();
		connections = new PriorityQueue<>(size, (o1, o2) -> o1.getRelayedCount() - o2.getRelayedCount());
		pendingConnections = new HashSet<>();
		Thread t = new Thread(this);
		t.setName("tunnel_pool_house_keeping");
		t.setDaemon(true);
		t.start();
	}
	private void houseKeeping() {

		log.debug("active connections: {}, relayed:\n{}", connections.size(), 
				connections.stream().map(c -> String.valueOf(c.getRelayedCount())).collect(Collectors.joining(",\n")));
		long t = System.currentTimeMillis();
		int to = config.getPoolIdleTime(TUN_IDLE_TIME);
		synchronized (this) {
			
			List<TunnelClientConnection> toClose = connections.stream()
					.map(c -> (TunnelClientConnection)c)
					.map(c -> {c.cleanupOrphaned(); return c;})
					.filter(c -> t - ((TunnelInfo) c.getAttachment()).idle > to)
					.toList();
			
			log.debug("closing idle connections: {}", toClose.size());
			toClose.forEach(c -> {
				// close tunnel that's idle for too long
				log.debug("closing idle tunnel: {}", c);
				c.close();
			});
		}
		
	}
	
	private static class ConnState {
		RelayedConnection tunneled;
		volatile boolean error;
		ConnState(RelayedConnection tunneled) {
			this.tunneled = tunneled;
		}
	}
	
	private class CB implements TunnelClientCallback {
		ConnState state;
		CB(ConnState st) {
			state = st;
		}
		@Override
		public void connected(TunnelClient tunnel) {
			tunnel.attach(new TunnelInfo(System.currentTimeMillis()));
			tunnel.tunnel(state.tunneled);
			synchronized (tunnel) {
				tunnel.notifyAll();
			}
			add(tunnel);
			log.debug("Tunnel connection created: {}", tunnel);
		}
		
		@Override
		public void connectError(TunnelClient tunnel) {
			log.error("tunnel connect error");
			state.error = true;
			synchronized (tunnel) {
				tunnel.notifyAll();
			}
		}

		@Override
		public void closing(TunnelClient tunnel) {
			remove(tunnel);
		}

		@Override
		public void channelClosed(TunnelClient tunnel, RelayedConnection tunneled) {
			reposition(tunnel);
			if (tunnel.getRelayedCount() == 0) {
				TunnelInfo i = (TunnelInfo) tunnel.getAttachment();
				i.idle = System.currentTimeMillis();
			}

		}

		@Override
		public void channelAdded(TunnelClient tunnel, RelayedConnection tunneled) {
			reposition(tunnel);
			TunnelInfo i = (TunnelInfo) tunnel.getAttachment();
			i.idle = Long.MAX_VALUE;
		}

	}

	@Override
	public void run() {
		while (true) {
			synchronized (this) {
				houseKeeping();
			}
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				log.debug(e.getMessage(), e);
			}
		}
	}

	private synchronized void add(TunnelClient t) {
		pendingConnections.remove(t);
		connections.add(t);
		notifyAll();
	}

	private synchronized void remove(TunnelClient t) {
		pendingConnections.remove(t);
		connections.remove(t);
		notifyAll();
	}

	private synchronized void reposition(TunnelClient t) {
		if (connections.contains(t)) { 
			connections.remove(t);
			connections.add(t);
			notifyAll();
		}
	}

	@Override
	public TunnelClient tunnel(TunneledConnection tunneled) {
		ConnState st;
		TunnelClient c;
		synchronized (this) {
			c = connections.peek();
			if (c != null && c.getRelayedCount() < RELAY_THRESHOLD) {
				((TunnelInfo)c.getAttachment()).idle = Long.MAX_VALUE;
				log.debug("found idle tunnel: {}, {}", c.getChannel(), c.getRelayedCount());
				c.tunnel((RelayedConnection)tunneled);
				return c;
			}
			if (c == null || connections.size() + pendingConnections.size() < size) {
				log.debug("creating new tunnel");
				st = new ConnState((RelayedConnection)tunneled);
				try {
					c = new TunnelClientConnection(context, new CB(st));
					pendingConnections.add(c);
				} catch (IOException e) {
					throw new SocksException("error creating tunnel", e);
				}
			} else {
				((TunnelInfo)c.getAttachment()).idle = Long.MAX_VALUE;
				log.debug("max connections reached, reusing tunnel: {}, {}", c.getChannel(), c.getRelayedCount());
				c.tunnel((RelayedConnection)tunneled);
				return c;
			}
		}
		return waitForNewTunnel((TunnelClientConnection)c, st);
	}

	private TunnelClient waitForNewTunnel(TunnelClientConnection c, ConnState st) {
		try {
			
			synchronized (c) {
				for (int i = 0; i < 3 && !c.isConnected() && !st.error; i++) {
					c.wait(5000);
				}
			}
			if (!c.isConnected()) {
				throw new SocksException("unable to create tunnel");
			}
			return c;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new SocksException(e);
		}
	}
}
