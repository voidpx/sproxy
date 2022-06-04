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
package org.sz.sproxy.tunnel.secure;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.SecretKeySpec;

import org.sz.sproxy.SocksException;
import org.sz.sproxy.tunnel.Crypto;
import org.sz.sproxy.tunnel.SecretManager;

/**
 * @author Sam Zheng
 *
 */
public class CryptoImpl implements Crypto {

	Cipher enc;
	Cipher dec;
	SecretKeySpec key;

	SecretManager mgr;

	public CryptoImpl(SecretManager mgr, Cipher cipher, Cipher dec, SecretKeySpec key) {
		this.mgr = mgr;
		this.enc = cipher;
		this.dec = dec;
		this.key = key;
	}

	@Override
	public byte[] encrypt(byte[] plain, byte[] iv) {
		try {
			synchronized (enc) {
				enc.init(Cipher.ENCRYPT_MODE, key, mgr.getCipherParamSpec(iv));
				return enc.doFinal(plain);
			}
		} catch (IllegalBlockSizeException | BadPaddingException | InvalidKeyException
				| InvalidAlgorithmParameterException e) {
			throw new SocksException(e);
		}
	}

	@Override
	public byte[] decrypt(byte[] encrypted, byte[] iv) {
		try {
			synchronized (dec) {
				dec.init(Cipher.DECRYPT_MODE, key, mgr.getCipherParamSpec(iv));
				return dec.doFinal(encrypted);
			}
		} catch (IllegalBlockSizeException | BadPaddingException | InvalidKeyException
				| InvalidAlgorithmParameterException e) {
			throw new SocksException(e);
		}
	}

}
