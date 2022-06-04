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

import org.sz.sproxy.SocksCommand;
import org.sz.sproxy.SocksCommandFactory;

/**
 * @author Sam Zheng
 *
 */
public class SocksCommandFactoryImpl implements SocksCommandFactory {

	@Override
	public SocksCommand createCmdHandler(byte cmd) {
		switch (cmd) {
		case 1:
			return createConnectCmd();
		}
		throw new IllegalArgumentException("Unimplemented command: " + cmd);
	}
	
	protected SocksCommand createConnectCmd() {
		return new SocksConnectCommand();
	}

}
