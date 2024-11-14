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
import java.nio.ByteBuffer;
import java.util.function.Consumer;

import org.sz.sproxy.SocksException;
import org.sz.sproxy.Writable.WR;
import org.sz.sproxy.tunnel.Tunnel;
import org.sz.sproxy.tunnel.TunnelCmd;
import org.sz.sproxy.tunnel.TunnelPacketReader;

/**
 * @author Sam Zheng
 *
 */
@Deprecated
public class TunnelCmdLPReq implements TunnelCmd {

	@Override
	public boolean isChannelCmd() {
		return false;
	}

	@Override
	public WR execute(Tunnel tunnel, TunnelPacketReader reader, Consumer<Object> onFinish, Object ctx) {
		try {
			tunnel.getWriter(Tunnel.LPRP, tunnel).write(ByteBuffer.wrap(new byte[0]));
		} catch (IOException e) {
			throw new SocksException(e);
		}
		return WR.DONE;
	}

}
