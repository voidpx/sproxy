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
import java.util.function.Consumer;

import org.sz.sproxy.ContextAccess;
import org.sz.sproxy.Readable;
import org.sz.sproxy.SocksException;
import org.sz.sproxy.Writable;

/**
 * @author Sam Zheng
 *
 */
public interface Tunnel extends ContextAccess, Writable, Readable, Identifiable {
// 	+----+-----+-------+------+-
//  |MAG | CMD |  RSV  |packet| 
//  +----+-----+-------+------+-
//  |0xA |     |  0x0  |      |
//  +----+-----+-------+------+-
// or	
//	+----+-----+-------+---------+-------+-
//  |MAG | CMD |  RSV  |channelid| packet| 
//  +----+-----+-------+---------+-------+-
//  |0xA |     |  0x0  |         |       |
//  +----+-----+-------+---------+-------+-

	byte MAGIC = 0xA;

	// commands
	
	// key agreement
	byte KARQ = 0x1;
	byte KARP = 0x2;
	
	// auth
	byte AUTHRQ = 0x3;
	byte AUTHRP = 0x4;
	
	// === experimental, unstable/useable ===
	// switch tunnel
	byte STRQ = 0x5;
	byte STRP = 0x6;
	byte STM = 0x7;
	// === experimental, unstable, end ===
	
	// liveness probe
	byte LPRQ = 0x8;
	byte LPRP = 0x9;
	
	// channel commands
	byte CONNECTRQ = 0x11;
	byte CONNECTRP = 0x12;
	byte DATA = 0x13;
	byte CLOSE = 0x14;

	// reserved
	byte RESERVED = 0x0;

	int HEADER_LEN = 3;
	
	TunneledConnection getTunneledConnection(int id);
	
	default void closeChannel(int id) {
		try {
			getWriter(id, CLOSE, this).write(ByteBuffer.wrap(new byte[0]));
		} catch (IOException e) {
			throw new SocksException(e); // something wrong with tunnel, close the tunnel
		};
	}
	
	default Writable getDataWriter(int id, Writable to) {
		return getWriter(id, DATA, to);
	}
	
	// TODO: optimize the buffer allocation
	default Writable getWriter(int id, byte cmd, Writable to) {
		return (b) -> {
			ByteBuffer buf = prepareBuffer(8 + b.remaining(), cmd);
			buf.putInt(id);
			buf.putInt(b.remaining());
			buf.put(b);
			buf.flip();
			to.write(buf);
		};
	}
	
	default Writable getWriter(byte cmd, Writable to) {
		return (b) -> {
			ByteBuffer buf = prepareBuffer(4 + b.remaining(), cmd);
			buf.putInt(b.remaining());
			buf.put(b);
			buf.flip();
			to.write(buf);
		};
	}
	
	Writable getPlainWriter();

	default Writable getWriter(TunneledConnection client, byte cmd, Writable to) {
		return getWriter(client.getId(), cmd, to);
	}

	default ByteBuffer prepareBuffer(int size, byte cmd) {
		ByteBuffer buffer = ByteBuffer.allocate(HEADER_LEN + size);
		prepareHeader(buffer, cmd);
		return buffer;
	}

	static void prepareHeader(ByteBuffer buffer, byte cmd) {
		buffer.put(MAGIC);
		buffer.put(cmd);
		buffer.put(RESERVED);
	}

	static boolean isKexRq(byte cmd) {
		return cmd == KARQ;
	}

	static boolean isKexRp(byte cmd) {
		return cmd == KARP;
	}

	interface Call {
		void run() throws Throwable;
	}

	default void execCatchAll(Call call, Consumer<Throwable> onError) {
		try {
			call.run();
		} catch (Throwable e) {
			onError.accept(e);
		}
	}
}
