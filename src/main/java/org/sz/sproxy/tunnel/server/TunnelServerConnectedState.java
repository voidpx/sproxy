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
public class TunnelServerConnectedState extends TunnelCmdState<SocketChannel, TunnelServerConnection> {
	
	public static final String NAME = "CONNECTED";

	public TunnelServerConnectedState() {
		super(NAME);
		addHandler(Byte.valueOf(Tunnel.CONNECTRQ), new TunnelCmdConnect());
		addHandler(Byte.valueOf(Tunnel.DATA), new TunnelCmdData());
		addHandler(Byte.valueOf(Tunnel.CLOSE), new TunnelCmdClose());
		addHandler(Byte.valueOf(Tunnel.LPRQ), new TunnelCmdLPReq());
	}

	@Override
	public State<SocketChannel, TunnelServerConnection> init(TunnelServerConnection handler, Object info) throws IOException {
		log.debug("CONNECTED");
		return this;
	}

}
