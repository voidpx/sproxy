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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.sz.sproxy.AcceptorFactory;
import org.sz.sproxy.ChannelHandler;
import org.sz.sproxy.ChannelHandlerFactory;
import org.sz.sproxy.impl.ContextImpl;
import org.sz.sproxy.tunnel.AuthManager;
import org.sz.sproxy.tunnel.KeyManager;
import org.sz.sproxy.tunnel.SecretManager;
import org.sz.sproxy.tunnel.TunnelContext;
import org.sz.sproxy.tunnel.auth.AuthManagerImpl;
import org.sz.sproxy.tunnel.auth.KeyManagerImpl;
import org.sz.sproxy.tunnel.secure.SecretManagerImpl;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Sam Zheng
 *
 */
@Slf4j
public class TunnelServerContext extends ContextImpl implements TunnelContext {
	
	@Getter
	AuthManager authManager;
	
	@Getter
	KeyManager keyManager;
	
	@Getter
	SecretManager secretManager;
	
	Map<Integer, TunnelServerConnection> connections = new ConcurrentHashMap<>();

	public TunnelServerContext(AcceptorFactory acceptorFactory, TunnelServerConfiguration config) {
		super(acceptorFactory, config);
		keyManager = new KeyManagerImpl(config);
		authManager = new AuthManagerImpl(keyManager);
		secretManager = new SecretManagerImpl();
		Thread watcher = new Thread(() -> {
			while (true) {
				try {
					log.debug("tunnel connections: {}", connections.size());
//					int[] removed = new int[] {0};
//					connections.values().stream().filter(Predicate.not(TunnelServerConnection::isAlive)).forEach(c -> {
//						c.close();
//						removed[0]++;
//					});
//					log.debug("idle connections removed: {}", removed);
					Thread.sleep(10000);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					log.debug("tunnel server watcher interrupted", e);
				}
			}
		});
		watcher.setDaemon(true);
		watcher.setName("tunnel_conn_watcher");
		watcher.start();
	}
	
	@Override
	protected ChannelHandlerFactory createConnectionFactory() {
		return new TunnelServerConnectionFactory();
	}
	
	@Override
	public void connectionEstablished(ChannelHandler<?> conn) {
		super.connectionEstablished(conn);
		if (!(conn instanceof TunnelServerConnection)) {
			return;
		}
		TunnelServerConnection c = (TunnelServerConnection)conn;
		connections.put(c.getId(), c);
	}
	
	public TunnelServerConnection getTunnel(int id) {
		return connections.get(id);
	}
	
	@Override
	public void connectionClosing(ChannelHandler<?> conn) {
		if (!(conn instanceof TunnelServerConnection)) {
			return;
		}
		TunnelServerConnection c = (TunnelServerConnection)conn;
		connections.remove(c.getId());
		super.connectionClosing(conn);
	}
	
	public TunnelServerConnection getConnection(int id) {
		return connections.get(id);
	}

}
