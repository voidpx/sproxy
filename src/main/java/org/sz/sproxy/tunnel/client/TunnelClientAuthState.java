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
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.Map.Entry;
import java.util.function.Consumer;

import org.sz.sproxy.SocksException;
import org.sz.sproxy.Writable.WR;
import org.sz.sproxy.relay.SocksRelayContext;
import org.sz.sproxy.tunnel.AuthManager;
import org.sz.sproxy.tunnel.Tunnel;
import org.sz.sproxy.tunnel.TunnelCmd;
import org.sz.sproxy.tunnel.TunnelCmdState;
import org.sz.sproxy.tunnel.TunnelConfiguration;
import org.sz.sproxy.tunnel.TunnelContext;
import org.sz.sproxy.tunnel.TunnelPacketReader;

/**
 * @author Sam Zheng
 *
 */
public class TunnelClientAuthState extends TunnelCmdState<SocketChannel, TunnelClientConnection> implements TunnelCmd {
	
	public static final String NAME = "AUTH";
	
	private static final byte AUTH_SUCCESS = 0;

	byte[] recv;

	public TunnelClientAuthState() {
		super(NAME);
		addHandler(Tunnel.AUTHRP, this);
	}

	@Override
	public boolean isChannelCmd() {
		return false;
	}

	@Override
	public WR execute(Tunnel tunnel, TunnelPacketReader reader, Consumer<Object> onFinish, Object ctx) {
		ByteBuffer buf = reader.getPayload();
		byte code = buf.get();
		if (code != AUTH_SUCCESS) {
			throw new SocksException("Authentication failed: " + code);
		}
		TunnelClientConnection conn = (TunnelClientConnection)tunnel;
		conn.setId(buf.getInt());
		// authenticate the server
		
		byte[] cert = new byte[buf.getInt()];
		buf.get(cert);
		byte[] sig = new byte[buf.getInt()];
		buf.get(sig);
		TunnelContext context = (TunnelContext) tunnel.getContext();
		AuthManager am = context.getAuthManager();
		Certificate certificate = am.deserialize(cert);
		if (!am.verifySignature(sig, recv, certificate.getPublicKey())) {
			throw new SocksException("Unable to verify server signature");
		}
		if (!am.isAuthorized(certificate.getPublicKey())) {
			throw new SocksException("Unauthorized server key");
		}
		conn.moveTo(TunnelClientConnectedState.NAME, null);
		// notify connection establishment
		conn.connected();
		return WR.DONE;
	}
	
	boolean requireServerAuth(TunnelClientConfiguration config) {
		return true;
	}

	@Override
	public TunnelClientAuthState init(TunnelClientConnection handler, Object info) throws IOException {
		try {
			byte[][] sigData = (byte[][])info;
			recv = sigData[0];
			SocksRelayContext ctx = (SocksRelayContext) handler.getContext();
			TunnelConfiguration config = (TunnelConfiguration) handler.getContext().getConfiguration();
			Entry<PrivateKey, Certificate[]> key = ctx.getKeyManager().load(config.getKeyStoreEntry(),
					config.getKeyStorePassword());
			byte[] sig = ctx.getAuthManager().signature(key.getKey(), sigData[1]);
			Certificate cert = key.getValue()[0];
			byte[] certData = cert.getEncoded();
			int len = 4 + certData.length + 4 + sig.length + 1;
			ByteBuffer buf = ByteBuffer.allocate(len);
			byte a = 1; // 1: always require server auth
			buf.put(a); // 0: no server auth, 1: require server auth
			buf.putInt(certData.length);
			buf.put(certData);
			buf.putInt(sig.length);
			buf.put(sig);
			buf.flip();
			handler.getWriter(Tunnel.AUTHRQ, handler).write(buf);
			return this;
		} catch (CertificateEncodingException e) {
			throw new SocksException(e);
		}
	}

}
