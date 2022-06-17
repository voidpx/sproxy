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
package org.sz.sproxy.tunnel.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;

import org.sz.sproxy.Context;
import org.sz.sproxy.StateManager;
import org.sz.sproxy.Writable;
import org.sz.sproxy.impl.NioConnection;
import org.sz.sproxy.tunnel.Tunnel;
import org.sz.sproxy.tunnel.TunneledConnection;

/**
 * @author Sam Zheng
 * 
 * @deprecated unstable, subject to change or removal
 */
public class TunnelClientSubst extends NioConnection<SocketChannel, TunnelClientSubst> implements Tunnel {
	TunnelClientConnection origin;
	Consumer<TunnelClientSubst> onReady;
	public TunnelClientSubst(Context context, InetSocketAddress address, Consumer<TunnelClientSubst> onReady,
			TunnelClientConnection origin) throws IOException {
		super(context, SocketChannel.open());
		this.origin = origin;
		key.interestOpsOr(SelectionKey.OP_CONNECT);
		this.onReady = onReady;
		channel.connect(address);
	}
	
	SelectionKey getKey() {
		return key;
	}
	
	synchronized void switchDone() {
		notifyAll();
	}

	@Override
	protected StateManager createStateManager() {
		return new TunnelClientSubstManager();
	}
	
	@Override
	protected void handleConnect() throws IOException {
		if (getChannel().finishConnect()) {
			getStateManager().moveTo(getStateManager().getInitState(), this, origin);
		} else {
			throw new ConnectionNotFinished();
		}
	}

	@Override
	public int getId() {
		return 0;
	}

	@Override
	public TunneledConnection getTunneledConnection(int id) {
		return null;
	}

	@Override
	public Writable getPlainWriter() {
		return this::write;
	}
	
	@Override
	public void close() {
		// TODO Auto-generated method stub
		super.close();
	}

}
