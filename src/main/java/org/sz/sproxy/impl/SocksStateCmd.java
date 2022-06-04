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
import org.sz.sproxy.SocksCommand;
import org.sz.sproxy.SocksConnection;
import org.sz.sproxy.SocksException;

/**
 * Authenticated state, expecting commands in this state.
 * 
 * @author Sam Zheng
 *
 */
public class SocksStateCmd extends SocksState {
	
	public static final String NAME = "CMD";
	
	protected SocksCommand cmdHandler;

	public SocksStateCmd() {
		super(NAME);
	}

	@Override
	public void process(SocksConnection handler) {
		if (cmdHandler == null) {
			try {
				ByteBuffer buf = ByteBuffer.allocate(2);
				int n = handler.read(buf);
				if (n == -1) {
					throw new SocksException("channel closed");
				}
				if (n != 2) {
					throw new SocksException("Invalid header");
				}
				if (buf.get(0) != Context.SOCKS_VERSION) {
					throw new SocksException("Invalid socks version: " + buf.get(0));
				}
				cmdHandler = handler.getContext().getCommandFactory().createCmdHandler(buf.get(1));
			} catch (IOException e) {
				throw new SocksException(e);
			}
		}
		cmdHandler.execute(handler, null, handler);
	}
	
}