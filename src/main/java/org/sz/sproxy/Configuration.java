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
package org.sz.sproxy;

/**
 * This interface provides centralized configuration.
 * 
 * @author Sam Zheng
 *
 */
public interface Configuration {
	
	String BUF_SIZE_KEY = "packet.buffer.size";
	
	String TASK_WORKERS_KEY = "task.workers";
	
	String DEF_HOST = "localhost";
	
	int DEF_BUF_SIZE = 0x400;
	
	int DEF_PORT = 8888;
	
	String SERVER_HOST = "server.host";
	
	String SERVER_PORT = "server.port";
	
	String SERVER_IPV6 = "server.ipv6";
	
	String get(String key, String def);
	
	default String get(String key) {
		return get(key, null);
	}
	
	default int getInt(String key, int def) {
		String v = get(key);
		if (v == null || v.isEmpty()) {
			return def;
		}
		return Integer.parseInt(v);
	}
	
	/**
	 * Returns the host on which the server will be listening.
	 * 
	 * @return
	 */
	default String getHost() {
		return get(SERVER_HOST, DEF_HOST);
	}
	
	/**
	 * @deprecated not used anymore
	 * @return
	 */
	default int getTaskWorkers() {
		return getInt(TASK_WORKERS_KEY, Runtime.getRuntime().availableProcessors());
	}
	
	/**
	 * Returns the port at which the server will be listening.
	 * 
	 * @return
	 */
	default int getPort() {
		return getInt(SERVER_PORT, DEF_PORT);
	}
	
	default int getPacketBufferSize() {
		return getInt(BUF_SIZE_KEY, DEF_BUF_SIZE);
	}
	
	String set(String key, String value);
	
}
