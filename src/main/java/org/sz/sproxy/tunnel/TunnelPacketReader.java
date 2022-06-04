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
package org.sz.sproxy.tunnel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Function;

import org.sz.sproxy.Readable;
import org.sz.sproxy.SocksException;
import org.sz.sproxy.impl.PacketReader;
import org.sz.sproxy.impl.Reader;

/**
 * @author Sam Zheng
 *
 */
public class TunnelPacketReader {
	
	Reader reader;
	
	CmdReader cmdReader = new CmdReader();
	
	ChIdReader channelIdReader = new ChIdReader();
	
	static class CmdReader extends Reader {

		public CmdReader() {
			super(Tunnel.HEADER_LEN);
		}
		
		void reset() {
			reset(Tunnel.HEADER_LEN);
		}
		
		byte getCmd() throws IOException {
			ByteBuffer b = getPacket();
			if (b.get() != Tunnel.MAGIC) {
				throw new SocksException("Invalid tunnel header");
			}
			return getPacket().get();
		}
		
	}
	
	static class ChIdReader extends Reader {

		public ChIdReader() {
			super(4);
		}
		
		void reset() {
			reset(4);
		}
		
		int getChannelId() throws IOException {
			return getPacket().getInt();
		}
	}
	
	byte cmd;
	
	int channelId;
	
	ByteBuffer payload;

	PacketReader payloadReader = new PacketReader();
	
	Function<Byte, TunnelCmd> cmds;
	
	public TunnelPacketReader(Function<Byte, TunnelCmd> cmds) {
		reader = cmdReader;
		this.cmds = cmds;
	}
	
	public void reset() {
		cmdReader.reset();
		channelIdReader.reset();
		payloadReader.reset();
		reader = cmdReader;
	}
	
	public byte getCmd() {
		return cmd;
	}
	
	public int getChannelId() {
		return channelId;
	}
	
	public ByteBuffer getPayload() {
		return payload;
	}
	
	public boolean read(Readable channel) throws IOException {
		while (reader.read(channel)) {
			if (reader instanceof CmdReader) {
				cmd = ((CmdReader)reader).getCmd();
				TunnelCmd handler = cmds.apply(cmd);
				if (handler.isChannelCmd()) {
					reader = channelIdReader;
				} else {
					reader = payloadReader;
				}
				continue;
			} else if (reader instanceof ChIdReader) {
				channelId = ((ChIdReader)reader).getChannelId();
				reader = payloadReader;
				continue;
			}
			payload = payloadReader.getPacket();
			return true;
			
		}
		return false;
	}

}
