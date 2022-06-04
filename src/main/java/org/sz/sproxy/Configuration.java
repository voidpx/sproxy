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
 * @author Sam Zheng
 *
 */
public interface Configuration {
	
	String DEF_HOST = "localhost";
	
	int DEF_PORT = 1080;
	
	String SOCKS_HOST = "socks.host";
	
	String SOCKS_PORT = "socks.port";
	
	String SOCKS_MODE = "socks.mode";
	
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
	
	default String getSocksHost() {
		return get(SOCKS_HOST, DEF_HOST);
	}
	
	default int getSocksPort() {
		return getInt(SOCKS_PORT, DEF_PORT);
	}
	
	String set(String key, String value);
	
}
