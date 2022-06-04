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

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * @author Sam Zheng
 *
 */
public interface KeyManager {
	
	String KEY_STORE = "tunnel.auth.keystore";

	String KEY_TYPE = "RSA";

	String SIG_ALG = "SHA256WithRSA";
	
	void generateKeyPair(String type, String subject, BiConsumer<PrivateKey, Certificate[]> consumer);

	void store(String keyAlias, String password, PrivateKey key, Certificate[] certificates);

	Map.Entry<PrivateKey, Certificate[]> load(String keyAlias, String password);
	
	String toString(PublicKey key);
	
	PublicKey fromString(String key);
	
	List<PublicKey> loadAuthorizedKeys();
}
