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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

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

/**
 * @author Sam Zheng
 *
 */
public class TunnelServerContext extends ContextImpl implements TunnelContext {
	
	@Getter
	AuthManager authManager;
	
	@Getter
	KeyManager keyManager;
	
	@Getter
	SecretManager secretManager;
	
	@Getter
	Executor lowPrioExecutor;
	
	@Getter
	Executor highPrioExecutor;
	
	Map<Integer, TunnelServerConnection> connections = new HashMap<>();

	public TunnelServerContext(AcceptorFactory acceptorFactory, Executor executor, TunnelServerConfiguration config) {
		super(acceptorFactory, executor, config);
		keyManager = new KeyManagerImpl(config);
		authManager = new AuthManagerImpl(keyManager);
		secretManager = new SecretManagerImpl();
		lowPrioExecutor = Executors.newFixedThreadPool(config.getLowPrioExecutors(), (r) -> {
			Thread t = new Thread(r);
			t.setDaemon(true);
			t.setName("tunnel_server_low_prio_executor");
			return t;
		});
		ThreadGroup hi = new ThreadGroup("tunnel_high");
		highPrioExecutor = Executors.newFixedThreadPool(config.getHighPrioExecutors(), r -> {
			Thread t = new Thread(hi, r);
			t.setDaemon(true);
			t.setName("tunnel_high_prio_executor");
			t.setPriority(hi.getMaxPriority());
			return t;
		});
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
