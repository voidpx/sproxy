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
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.function.BiConsumer;

import org.sz.sproxy.ChannelHandler;
import org.sz.sproxy.Context;
import org.sz.sproxy.StateManager;
import org.sz.sproxy.Writable;
import org.sz.sproxy.impl.NioConnection;
import org.sz.sproxy.impl.Utils;
import org.sz.sproxy.tunnel.Tunnel;
import org.sz.sproxy.tunnel.TunneledConnection;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Sam Zheng
 *
 */
@Slf4j
public class ServerRemoteConnection extends NioConnection<SocketChannel, ServerRemoteConnection>
		implements TunneledConnection {

	@Getter
	int id;

	Tunnel tunnel;

	BiConsumer<ChannelHandler<SocketChannel>, Writable> connected;

	public ServerRemoteConnection(Context context, Tunnel tunnel,
			InetSocketAddress address, BiConsumer<ChannelHandler<SocketChannel>, Writable> connected,
			int id) throws IOException {
		super(context, SocketChannel.open());
		this.id = id;
		this.tunnel = tunnel;
		this.connected = connected;
		key.interestOpsOr(SelectionKey.OP_CONNECT);
		channel.connect(address);
	}
	
	@Override
	protected StateManager createStateManager() {
		return null;
	}

	@Override
	protected void handleConnect() throws IOException {
		if (channel.finishConnect()) {
			log.debug("Remote connected: {}", channel);
			connected.accept(this, tunnel);
		} else {
			throw new ConnectionNotFinished();
		}
	}

	@Override
	protected void handleRead(int ops) throws IOException {
		Utils.pump(context, this, tunnel.getDataWriter(getId(), tunnel), this::close);
	}

	@Override
	protected void closeInternal() throws IOException {
		tunnel.closeChannel(getId());
	}
}
