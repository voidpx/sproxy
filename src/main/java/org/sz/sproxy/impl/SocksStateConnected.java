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
import java.util.Objects;

import org.sz.sproxy.SocksConnection;
import org.sz.sproxy.SocksException;
import org.sz.sproxy.Writable;
import org.sz.sproxy.Writable.WR;

/**
 * @author Sam Zheng
 *
 */
public class SocksStateConnected extends SocksState {
	
	public static final String NAME = "CONNECTED";
	
	public SocksStateConnected() {
		super(NAME);
	}

	@Override
	public WR process(SocksConnection handler) {
		Objects.requireNonNull(handler);
		try {
			return Utils.pump(handler.getContext(), handler, (Writable)handler.getRemote(), handler::close);
		} catch (IOException e) {
			throw new SocksException(e);
		}
	}
	
}
