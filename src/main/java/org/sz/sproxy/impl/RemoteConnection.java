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
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.function.BiConsumer;

import org.sz.sproxy.ChannelHandler;
import org.sz.sproxy.Context;
import org.sz.sproxy.StateManager;
import org.sz.sproxy.Writable;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Sam Zheng
 *
 */
@Slf4j
public class RemoteConnection extends NioConnection<SocketChannel, RemoteConnection> {

	protected BiConsumer<ChannelHandler<SocketChannel>, Writable> connected;

	protected SocksConnectionImpl local;

	public RemoteConnection(SocksConnectionImpl local, Context context, InetSocketAddress address,
			BiConsumer<ChannelHandler<SocketChannel>, Writable> connected) throws IOException {
		super(context, SocketChannel.open());
		this.local = local;
		key.interestOpsOr(SelectionKey.OP_CONNECT);
		this.connected = connected;
		channel.connect(address);
	}
	
	@Override
	protected StateManager createStateManager() {
		return null;
	}
	
	@Override
	protected void handleRead(int ops) throws IOException {
		Utils.pump(context, this, local, this::close);
	}

	@Override
	protected void closeInternal() throws IOException {
		local.close();
	}
	
	@Override
	protected void handleConnect() throws IOException {
		if (channel.finishConnect()) {
			log.debug("Remote connected: {}", channel);
			connected.accept(this, local);
		} else {
			throw new IOException("Unable to finish connecting: " + channel);
		}
	}

}
