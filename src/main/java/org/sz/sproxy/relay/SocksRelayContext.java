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
package org.sz.sproxy.relay;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.sz.sproxy.AcceptorFactory;
import org.sz.sproxy.ChannelHandlerFactory;
import org.sz.sproxy.SocksCommandFactory;
import org.sz.sproxy.impl.ContextImpl;
import org.sz.sproxy.tunnel.AuthManager;
import org.sz.sproxy.tunnel.KeyManager;
import org.sz.sproxy.tunnel.SecretManager;
import org.sz.sproxy.tunnel.TunnelContext;
import org.sz.sproxy.tunnel.auth.AuthManagerImpl;
import org.sz.sproxy.tunnel.auth.KeyManagerImpl;
import org.sz.sproxy.tunnel.client.TunnelClientConfiguration;
import org.sz.sproxy.tunnel.client.TunnelPool;
import org.sz.sproxy.tunnel.client.TunnelPoolImpl;
import org.sz.sproxy.tunnel.secure.SecretManagerImpl;

import lombok.Getter;

/**
 * @author Sam Zheng
 *
 */
public class SocksRelayContext extends ContextImpl implements TunnelContext {
	
	@Getter
	TunnelPool pool;
	
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
	
	public SocksRelayContext(AcceptorFactory acceptorFactory, Executor executor, TunnelClientConfiguration config) {
		super(acceptorFactory, executor, config);
		keyManager = new KeyManagerImpl(config);
		authManager = new AuthManagerImpl(keyManager);
		secretManager = new SecretManagerImpl();
		lowPrioExecutor = Executors.newFixedThreadPool(config.getLowPrioExecutors(), r -> {
			Thread t = new Thread(r);
			t.setDaemon(true);
			t.setName("tunnel_low");
			t.setPriority(Thread.MIN_PRIORITY);
			return t;
		});
		ThreadGroup hi = new ThreadGroup("tunnel_high");
		highPrioExecutor = Executors.newFixedThreadPool(config.getHighPrioExecutors(), r -> {
			Thread t = new Thread(hi, r);
			t.setDaemon(true);
			t.setName("tunnel_high");
			t.setPriority(hi.getMaxPriority());
			return t;
		});
		pool = new TunnelPoolImpl(this);
	}
	
	@Override
	protected SocksCommandFactory createCommandFactory() {
		return new SocksRelayCmdFactory();
	}
	
	@Override
	protected ChannelHandlerFactory createConnectionFactory() {
		return new SocksRelayConnectionFactory();
	}
	
}
