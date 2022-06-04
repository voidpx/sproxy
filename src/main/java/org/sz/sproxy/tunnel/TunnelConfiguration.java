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
package org.sz.sproxy.tunnel;

import java.io.IOException;

import org.sz.sproxy.impl.PropertiesConfiguration;

/**
 * @author Sam Zheng
 *
 */
public abstract class TunnelConfiguration extends PropertiesConfiguration {

	public static final String HI_EXECUTORS = "tunnel.workers.high";
	
	public static final String LOW_EXECUTORS = "tunnel.workers.low";
	
	public static final String KEY_STORE_PASS = "tunnel.auth.keystore.password";

	public static final String KEY_STORE_ENTRY = "tunnel.auth.keystore.entry";
	
	public static final String KEY_STORE = "tunnel.auth.keystore";
	
	public static final String AUTHORIZED_KEYS_FILE = "tunnel.auth.authorized_keys";
	
	
	public TunnelConfiguration() {
		
	}
	
	public TunnelConfiguration(String file) throws IOException {
		super(file);
	}

	public int getHighPrioExecutors() {
		return getInt(HI_EXECUTORS, Runtime.getRuntime().availableProcessors());
	}
	
	public int getLowPrioExecutors() {
		return getInt(LOW_EXECUTORS, 1);
	}
	
	public String getKeyStore() {
		return get(KEY_STORE, getDefaultKeyStore());
	}
	
	public String getKeyStorePassword() {
		return get(KEY_STORE_PASS, getDefaultKeyStorePassword());
	}
	
	public String getDefaultKeyStorePassword() {
		return "";
	}
	
	public String getKeyStoreEntry() {
		return get(KEY_STORE_ENTRY, getDefaultKeyStoreEntry());
	}
	
	public String getDefaultKeyStoreEntry() {
		return "default";
	}
	
	public String getAuthorizedKeyFile() {
		return get(AUTHORIZED_KEYS_FILE, getDefaultAuthorizedKeyFile());
	}
	
	public abstract String getDefaultAuthorizedKeyFile();
	
	public abstract String getDefaultKeyStore();
}
