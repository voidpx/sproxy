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
package org.sz.sproxy.impl;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.sz.sproxy.Context;
import org.sz.sproxy.SocksAuthHandler;
import org.sz.sproxy.SocksConnection;
import org.sz.sproxy.SocksException;
import org.sz.sproxy.Writable.WR;

/**
 * @author Sam Zheng
 *
 */
public class NoAuthHandler implements SocksAuthHandler {

	static final byte CODE = 0x0;
	
	@Override
	public WR handleAuth(SocksConnection connection) throws SocksException {
		ByteBuffer buf = ByteBuffer.allocate(257);
		try {
			int n = connection.getChannel().read(buf);
			if (n < 3 || buf.get(0) != Context.SOCKS_VERSION) {
				throw new SocksException("Invalid socks header");
			}
			connection.moveTo(SocksStateCmd.NAME, null);
			return connection.write(ByteBuffer.wrap(new byte[] {Context.SOCKS_VERSION, CODE}));
		} catch (IOException e) {
			throw new SocksException(e);
		}
	}

}
