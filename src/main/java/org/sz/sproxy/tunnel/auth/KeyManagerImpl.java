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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableEntryException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.sz.sproxy.SocksException;
import org.sz.sproxy.tunnel.KeyManager;
import org.sz.sproxy.tunnel.TunnelConfiguration;

/**
 * @author Sam Zheng
 *
 */
public class KeyManagerImpl implements KeyManager {

	TunnelConfiguration configuration;

	KeyFactory keyFactory;

	public KeyManagerImpl(TunnelConfiguration configuration) {
		this.configuration = configuration;
		try {
			keyFactory = KeyFactory.getInstance(KEY_TYPE, BouncyCastleProvider.PROVIDER_NAME);
		} catch (NoSuchAlgorithmException | NoSuchProviderException e) {
			throw new SocksException(e);
		}
	}

	@Override
	public void generateKeyPair(String type, String subject, BiConsumer<PrivateKey, Certificate[]> consumer) {
		try {
			if (type == null) {
				type = KEY_TYPE;
			}
			if (!KEY_TYPE.equalsIgnoreCase(type)) {
				throw new SocksException("Only RSA is supported");
			}
			if (subject == null) {
				subject = InetAddress.getLocalHost().getCanonicalHostName();
			}
			KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KEY_TYPE,
					BouncyCastleProvider.PROVIDER_NAME);
			keyPairGenerator.initialize(2048);
			KeyPair keyPair = keyPairGenerator.generateKeyPair();
			X500Name dnName = new X500Name("CN=" + subject);
			BigInteger certSerialNumber = BigInteger.valueOf(System.currentTimeMillis());
			ContentSigner contentSigner = new JcaContentSignerBuilder(SIG_ALG).build(keyPair.getPrivate());
			Instant startDate = Instant.now();
			Instant endDate = startDate.plus(365, ChronoUnit.DAYS);
			JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(dnName, certSerialNumber,
					Date.from(startDate), Date.from(endDate), dnName, keyPair.getPublic());
			Certificate certificate = new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME)
					.getCertificate(certBuilder.build(contentSigner));
			consumer.accept(keyPair.getPrivate(), new Certificate[] { certificate });

		} catch (NoSuchAlgorithmException | CertificateException | NoSuchProviderException | IOException
				| OperatorCreationException e) {
			throw new SocksException(e);
		}
	}

	@Override
	public void store(String keyAlias, String password, PrivateKey key, Certificate[] certificates) {
		try {
			KeyStore keyStore = KeyStore.getInstance("pkcs12", BouncyCastleProvider.PROVIDER_NAME);
			keyStore.load(null, "".toCharArray());
			keyStore.setKeyEntry(Optional.ofNullable(keyAlias).orElse(configuration.getKeyStoreEntry()), key,
					Optional.ofNullable(password).orElse("").toCharArray(), certificates);
			String kf = getKeyStoreFile();
			keyStore.store(new FileOutputStream(kf), Optional.ofNullable(password).orElse("").toCharArray());
		} catch (IOException | KeyStoreException | NoSuchProviderException | NoSuchAlgorithmException
				| CertificateException e) {
			throw new SocksException(e);
		}

	}

	private String getKeyStoreFile() {
		return configuration.getKeyStore();
	}

	@Override
	public Entry<PrivateKey, Certificate[]> load(String keyAlias, String password) {
		try {
			KeyStore keyStore = KeyStore.getInstance("pkcs12", BouncyCastleProvider.PROVIDER_NAME);
			keyStore.load(new FileInputStream(getKeyStoreFile()),
					Optional.ofNullable(password).orElse("").toCharArray());
			PrivateKeyEntry k = (PrivateKeyEntry) keyStore.getEntry(
					Optional.ofNullable(keyAlias).orElse(configuration.getKeyStoreEntry()),
					new PasswordProtection("".toCharArray()));
			return Map.entry(k.getPrivateKey(), k.getCertificateChain());
		} catch (IOException | NoSuchAlgorithmException | CertificateException | NoSuchProviderException
				| KeyStoreException | UnrecoverableEntryException e) {
			throw new SocksException(e);
		}
	}

	@Override
	public String toString(PublicKey key) {
		return "rsa " + Base64.getEncoder().encodeToString(key.getEncoded());
	}

	@Override
	public PublicKey fromString(String key) {
		String[] a = key.split("\\s+");
		String k;
		if (a.length >= 2) {
			if (!KEY_TYPE.equalsIgnoreCase(a[0])) {
				throw new SocksException("Invalid key type " + a[0]);
			}
			k = a[1];
		} else {
			k = a[0];
		}
		try {
			return keyFactory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(k)));
		} catch (InvalidKeySpecException e) {
			throw new SocksException(e);
		}
	}
	
	@Override
	public List<PublicKey> loadAuthorizedKeys() {
		Path p = Paths.get(configuration.getAuthorizedKeyFile());
		if (p.toFile().exists()) {
			try {
				return Files.readAllLines(p).stream().filter(l -> !l.startsWith("#"))
						.map(this::fromString).collect(Collectors.toList());
			} catch (IOException e) {
				throw new SocksException(e);
			}
		}
		return Collections.emptyList();
	}
}
