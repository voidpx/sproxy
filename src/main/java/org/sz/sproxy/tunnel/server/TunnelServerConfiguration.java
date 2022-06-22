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

import org.sz.sproxy.Context;
import org.sz.sproxy.ContextConfiguration;
import org.sz.sproxy.impl.AcceptorFactoryImpl;
import org.sz.sproxy.tunnel.TunnelConfiguration;

/**
 * @author Sam Zheng
 *
 */
public class TunnelServerConfiguration extends TunnelConfiguration implements ContextConfiguration {
	
	public TunnelServerConfiguration() {
		super();
	}

	public TunnelServerConfiguration(String file) throws IOException {
		super(file);
	}

	@Override
	public Context createContext() {
		return new TunnelServerContext(new AcceptorFactoryImpl(), this);
	}
	
	@Override
	public String getDefaultKeyStore() {
		return "./ts.p12";
	}
	
	@Override
	public String getDefaultAuthorizedKeyFile() {
		return "./ts_authorized_keys";
	}
}
