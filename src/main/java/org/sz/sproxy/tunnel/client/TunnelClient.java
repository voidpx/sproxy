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
import java.nio.channels.SocketChannel;

import org.sz.sproxy.Attachable;
import org.sz.sproxy.ChannelHandler;
import org.sz.sproxy.tunnel.Tunnel;

/**
 * @author Sam Zheng
 *
 */
public interface TunnelClient extends Tunnel, ChannelHandler<SocketChannel> , Attachable {
	
	int getRelayedCount();
	
	RelayedConnection tunnel(RelayedConnection conn);
	
	void close(RelayedConnection conn);
	
	void pump(RelayedConnection conn) throws IOException;
	
	
	/**
	 * not needed.
	 */
	@Deprecated(since = "1.0.1")
	void livenessTick();

}
