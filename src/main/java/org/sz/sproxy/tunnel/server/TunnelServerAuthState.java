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
package org.sz.sproxy.tunnel.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Consumer;

import org.sz.sproxy.BlackListAware;
import org.sz.sproxy.ChannelHandler;
import org.sz.sproxy.SocksException;
import org.sz.sproxy.State;
import org.sz.sproxy.Writable.WR;
import org.sz.sproxy.tunnel.AuthManager;
import org.sz.sproxy.tunnel.KeyManager;
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
public class TunnelServerAuthState extends TunnelCmdState<SocketChannel, TunnelServerConnection> implements TunnelCmd {

	public static final String NAME = "AUTH";

	byte[][] sigData;

	List<PublicKey> authorizedKeys;

	public TunnelServerAuthState() {
		super(NAME);
		addHandler(Tunnel.AUTHRQ, this);
	}

	@SuppressWarnings("unchecked")
	@Override
	public WR execute(Tunnel tunnel, TunnelPacketReader reader, Consumer<Object> onFinish, Object ctx) {
		try {
			ByteBuffer buf = reader.getPayload();
			byte requireAuth = buf.get();
			assert requireAuth == 1; // always require server auth
			byte[] cert = new byte[buf.getInt()];
			buf.get(cert);
			byte[] sig = new byte[buf.getInt()];
			buf.get(sig);
			TunnelContext context = (TunnelContext) tunnel.getContext();
			AuthManager am = context.getAuthManager();
			Certificate certificate = am.deserialize(cert);
			TunnelServerConnection conn = (TunnelServerConnection) tunnel;
			if (am.verifySignature(sig, sigData[0], certificate.getPublicKey())
					&& am.isAuthorized(certificate.getPublicKey())) {
				TunnelConfiguration config = (TunnelConfiguration) tunnel.getContext().getConfiguration();
				KeyManager km = context.getKeyManager();
				Entry<PrivateKey, Certificate[]> key = km.load(config.getKeyStoreEntry(), config.getKeyStorePassword());
				byte[] ssig = am.signature(key.getKey(), sigData[1]);
				Certificate scert = key.getValue()[0];
				byte[] certData = scert.getEncoded();
				ByteBuffer ret = ByteBuffer.allocate(1 + 4 + 4 + certData.length + 4 + ssig.length);
				ret.put((byte) 0); // success
				ret.putInt(conn.getId());
				ret.putInt(certData.length);
				ret.put(certData);
				ret.putInt(ssig.length);
				ret.put(ssig);
				ret.flip();
				conn.moveTo(TunnelServerConnectedState.NAME, null);
				tunnel.getWriter(Tunnel.AUTHRP, tunnel).write(ret);
			} else {
				if (context instanceof BlackListAware) {
					((BlackListAware) context).addBlackList(((InetSocketAddress) ((ChannelHandler<SocketChannel>) tunnel)
							.getChannel().getRemoteAddress()).getAddress());
				}
				tunnel.getWriter(Tunnel.AUTHRP, tunnel).write(ByteBuffer.wrap(new byte[] { 1 })); // authentication
																									// failed
				conn.close();
			}
		} catch (IOException | CertificateEncodingException e) {
			throw new SocksException(e);
		}
		return WR.DONE;
	}

	@Override
	public State<SocketChannel, TunnelServerConnection> init(TunnelServerConnection handler, Object info)
			throws IOException {
		sigData = (byte[][]) info;
		return this;
	}

	@Override
	public boolean isChannelCmd() {
		return false;
	}
}
