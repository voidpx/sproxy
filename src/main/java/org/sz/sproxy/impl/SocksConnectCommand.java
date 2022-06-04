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
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.sz.sproxy.ChannelHandler;
import org.sz.sproxy.Context;
import org.sz.sproxy.SocksCommand;
import org.sz.sproxy.SocksConnection;
import org.sz.sproxy.SocksException;
import org.sz.sproxy.Writable;

/**
 * @author Sam Zheng
 *
 */
public class SocksConnectCommand implements SocksCommand {

//	  +----+-----+-------+------+----------+----------+
//    |VER | CMD |  RSV  | ATYP | DST.ADDR | DST.PORT |
//    +----+-----+-------+------+----------+----------+
//    | 1  |  1  | X'00' |  1   | Variable |    2     |
//    +----+-----+-------+------+----------+----------+

// 	  +----+-----+-------+------+----------+----------+
//    |VER | REP |  RSV  | ATYP | BND.ADDR | BND.PORT |
//    +----+-----+-------+------+----------+----------+
//    | 1  |  1  | X'00' |  1   | Variable |    2     |
//    +----+-----+-------+------+----------+----------+
//  o  VER    protocol version: X'05'
//  o  REP    Reply field:
//     o  X'00' succeeded
//     o  X'01' general SOCKS server failure
//     o  X'02' connection not allowed by ruleset
//     o  X'03' Network unreachable
//     o  X'04' Host unreachable
//     o  X'05' Connection refused
//     o  X'06' TTL expired
//     o  X'07' Command not supported
//     o  X'08' Address type not supported
//     o  X'09' to X'FF' unassigned
//  o  RSV    RESERVED
//  o  ATYP   address type of following address
//    o  IP V4 address: X'01'
//    o  DOMAINNAME: X'03'
//    o  IP V6 address: X'04'
// o  BND.ADDR       server bound address
// o  BND.PORT       server bound port in network octet order

	public static final byte IPV4 = 1;

	public static final byte IPV6 = 4;

	public static final byte DN = 3;

	@Override
	public void execute(SocksConnection connection, Consumer<Object> onFinish,
			Object ctx) throws SocksException {
		try {
			ByteBuffer buf = ByteBuffer.allocate(256);
			if (connection.read(buf) == -1) {
				throw new SocksException("peer closed");
			}
			buf.flip();
			connect(buf, connection, onFinish, ctx);
		} catch (IOException e) {
			throw new SocksException(e);
		}
	}

	protected InetSocketAddress getAddress(ByteBuffer buf) throws UnknownHostException {
		return getTargetAddress(buf);
	}

	protected void connect(ByteBuffer buffer, SocksConnection connection,
			Consumer<Object> onFinish, Object ctx) throws IOException {
		InetSocketAddress addr = getAddress(buffer);
		connection.connectRemote(addr, getConnectedCallback(connection, onFinish, ctx), ctx);
	}

	protected BiConsumer<ChannelHandler<SocketChannel>, Writable> getConnectedCallback(
			SocksConnection connection, Consumer<Object> onFinish, Object ctx) {
		return connectedCallback(connection, SocksStateConnected.NAME, onFinish, ctx);
	}
	
	public static BiConsumer<ChannelHandler<SocketChannel>, Writable> connectedCallback(
			SocksConnection connection, String connectedState,
			Consumer<Object> onFinish, Object ctx) {
		return (c, sink) -> {
			try {
				Optional.ofNullable(connectedState).ifPresent(s -> connection.moveTo(connectedState, null));
				InetSocketAddress local = (InetSocketAddress) c.getChannel().getLocalAddress();
				InetAddress a = local.getAddress();
				ByteBuffer buf = ByteBuffer.allocate(22);
				buf.clear();
				buf.put(Context.SOCKS_VERSION); // VER
				buf.put((byte) 0); // REP
				buf.put((byte) 0); // RSV
				if (a instanceof Inet4Address) {
					buf.put(IPV4);
				} else if (a instanceof Inet6Address) {
					buf.put(IPV6);
				}
				buf.put(a.getAddress());
				int port = local.getPort();
				buf.put((byte)((port >> 8) & 0xFF));
				buf.put((byte)(port & 0xFF));
				buf.flip();
				sink.write(buf);
				Optional.ofNullable(onFinish).ifPresent(f -> f.accept(ctx));
			} catch (IOException e) {
				throw new SocksException(e);
			}
		};
	}
	
	public static InetSocketAddress getTargetAddress(ByteBuffer buf) throws UnknownHostException {
		InetSocketAddress addr;
		int port;
		buf.get(); // remove the reserved byte
		switch (buf.get()) {
		case IPV4: // ipv4
			byte[] a = new byte[4];
			buf.get(a);
			port = (buf.get() & 0xFF) << 8 | (buf.get() & 0xFF);
			addr = new InetSocketAddress(Inet4Address.getByAddress(a), port);
			break;
		case DN: // domain name
			int nlen = buf.get();
			a = new byte[nlen];
			buf.get(a);
			String name = new String(a, StandardCharsets.UTF_8);
			port = (buf.get() & 0xFF) << 8 | (buf.get() & 0xFF);
			addr = new InetSocketAddress(name, port);
			break;
		case IPV6: // ipv6
			a = new byte[16];
			buf.get(a);
			port = (buf.get() & 0xFF) << 8 | (buf.get() & 0xFF);
			addr = new InetSocketAddress(Inet6Address.getByAddress(a), port);
			break;
		default:
			throw new SocksException("Invalid address type: " + buf.get(1));
		}
		return addr;
	}

}
