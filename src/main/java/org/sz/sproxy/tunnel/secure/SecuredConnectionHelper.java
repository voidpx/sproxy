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

import java.io.IOException;
import java.nio.ByteBuffer;

import org.sz.sproxy.Context;
import org.sz.sproxy.Readable;
import org.sz.sproxy.Writable;
import org.sz.sproxy.Writable.WR;
import org.sz.sproxy.impl.PacketReader;
import org.sz.sproxy.tunnel.Crypto;
import org.sz.sproxy.tunnel.SecretManager;
import org.sz.sproxy.tunnel.TunnelContext;

import lombok.Getter;
import lombok.Setter;

/**
 * @author Sam Zheng
 *
 */
public class SecuredConnectionHelper implements Readable {
	@Getter
	@Setter
	private Crypto crypto;
	@Setter
	private Readable channel;
	private PacketReader reader;
	private ByteBuffer pending;
	SecretManager secretManager;
	
	public SecuredConnectionHelper(Readable readable, Context context) {
		this.channel = readable;
		this.secretManager = ((TunnelContext)context).getSecretManager();
	}

	private int readPending(ByteBuffer buffer) {
		int n = Math.min(buffer.remaining(), pending.remaining());
		buffer.put(buffer.position(), pending, pending.position(), n);
		buffer.position(buffer.position() + n);
		pending.position(pending.position() + n);
		return n;
	}

	@Override
	public int read(ByteBuffer buffer) throws IOException {
		if (crypto == null) {
			return channel.read(buffer);
		}
		if (pending != null && pending.remaining() > 0) {
			return readPending(buffer);
		}
		if (reader == null) {
			reader = new PacketReader();
		}
		if (reader.read(channel)) {
			ByteBuffer packet = reader.getPacket();
			int i = packet.get();
			byte[] iv = new byte[i];
			packet.get(iv);
			int n = packet.getInt();
			byte[] data = new byte[n];
			packet.get(data);
		    byte[] buf = crypto.decrypt(data, iv);
			pending = ByteBuffer.wrap(buf);
			reader = null;
			return readPending(buffer);
		}
		return 0;
	}
	
	public WR write(ByteBuffer buffer, Writable sink) throws IOException {
		if (crypto == null) {
			return sink.write(buffer);
		}
		byte[] buf = new byte[buffer.remaining()];
		buffer.get(buf);
		byte[] iv = secretManager.getIV();
		buf = crypto.encrypt(buf, iv);
		int len = 4 + iv.length + 1 + buf.length;
	    ByteBuffer bf = ByteBuffer.allocate(4 + len);
	    bf.putInt(len);
	    bf.put((byte)iv.length);
	    bf.put(iv);
	    // |--|--|--|--|-----------------------
	    // | length    | data
	    bf.putInt(buf.length);
	    bf.put(buf);
	    bf.flip();
		return sink.write(bf);
	}
}
