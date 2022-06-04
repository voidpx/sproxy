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

import org.sz.sproxy.tunnel.KeyManager;
import org.sz.sproxy.tunnel.TunnelConfiguration;

/**
 * @author Sam Zheng
 *
 */
public class TunnelClientConfiguration extends TunnelConfiguration {
	
	public static final String SERVER_HOST = "tunnel.server.host";
	
	public static final String SERVER_PORT = "tunnel.server.port";
	
	public TunnelClientConfiguration() {
		super();
	}
	
	public TunnelClientConfiguration(String file) throws IOException {
		super(file);
	}
	
	public String getServerHost() {
		return get(SERVER_HOST, DEF_HOST);
	}
	
	public int getServerPort() {
		return getInt(SERVER_PORT, DEF_PORT);
	}
	
	public String getKeyStoreFile() {
		return get(KeyManager.KEY_STORE);
	}
	
	@Override
	public String getDefaultKeyStore() {
		return "./tc.p12";
	}
	
	@Override
	public String getDefaultAuthorizedKeyFile() {
		return "./tc_authorized_keys";
	}
	
}
