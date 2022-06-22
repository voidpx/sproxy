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
	
	public static final String TUNNEL_SERVER_HOST = "tunnel.server.host";
	
	public static final String TUNNEL_SERVER_PORT = "tunnel.server.port";
	
	public static final String TUNNEL_POOL_IDLE_TIME = "tunnel.client.pool.connections.idleTime";
	
	public static final String TUNNEL_POOL_MAX = "tunnel.client.pool.connections.max";
	
	public static final String TUNNEL_LIVENESS_RESP = "tunnel.client.liveness.response";
	
	public static final int TUNNEL_LIVENESS_RESP_DEF = 16; // in seconds
	
	public static final int TUNNEL_SERVER_PORT_DEF = 9999;
	
	public TunnelClientConfiguration() {
		super();
	}
	
	public TunnelClientConfiguration(String file) throws IOException {
		super(file);
	}
	
	public String getServerHost() {
		return get(TUNNEL_SERVER_HOST, DEF_HOST);
	}
	
	public int getServerPort() {
		return getInt(TUNNEL_SERVER_PORT, TUNNEL_SERVER_PORT_DEF);
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
	
	public int getPoolIdleTime(int def) {
		return getInt(TUNNEL_POOL_IDLE_TIME, def);
	}
	
	public int getMaxConnections(int def) {
		return getInt(TUNNEL_POOL_MAX, def);
	}
	
	public int getLivenessResponse() {
		return getInt(TUNNEL_LIVENESS_RESP, TUNNEL_LIVENESS_RESP_DEF);
	}
	
}
