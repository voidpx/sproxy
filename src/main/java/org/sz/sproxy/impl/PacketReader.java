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
package org.sz.sproxy.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;

import org.sz.sproxy.Readable;

/**
 * @author Sam Zheng
 *
 */
public class PacketReader extends Reader {
	
	protected static final int PACKET_SIZE_SIZE = 4;
	
    protected Reader lenReader = new Reader(PACKET_SIZE_SIZE);
    
    protected Reader packetReader;
    
    public void reset() {
    	Optional.ofNullable(lenReader).ifPresent(r -> r.reset(PACKET_SIZE_SIZE));
    }
    
    @Override
    public void reset(int len) {
    	super.reset(0);
    	reset();
    }

	public ByteBuffer getPacket() throws IOException {
		return packetReader.getPacket();
	}
	
	public boolean read(Readable channel) throws IOException {
		if (!lenReader.isComplete()) {
			lenReader.read(channel);
			if (lenReader.isComplete()) {
				if (packetReader == null) {
					packetReader = new Reader(lenReader.getPacket().getInt());
				} else {
					packetReader.reset(lenReader.getPacket().getInt());
				}
			}
		}
		if (lenReader.isComplete()) {
			packetReader.read(channel);
		}
		return Optional.ofNullable(packetReader).map(Reader::isComplete).orElse(false);
	}
	
	@Override
	public boolean isComplete() {
		return lenReader.isComplete() || (packetReader != null && packetReader.isComplete()); 
	}
	
	public boolean isPending() {
		return lenReader.isPending() || (packetReader != null && packetReader.isPending());
	}

}
