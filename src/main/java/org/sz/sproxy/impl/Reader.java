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

import org.sz.sproxy.Readable;

/**
 * @author Sam Zheng
 *
 */
public class Reader {
	protected ByteBuffer buf;
	protected boolean complete;
	
	public Reader() {
		this(0);
	}
	
	public Reader(int len) {
		reset(len);
	}
	
	public void reset(int len) {
		if (buf != null && len <= buf.capacity()) {
			buf.position(0);
			buf.limit(len);
		} else {
			Utils.sanitizePacketSize(len);
			buf = ByteBuffer.allocate(len); 
		}
		complete = false;
	}
	
	public ByteBuffer getPacket() throws IOException {
		if (!complete) {
			throw new IOException("Packet not completely read");
		}
		return buf;
	}
	
	public boolean isPending() {
		return buf.position() > 0;
	}
	
	public boolean isComplete() {
		return complete;
	}
	
	public boolean read(Readable channel) throws IOException {
		if (channel.read(buf) == -1) {
			throw new IOException("peer closed");
		}
		if (buf.position() == buf.limit()) {
			buf.flip(); // prepare to be used
			complete = true;
		}
		return complete;
	}
}
