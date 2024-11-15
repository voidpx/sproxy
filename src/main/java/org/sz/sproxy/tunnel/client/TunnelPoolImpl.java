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
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.sz.sproxy.Context;
import org.sz.sproxy.SocksException;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Sam Zheng
 *
 */
@Slf4j
public class TunnelPoolImpl implements TunnelPool, Runnable {

	private static final int TUN_IDLE_TIME = 5 * 60 * 1000; // max idle time

	@SuppressWarnings("unused")
	private static final long TUN_RENEW_TIME = /* 20 * */30 * 1000L;

	private static final int MAX_CONN = 15;

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
	
	AtomicInteger livenessProbeId = new AtomicInteger(new Random().nextInt());

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
		
//		// don't do liveness tick, it sometimes reports false positive
//		Thread livenessTick = new Thread(() -> {
//			while (true) {
//				synchronized (this) {
//					new ArrayList<>(connections).forEach(c -> c.livenessTick());
//				}
//				try {
//					Thread.sleep(config.getLivenessResponse() * 1000 + 500); // half a second skew
//				} catch (InterruptedException e) {
//					Thread.currentThread().interrupt();
//					log.debug(e.getMessage(), e);
//				}
//			}
//		});
//		livenessTick.setName("tunnel_liveness_tick");
//		livenessTick.setDaemon(true);
//		livenessTick.start();
	}

	private void houseKeeping() {
		long t = System.currentTimeMillis();

		log.debug("active connections: {}, relayed:\n{}", connections.size(), 
				connections.stream().map(c -> String.valueOf(c.getRelayedCount())).collect(Collectors.joining(",\n")));

		List<TunnelClient> toClose = connections.stream()
				.filter(c -> t - ((TunnelInfo) c.getAttachment()).idle > config.getPoolIdleTime(TUN_IDLE_TIME))
				.toList();

		log.debug("closing idle connections: {}", toClose.size());
		toClose.forEach(c -> {
			// close tunnel that's idle for too long
			log.debug("closing idle tunnel");
			c.close();
		});
		
	}

	TunnelClientCallback callback = new TunnelClientCallback() {

		@Override
		public void connected(TunnelClient tunnel) {
			tunnel.attach(new TunnelInfo(System.currentTimeMillis()));
			add(tunnel);
			log.debug("Tunnel connection created: {}", tunnel);
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

	};

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
		connections.remove(t);
		connections.add(t);
		notifyAll();
	}

	@Override
	public synchronized TunnelClient getTunnel() {
		long t = System.currentTimeMillis();
		log.debug("trying getting tunnel connection, ");
		for (int i = 0; i < 10 && connections.isEmpty(); i++) {
			if (pendingConnections.isEmpty()) {
				newTunnel(callback);
			}
			try {
				log.debug("waiting for the first connection");
				wait(5000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new SocksException("Interrupted while waiting for tunnel to be ready");
			}
		}
		if (connections.isEmpty()) {
			throw new SocksException("Unable to connect to tunnel server");
		}
		TunnelClient c = connections.peek();
		log.debug("head of connection queue: {}", c.getRelayedCount());
		while (c.getRelayedCount() >= RELAY_THRESHOLD && (connections.size() + pendingConnections.size()) < size) {			
			TunnelClient anew = newTunnel(callback);
			try {
				log.debug("waiting for new connection to be ready, {}" + anew);
				wait();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new SocksException("Interrupted while waiting for tunnel to be ready");
			}
			if (!connections.isEmpty()) {
				c = connections.peek();
				log.debug("got new connection: {}", anew);
			}
		}
		log.debug("got tunnel connection, relay count: {}, took: {}s", c.getRelayedCount(), 
				(System.currentTimeMillis() - t / 1000.0));
		return c;
	}

	synchronized TunnelClient newTunnel(TunnelClientCallback callback) {
		try {
			TunnelClient c = new TunnelClientConnection(context, callback);
			pendingConnections.add(c);
			return c;
		} catch (IOException e) {
			throw new SocksException(e);
		}
	}
}
