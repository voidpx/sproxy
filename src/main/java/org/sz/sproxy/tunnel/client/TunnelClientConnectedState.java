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

import org.sz.sproxy.State;
import org.sz.sproxy.tunnel.Tunnel;
import org.sz.sproxy.tunnel.TunnelCmdClose;
import org.sz.sproxy.tunnel.TunnelCmdData;
import org.sz.sproxy.tunnel.TunnelCmdState;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Sam Zheng
 *
 */
@Slf4j
public class TunnelClientConnectedState extends TunnelCmdState<SocketChannel, TunnelClientConnection> {
	
	public static final String NAME = "CONNECTED";

	public TunnelClientConnectedState() {
		super(NAME);
		addHandler(Byte.valueOf(Tunnel.CONNECTRP), new TunnelCmdConnectReply());
		addHandler(Byte.valueOf(Tunnel.CLOSE), new TunnelCmdClose());
		addHandler(Byte.valueOf(Tunnel.DATA), new TunnelCmdData());
	}

	@Override
	public State<SocketChannel, TunnelClientConnection> init(TunnelClientConnection handler, Object info) throws IOException {
		log.debug("Tunnel connected");
		return this;
	}
	
}
