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
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.function.Consumer;

import javax.crypto.KeyAgreement;
import javax.crypto.spec.SecretKeySpec;

import org.sz.sproxy.SocksException;
import org.sz.sproxy.tunnel.Crypto;
import org.sz.sproxy.tunnel.SecretManager;
import org.sz.sproxy.tunnel.Tunnel;
import org.sz.sproxy.tunnel.TunnelCmd;
import org.sz.sproxy.tunnel.TunnelCmdState;
import org.sz.sproxy.tunnel.TunnelContext;
import org.sz.sproxy.tunnel.TunnelPacketReader;

/**
 * @author Sam Zheng
 *
 */
public class TunnelServerKAState extends TunnelCmdState<SocketChannel, TunnelServerConnection>
		implements TunnelCmd {
	
	public static final String NAME = "KA";

	public TunnelServerKAState() {
		super(NAME);
		addHandler(Byte.valueOf(Tunnel.KARQ), this);
	}
	
	@Override
	public boolean isChannelCmd() {
		return false;
	}

	@Override
	public void execute(Tunnel tunnel, TunnelPacketReader reader, Consumer<Object> onFinish, Object ctx) {
		try {
			ByteBuffer packet = reader.getPayload();
			byte[] ka = new byte[packet.remaining()];
			packet.get(ka);
			SecretManager sec = ((TunnelContext)tunnel.getContext()).getSecretManager();
			PublicKey peerKey = sec.decodeKAPublicKey(ka);

			KeyPair keyPair = sec.generateKAKeyPair(sec.getSpec(peerKey));

			KeyAgreement agreement = sec.getKeyAgreement(keyPair.getPrivate());
			agreement.doPhase(peerKey, true);
			byte[] secret = agreement.generateSecret();
			SecretKeySpec secKey = sec.getSecretKeySpec(secret);
			Crypto crypto = sec.createCrypto(secKey);

			byte[] k = keyPair.getPublic().getEncoded();
			ByteBuffer out = ByteBuffer.wrap(k);
			TunnelServerConnection conn = ((TunnelServerConnection) tunnel);
			conn.setCrypto(crypto); // further data will be encrypted
			
			conn.moveTo(TunnelServerAuthState.NAME, new byte[][] {ka, k});
			tunnel.getWriter(Tunnel.KARP, tunnel.getPlainWriter()).write(out); // this message is still unencrypted
		} catch (IOException | InvalidKeyException | IllegalStateException e) {
			throw new SocksException(e);
		}

	}

}
