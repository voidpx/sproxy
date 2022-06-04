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
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.sz.sproxy.SocksException;
import org.sz.sproxy.tunnel.Crypto;
import org.sz.sproxy.tunnel.SecretManager;

/**
 * @author Sam Zheng
 *
 */
public class SecretManagerImpl implements SecretManager {

	static final int KEY_SIZE = 2048;

	static final String KA_ALG = "DH";

	static final String SEC_ALG = "AES";

	static final String CIPHER = "AES/GCM/NoPadding";
	
	static final int IV_SIZE = 12;

	private SecureRandom random = new SecureRandom();

	@Override
	public KeyPair generateKAKeyPair() {
		try {
			KeyPairGenerator gen = KeyPairGenerator.getInstance(KA_ALG);
			gen.initialize(KEY_SIZE);
			return gen.generateKeyPair();
		} catch (NoSuchAlgorithmException e) {
			throw new SocksException(e);
		}
	}

	@Override
	public KeyPair generateKAKeyPair(AlgorithmParameterSpec spec) {
		try {
			KeyPairGenerator gen = KeyPairGenerator.getInstance(KA_ALG);
			gen.initialize(spec);
			return gen.generateKeyPair();
		} catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
			throw new SocksException(e);
		}
	}

	@Override
	public KeyAgreement getKeyAgreement(PrivateKey key) {
		try {
			KeyAgreement ka = KeyAgreement.getInstance(KA_ALG);
			ka.init(key);
			return ka;
		} catch (InvalidKeyException | NoSuchAlgorithmException e) {
			throw new SocksException(e);
		}
	}

	@Override
	public AlgorithmParameterSpec getSpec(PublicKey key) {
		if (key instanceof DHPublicKey) {
			return ((DHPublicKey) key).getParams();
		}
		return null;
	}

	@Override
	public PublicKey decodeKAPublicKey(byte[] encoded) {
		try {
			KeyFactory fact = KeyFactory.getInstance(KA_ALG);
			X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(encoded);
			return fact.generatePublic(x509KeySpec);
		} catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
			throw new SocksException(e);
		}
	}

	@Override
	public SecretKeySpec getSecretKeySpec(byte[] secret) {
		return new SecretKeySpec(secret, 0, 16, SEC_ALG);
	}

	@Override
	public AlgorithmParameterSpec getCipherParamSpec(byte[] iv) {
		if (iv.length != IV_SIZE) {
			throw new IllegalArgumentException("Invalid IV");
		}
		return new GCMParameterSpec(128, iv);
	}
	
	@Override
	public Crypto createCrypto(SecretKeySpec spec) {
		try {
			return new CryptoImpl(this, Cipher.getInstance(CIPHER), Cipher.getInstance(CIPHER), spec);
		} catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
			throw new IllegalArgumentException(e);
		}
	}

	@Override
	public byte[] getIV() {
		byte[] b = new byte[IV_SIZE];
		random.nextBytes(b);
		return b;
	}

}
