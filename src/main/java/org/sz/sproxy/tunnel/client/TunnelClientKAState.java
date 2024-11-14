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
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.function.Consumer;

import javax.crypto.KeyAgreement;
import javax.crypto.spec.SecretKeySpec;

import org.sz.sproxy.SocksException;
import org.sz.sproxy.State;
import org.sz.sproxy.Writable.WR;
import org.sz.sproxy.tunnel.Crypto;
import org.sz.sproxy.tunnel.SecretManager;
import org.sz.sproxy.tunnel.Tunnel;
import org.sz.sproxy.tunnel.TunnelCmd;
import org.sz.sproxy.tunnel.TunnelCmdState;
import org.sz.sproxy.tunnel.TunnelContext;
import org.sz.sproxy.tunnel.TunnelPacketReader;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Sam Zheng
 *
 */
@Slf4j
public class TunnelClientKAState extends TunnelCmdState<SocketChannel, TunnelClientConnection>
		implements TunnelCmd {
	
	public static final String NAME = "KA";

	KeyAgreement agreement;
	
	byte[] sigData;

	public TunnelClientKAState() {
		super(NAME);
		addHandler(Byte.valueOf(Tunnel.KARP), this);

	}
	
	@Override
	public boolean isChannelCmd() {
		return false;
	}

	@Override
	public WR execute(Tunnel tunnel, TunnelPacketReader reader, Consumer<Object> onFinish, Object ctx) {
		ByteBuffer packet = reader.getPayload();
		byte[] key = new byte[packet.remaining()];
		packet.get(key);
		SecretManager sec = ((TunnelContext)tunnel.getContext()).getSecretManager();
		PublicKey peerKey = sec.decodeKAPublicKey(key);
		try {
			agreement.doPhase(peerKey, true);
		} catch (InvalidKeyException | IllegalStateException e) {
			throw new SocksException(e);

		}
		byte[] secret = agreement.generateSecret();
		SecretKeySpec secKey = sec.getSecretKeySpec(secret);

		Crypto crypto = sec.createCrypto(secKey);

		TunnelClientConnection conn = (TunnelClientConnection) tunnel;
		conn.setCrypto(crypto);
		log.debug("shared key established");

		conn.moveTo(TunnelClientAuthState.NAME, new byte[][] {key, sigData});
		return WR.DONE;
	}

	@Override
	public State<SocketChannel, TunnelClientConnection> init(TunnelClientConnection connection, Object info) {
		try {
			SecretManager sec = ((TunnelContext)connection.getContext()).getSecretManager();
			KeyPair keyPair = sec.generateKAKeyPair();
			agreement = sec.getKeyAgreement(keyPair.getPrivate());
			byte[] key = keyPair.getPublic().getEncoded();
			sigData = new byte[key.length];
			System.arraycopy(key, 0, sigData, 0, key.length);
			connection.getWriter(Tunnel.KARQ, connection.getPlainWriter()).write(ByteBuffer.wrap(key));
			return this;
		} catch (IOException e) {
			throw new SocksException(e);
		}
	}

}
