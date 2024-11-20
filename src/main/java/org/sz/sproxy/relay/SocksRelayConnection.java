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
package org.sz.sproxy.relay;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.function.BiConsumer;

import org.sz.sproxy.ChannelHandler;
import org.sz.sproxy.Context;
import org.sz.sproxy.StateManager;
import org.sz.sproxy.Writable;
import org.sz.sproxy.impl.SocksConnectionImpl;
import org.sz.sproxy.tunnel.client.RelayedConnection;
import org.sz.sproxy.tunnel.client.TunnelClient;

import lombok.Getter;
import lombok.Setter;

/**
 * @author Sam Zheng
 *
 */
public class SocksRelayConnection extends SocksConnectionImpl implements RelayedConnection {

	@Getter
	@Setter
	int id;

	public SocksRelayConnection(Context context, SocketChannel channel) throws IOException {
		super(context, channel);
	}
	
	@Override
	protected StateManager createStateManager() {
		return new SocksRelayStateManager();
	}

	@Override
	public void connectRemote(InetSocketAddress address,
			BiConsumer<ChannelHandler<SocketChannel>, Writable> connected, Object ctx) throws IOException {
		SocksRelayContext context = (SocksRelayContext) getContext();
		TunnelClient t = context.getPool().tunnel(this);
		remote = t;

	}

	@Override
	protected void closeInternal() throws IOException {
		if (remote != null) {
			((TunnelClient) remote).close(this);
		}
	}
}
