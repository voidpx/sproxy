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
package org.sz.sproxy.tunnel.auth;

import java.io.ByteArrayInputStream;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.sz.sproxy.SocksException;
import org.sz.sproxy.tunnel.AuthManager;
import org.sz.sproxy.tunnel.KeyManager;

/**
 * @author Sam Zheng
 *
 */
public class AuthManagerImpl implements AuthManager {

	KeyManager km;
	
	Cipher cipher;

	public AuthManagerImpl(KeyManager km) {
		this.km = km;
		try {
			cipher = Cipher.getInstance(KeyManager.KEY_TYPE);
		} catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
			throw new SocksException(e);
		}
	}
	
	@Override
	public byte[] signature(PrivateKey key, byte[] message) {
		try {
			MessageDigest md = MessageDigest.getInstance(HASH_ALG);
			byte[] hash = md.digest(message);
			synchronized (cipher) {
				cipher.init(Cipher.ENCRYPT_MODE, key);
				return cipher.doFinal(hash);
			}
		} catch (NoSuchAlgorithmException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
			throw new SocksException(e);
		}
	}

	@Override
	public boolean verifySignature(byte[] signature, byte[] message, PublicKey key) {
		try {
			MessageDigest md = MessageDigest.getInstance(HASH_ALG);
			byte[] hash = md.digest(message);
			synchronized (cipher) {
				cipher.init(Cipher.DECRYPT_MODE, key);
				byte[] decrypted = cipher.doFinal(signature);
				return Arrays.equals(hash, decrypted);
			}
		} catch (NoSuchAlgorithmException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
			throw new SocksException(e);
		}
	}

	@Override
	public Certificate deserialize(byte[] certificate) {
		try {
			CertificateFactory fact = CertificateFactory.getInstance("X.509", BouncyCastleProvider.PROVIDER_NAME);
			return fact.generateCertificate(new ByteArrayInputStream(certificate));
		} catch (CertificateException | NoSuchProviderException e) {
			throw new SocksException(e);
		}
	}
	
	@Override
	public boolean isAuthorized(PublicKey key) {
		return km.loadAuthorizedKeys().stream().anyMatch(k -> k.equals(key));
	}

}
