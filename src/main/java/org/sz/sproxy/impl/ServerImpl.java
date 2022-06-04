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
import java.util.concurrent.Executor;

import org.sz.sproxy.Acceptor;
import org.sz.sproxy.Context;
import org.sz.sproxy.ContextConfiguration;
import org.sz.sproxy.Server;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Sam Zheng
 *
 */
@Slf4j
public class ServerImpl implements Server {
	
	Executor executor;
	
	String host;
	
	int port;
	
	Context context;
	
	Acceptor acceptor;
	
	ServerImpl(Context context, String host, int port) {
		this.host = host;
		this.port = port;
		this.context = context;
	}
	
	@Override
	public Context getContext() {
		return context;
	}
	
	@Override
	public int getPort() {
		return port;
	}
	
	@Override
	public String getHost() {
		return host;
	}
	
	@Override
	public void start() throws IOException {
		acceptor = context.getAcceptorFactory().createAcceptor(this);
		acceptor.startAccepting();
	}
	
	@Override
	public void stop() {
		acceptor.close();
		log.info("stopped");
	}
	
	public static Server create(ContextConfiguration config) {
		return new ServerImpl(config.createContext(),
				config.getHost(), config.getPort());
	}

}
