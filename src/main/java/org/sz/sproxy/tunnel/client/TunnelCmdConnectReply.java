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

import java.nio.channels.SocketChannel;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.sz.sproxy.ChannelHandler;
import org.sz.sproxy.Writable;
import org.sz.sproxy.Writable.WR;
import org.sz.sproxy.tunnel.Tunnel;
import org.sz.sproxy.tunnel.TunnelCmd;
import org.sz.sproxy.tunnel.TunnelContext;
import org.sz.sproxy.tunnel.TunnelPacketReader;
import org.sz.sproxy.tunnel.TunneledConnection;

/**
 * @author Sam Zheng
 *
 */
public class TunnelCmdConnectReply implements TunnelCmd {
	
	@Override
	public boolean isChannelCmd() {
		return true;
	}

	@SuppressWarnings("unchecked")
	@Override
	public WR execute(Tunnel tunnel, TunnelPacketReader reader, Consumer<Object> onFinish, Object ctx) {
		int channelId = reader.getChannelId();
		TunneledConnection tunneled = tunnel.getTunneledConnection(channelId);
		RelayedConnection conn = (RelayedConnection) tunneled;
		if (conn == null) {
			// proxied connection already closed
			TunnelContext context = (TunnelContext)tunnel.getContext();
			context.getTaskExecutor().execute(() -> {
				tunnel.closeChannel(channelId);
			});
		} else {
			((BiConsumer<ChannelHandler<SocketChannel>, Writable>) conn.getAttachment())
			.accept((ChannelHandler<SocketChannel>) tunnel, conn);
		}
		return WR.DONE;
	}

}
