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
package org.sz.sproxy.start;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.sz.sproxy.Configuration;
import org.sz.sproxy.impl.SocksContextConfiguration;
import org.sz.sproxy.impl.SocksServerImpl;
import org.sz.sproxy.relay.SocksRelayConfiguration;
import org.sz.sproxy.relay.SocksRelayContext;
import org.sz.sproxy.tunnel.KeyManager;
import org.sz.sproxy.tunnel.TunnelConfiguration;
import org.sz.sproxy.tunnel.server.TunnelServerConfiguration;
import org.sz.sproxy.tunnel.server.TunnelServerContext;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Sam Zheng
 *
 */
@Slf4j
public class Launcher {
	
	static {
		Security.addProvider(new BouncyCastleProvider());
	}
	
	private static final Map<String, Command> CMDS = new HashMap<>();
	
	static {
		CMDS.put("standalone", Launcher::doStandalone);
		CMDS.put("client", Launcher::doClient);
		CMDS.put("server", Launcher::doServer);
		CMDS.put("genKey", Launcher::doKeyGen);
		CMDS.put("addAuthKey", Launcher::doAddAuthKey);
	}
	
	private static String getRequiredArg(String[] args, int optIdx, String opt) {
		if (optIdx < args.length - 1) {
			return args[optIdx + 1];
		} else {
			throw new IllegalArgumentException("Option " + opt + " requires an argument");
		}
	}
	
	private static void parseOpts(String[] args, Map<String, String> opts, Configuration config, Set<String> required) {
		required = new HashSet<String>(required);
		for (int i = 0; i < args.length; i++) {
			String s = args[i];
			if (opts.containsKey(s)) {
				String v = getRequiredArg(args, i, s);
				String p = opts.get(s);
				config.set(p, v);
				i++;
			}
			required.remove(s);
		}
		if (!required.isEmpty()) {
			throw new IllegalArgumentException("Required options not specified: " + required);
		}
	}
	
	private static void parseOpts(String[] args, Map<String, String> opts, Configuration config) {
		parseOpts(args, opts, config, Collections.emptySet());
	}
	
	private static void doClient(String[] args) throws IOException {
		SocksRelayConfiguration config = new SocksRelayConfiguration();
		parseOpts(args, Map.of(
				"-h", Configuration.SOCKS_HOST,
				"-p", Configuration.SOCKS_PORT,
				"-H", SocksRelayConfiguration.SERVER_HOST,
				"-P", SocksRelayConfiguration.SERVER_PORT,
				"-k", KeyManager.KEY_STORE), config);
		SocksServerImpl.create(config).start();
	}
	
	private static void doServer(String[] args) throws IOException {
		TunnelServerConfiguration config = new TunnelServerConfiguration();
		parseOpts(args, Map.of(
				"-h", Configuration.SOCKS_HOST,
				"-p", Configuration.SOCKS_PORT,
				"-a", TunnelServerConfiguration.AUTHORIZED_KEYS_FILE), config);
		SocksServerImpl.create(config).start();
	}
	
	private static void doKeyGen(String[] args) throws IOException {
		// client
		SocksRelayConfiguration config = new SocksRelayConfiguration();
		SocksRelayContext ctx = (SocksRelayContext)config.createContext();
		ctx.getKeyManager().generateKeyPair(null, null, (k, c) -> {
			ctx.getKeyManager().store(null, null, k, c);
		});
		
		// server
		TunnelServerConfiguration sc = new TunnelServerConfiguration();
		TunnelServerContext sctx = (TunnelServerContext)sc.createContext();
		sctx.getKeyManager().generateKeyPair(null, null, (k, c) -> {
			sctx.getKeyManager().store(null, null, k, c);
		});
		
		doAddAuthKey(new String[] {"-k", config.getKeyStore(), "-a", sc.getAuthorizedKeyFile()});
		doAddAuthKey(new String[] {"-k", sc.getKeyStore(), "-a", config.getAuthorizedKeyFile()});
	}
	
	private static void doAddAuthKey(String[] args) throws IOException {
		TunnelServerConfiguration config = new TunnelServerConfiguration();
		parseOpts(args, Map.of(
				"-k", KeyManager.KEY_STORE,
				"-a", TunnelConfiguration.AUTHORIZED_KEYS_FILE), config, new HashSet<>(Arrays.asList("-k", "-a")));
		TunnelServerContext ctx = (TunnelServerContext)config.createContext();
		Entry<PrivateKey, Certificate[]> entry = ctx.getKeyManager().load(config.getKeyStoreEntry(), null);
		String k = ctx.getKeyManager().toString(entry.getValue()[0].getPublicKey());
		Path p = Paths.get(config.getAuthorizedKeyFile());
		if (p.toFile().exists()) {
			String keys = Files.readString(p);
			k = keys + "\n" + k;
		}
		Files.writeString(p, k);
	}
	
	private static void doStandalone(String[] args) throws IOException {
		SocksContextConfiguration config = new SocksContextConfiguration();
		parseOpts(args, Map.of(
				"-h", Configuration.SOCKS_HOST,
				"-p", Configuration.SOCKS_PORT), config);
		SocksServerImpl.create(config).start();
	}
	
	
	private static void help(PrintStream out) {
		StringBuilder sb = new StringBuilder();
		sb.append("Usage:\n")
			.append("sub commands:\n")
			.append("  client - run as a tunnel client proxy\n")
			.append("    -h <host> - address at which the tunnel client will be listening\n")
			.append("    -p <port> - port on which the tunnel client will be listening\n")
			.append("    -H <host> - remote address at which the tunnel server is listening\n")
			.append("    -P <port> - remote port on which the tunnel server is listening\n")
			.append("    -k <keystore file> - keystore file(pkcs12) that contains the key to be used by the tunnel client to authenticate with the tunnel server\n")
			.append("  server - run as a tunnel server\n")
			.append("    -h <host> - address at which the tunnel server will be listening\n")
			.append("    -p <port> - port on which the tunnel server will be listening\n")
			.append("    -a <authorized key file> - file that contains the public keys that are authorized to connect to this tunnel server\n")
			.append("  genKey - generate a key pair(RSA) for tunnel client and server respectively for mutual authentication, generated files are:\n")
			.append("    tc.p12 - tunnel client key store\n")
			.append("    tc_authorized_keys - this file contains public key of the tunnel server that the tunnel client trusts, more public keys can be added with the addAuthKey command\n")
			.append("    ts.p12 - tunnel server key store\n")
			.append("    ts_authorized_keys - this file contains public keys of the tunnel client that the tunnel server trusts, more public keys can be added with the addAuthKey command\n")
			
			.append("  addAuthKey - add a public key to the list of authorized keys used by the tunnel server\n")
			.append("    -k <keystore file> - keystore file(pkcs12) from which to extract the public key\n")
			.append("    -a <authorized key file> - file to store(append) the extracted public key\n")
			.append("  standalone - run as a standalone proxy that provides no authentication or encryption\n")
			.append("    -h <host> - address at which the proxy will be listening\n")
			.append("    -p <port> - port on which the proxy will be listening\n");
		
		out.println(sb.toString());
	}
	

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			String cmd = "standalone";
			String[] a = args;
			if (args.length > 0) {
				cmd = args[0];
				a = new String[args.length - 1];
				System.arraycopy(args, 1, a, 0, a.length);
			}
			Command c = CMDS.get(cmd);
			if (c == null) {
				help(System.out);
				System.exit(1);
			}
			c.doCmd(a);
		} catch (Throwable e) {
			log.error(e.getMessage(), e);
			System.exit(1);
		}
		
	}

}
