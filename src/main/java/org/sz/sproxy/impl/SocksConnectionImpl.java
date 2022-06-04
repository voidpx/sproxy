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
import java.nio.channels.SocketChannel;
import java.util.function.BiConsumer;

import org.sz.sproxy.ChannelHandler;
import org.sz.sproxy.Context;
import org.sz.sproxy.SocksConnection;
import org.sz.sproxy.StateManager;
import org.sz.sproxy.Writable;

import lombok.Getter;

/**
 * @author Sam Zheng
 *
 */
public class SocksConnectionImpl extends NioConnection<SocketChannel, SocksConnection> implements SocksConnection {

	@Getter
	protected ChannelHandler<SocketChannel> remote;

	@Getter
	protected Object attachment;

	public SocksConnectionImpl(Context context, SocketChannel channel) throws IOException {
		super(context, channel);
		moveTo(stateManager.getInitState(), null);
	}
	
	@Override
	protected StateManager createStateManager() {
		return new SocksStateManager();
	}

	@Override
	protected void closeInternal() throws IOException {
		if (remote != null) {
			remote.close();
		}
	}

	@Override
	public void connectRemote(InetSocketAddress address, BiConsumer<ChannelHandler<SocketChannel>, Writable> connected,
			Object ctx) throws IOException {
		remote = new RemoteConnection(this, context, address, connected);
	}

	@Override
	public void attach(Object attachment) {
		this.attachment = attachment;
	}

}
