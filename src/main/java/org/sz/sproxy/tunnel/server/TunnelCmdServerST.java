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
import java.util.Objects;
import java.util.function.Consumer;

import org.sz.sproxy.SocksException;
import org.sz.sproxy.impl.Utils;
import org.sz.sproxy.tunnel.Crypto;
import org.sz.sproxy.tunnel.Tunnel;
import org.sz.sproxy.tunnel.TunnelCmd;
import org.sz.sproxy.tunnel.TunnelPacketReader;

/**
 * @author Sam Zheng
 *
 */
public class TunnelCmdServerST implements TunnelCmd {

	@Override
	public boolean isChannelCmd() {
		return false;
	}
	

	@Override
	public void execute(Tunnel tunnel, TunnelPacketReader reader, Consumer<Object> onFinish, Object ctx) {
		ByteBuffer buf = reader.getPayload();
		int id = buf.getInt();
		TunnelServerContext context = (TunnelServerContext)tunnel.getContext();
		TunnelServerConnection old = context.getConnection(id);
		Objects.requireNonNull(old.helper, "helper is null");
		Crypto crypto = old.helper.getCrypto();
		int ivl = buf.getInt();
		Utils.sanitizePacketSize(ivl);
		byte[] iv = new byte[ivl];
		buf.get(iv);
		int idbel = buf.getInt();
		Utils.sanitizePacketSize(idbel);
		byte[] idbe = new byte[idbel];
		buf.get(idbe);
		byte[] decoded = crypto.decrypt(idbe, iv);
		int ide = ByteBuffer.wrap(decoded).getInt();
		if (ide != id) {
			throw new SocksException("ST: invalid id " + id + " != " + ide);
		}
		TunnelServerConnection newTunnel = (TunnelServerConnection)tunnel;
		old.setNewTunnel(newTunnel);
		try {
			tunnel.getWriter(Tunnel.STRP, tunnel).write(ByteBuffer.wrap(new byte[] {0}));
		} catch (IOException e) {
			throw new SocksException(e);
		}
	}

}
