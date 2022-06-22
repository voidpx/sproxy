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
import java.net.InetAddress;
import java.nio.channels.Selector;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.sz.sproxy.AcceptorFactory;
import org.sz.sproxy.BlackListAware;
import org.sz.sproxy.ChannelHandler;
import org.sz.sproxy.ChannelHandlerFactory;
import org.sz.sproxy.Configuration;
import org.sz.sproxy.ConnectionListener;
import org.sz.sproxy.Context;
import org.sz.sproxy.Server;
import org.sz.sproxy.SocksCommandFactory;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Sam Zheng
 *
 */
@Slf4j
public class ContextImpl implements Context, ConnectionListener, BlackListAware {
	
	@Getter
	protected Server server;
	
	@Getter
	protected AcceptorFactory acceptorFactory;
	
	@Getter
	protected ChannelHandlerFactory channelHandlerFactory;
	
	@Getter
	protected Executor taskExecutor;
	
	protected Selector selector;
	
	@Getter
	protected SocksCommandFactory commandFactory;

	@Getter
	protected Configuration configuration;
	
	@Getter
	@Setter
	protected BlackListAware blackListAware;
	
	protected Map<String, Object> objects = new HashMap<>();
	
	public ContextImpl(AcceptorFactory acceptorFactory,
			Configuration config) {
		this.acceptorFactory = acceptorFactory;
		this.taskExecutor = Executors.newFixedThreadPool(config.getTaskWorkers(), r -> {
			Thread t = new Thread(r);
			t.setDaemon(true);
			t.setName("task_executor");
			return t;
		});
		configuration = config;
		commandFactory = createCommandFactory();
		channelHandlerFactory = createConnectionFactory();
	}
	
	protected SocksCommandFactory createCommandFactory() {
		return new SocksCommandFactoryImpl();
	}
	
	protected ChannelHandlerFactory createConnectionFactory() {
		return new SocksConnectionFactory();
	}

	public void setSocksServer(Server server) {
		this.server = server;
	}
	
		@SuppressWarnings("unchecked")
	@Override
	public <T> T getService(Class<T> clazz) {
		if (clazz == Selector.class) {
			return (T) getSelector();
		} else if (clazz.isAssignableFrom(getClass())) {
			return (T) this;
		}
		return null;
	}

	@Override
	public List<ConnectionListener> getConnectionListeners() {
		return Arrays.asList(this);
	}

	@Override
	public void connectionEstablished(ChannelHandler<?> conn) {
		log.debug("Connection established: {}", conn.getChannel());
	}

	@Override
	public void connectionClosing(ChannelHandler<?> conn) {
		log.debug("Connection being closed: {}", conn.getChannel());
	}
	
	@Override
	public synchronized Selector getSelector() {
		if (selector == null) {
			try {
				selector = Selector.open();
			} catch (IOException e) {
				throw new RuntimeException(e); // fatal
			}
		}
		return selector;
	}

	@Override
	public Context set(String key, Object obj) {
		objects.put(key, obj);
		return this;
	}

	@Override
	public Object get(String key) {
		return objects.get(key);
	}
	
	@Override
	public void addBlackList(InetAddress address) {
		if (blackListAware != null) {
			blackListAware.addBlackList(address);
		}
	}
}
