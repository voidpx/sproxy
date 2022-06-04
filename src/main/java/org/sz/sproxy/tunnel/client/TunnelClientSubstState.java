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
import java.util.Objects;
import java.util.function.Consumer;

import org.sz.sproxy.SocksException;
import org.sz.sproxy.State;
import org.sz.sproxy.tunnel.Crypto;
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
public class TunnelClientSubstState extends TunnelCmdState<SocketChannel, TunnelClientSubst> implements TunnelCmd {
	
	TunnelClientConnection origin;

	public TunnelClientSubstState() {
		super("ST");
		addHandler(Tunnel.STRP, this);
	}

	@Override
	public boolean isChannelCmd() {
		return false;
	}
	
	private void wait0(TunnelClientSubst subst) {
		synchronized (subst) {
			try {
				subst.wait();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				log.debug("Interrupted while waiting", e);
			}
		}
	}

	@Override
	public void execute(Tunnel tunnel, TunnelPacketReader reader, Consumer<Object> onFinish, Object ctx) {
		ByteBuffer buf = reader.getPayload();
		if (buf.get() == 0) {
			((TunnelClientSubst)tunnel).setState(new State<SocketChannel, TunnelClientSubst>() {
				
				@Override
				public void process(TunnelClientSubst handler) throws IOException {
					log.debug("packet suspending");
					wait0((TunnelClientSubst)tunnel);
				}
				
				@Override
				public String getName() {
					return "pending";
				}
			});
			TunnelClientSubst s = (TunnelClientSubst)tunnel;
			s.onReady.accept(s);
			return;
		}
		throw new SocksException("Unable to initiate tunnel trampoline connection");
	}

	@Override
	public State<SocketChannel, TunnelClientSubst> init(TunnelClientSubst handler, Object info) throws IOException {
		Objects.requireNonNull(info, "No tunnel client");
		origin = (TunnelClientConnection)info;
		int id = origin.getId();
		byte[] idb = new byte[4];
		ByteBuffer.allocate(4).putInt(id).flip().get(idb);
		Crypto crypto = origin.helper.getCrypto();
		Objects.requireNonNull(crypto, "Invaild state");
		byte[] iv = ((TunnelContext) handler.getContext()).getSecretManager().getIV();
		byte[] idbe = crypto.encrypt(idb, iv);
		ByteBuffer buf = ByteBuffer.allocate(idb.length + iv.length + 4 + idbe.length + 4);
		buf.put(idb);
		buf.putInt(iv.length);
		buf.put(iv);
		buf.putInt(idbe.length);
		buf.put(idbe);
		buf.flip();
		handler.getWriter(Tunnel.STRQ, handler).write(buf);
		return this;
	}
}
