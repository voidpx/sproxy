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
import java.util.function.Consumer;

import org.sz.sproxy.Writable.WR;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Sam Zheng
 *
 */
@Slf4j
public class TunnelCmdData implements TunnelCmd {

	@Override
	public boolean isChannelCmd() {
		return true;
	}
	
	@Override
	public WR execute(Tunnel tunnel, TunnelPacketReader reader, Consumer<Object> onFinish, Object ctx) {
		int channelId = reader.getChannelId();
		TunneledConnection tunneled = tunnel.getTunneledConnection(channelId);
		try {
			if (tunneled == null) { // probably closed on our side
				log.trace("data received for a non-existent connection {}, probably closed already", channelId);
				TunnelContext context = (TunnelContext)tunnel.getContext();
				context.getTaskExecutor().execute(() -> {
					tunnel.closeChannel(channelId);
				});
			} else {
				return tunneled.write(reader.getPayload());
			}
		} catch (IOException e1) {
			log.debug("error forwarding packet");
			tunneled.close();
		}
		return WR.DONE;
		
	}

}
