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
package org.sz.sproxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.function.BiConsumer;

/**
 * This interface represents a SOCKS5 connection.
 * 
 * @author Sam Zheng
 *
 */
public interface SocksConnection extends StatefulHandler<SocketChannel, SocksConnection>, Attachable {
	
	/**
	 * Returns the remote end of this connection.
	 * 
	 * @return
	 */
	ChannelHandler<SocketChannel> getRemote();
	
	/**
	 * Connects to the destination on behalf of the client.
	 * 
	 * @param address
	 * @param connected
	 * @param ctx
	 * @throws IOException
	 */
	void connectRemote(InetSocketAddress address, BiConsumer<ChannelHandler<SocketChannel>, Writable> connected,
			Object ctx) throws IOException;
	
}
