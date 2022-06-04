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
import java.nio.ByteBuffer;
import java.util.function.Consumer;

import org.sz.sproxy.SocksConnection;
import org.sz.sproxy.impl.SocksConnectCommand;
import org.sz.sproxy.tunnel.client.TunnelClientConnection;

/**
 * @author Sam Zheng
 *
 */
public class SocksRelayConnectCmd extends SocksConnectCommand {

	
	@Override
	protected void connect(ByteBuffer buffer, SocksConnection connection, 
			Consumer<Object> onFinish, Object ctx)
			throws IOException {
//		+-------+------+----------+----------+
//	    |  RSV  | ATYP | DST.ADDR | DST.PORT |
//	    +-------+------+----------+----------+
//	    | X'00' |  1   | Variable |    2     |
//	    +-------+------+----------+----------+
		SocksRelayConnection conn = (SocksRelayConnection)connection;
		conn.connectRemote(null, null, null);
		TunnelClientConnection tunnel = (TunnelClientConnection) conn.getRemote();
		tunnel.connect(conn, buffer, getConnectedCallback(connection, onFinish, ctx));
	}
	
}
