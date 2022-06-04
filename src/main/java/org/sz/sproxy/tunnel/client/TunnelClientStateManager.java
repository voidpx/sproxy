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

import org.sz.sproxy.impl.AbstractStateManager;

/**
 * @author Sam Zheng
 *
 */
public class TunnelClientStateManager extends AbstractStateManager {
	
	public TunnelClientStateManager() {
		addState(TunnelClientKAState.NAME, TunnelClientKAState::new);
		addState(TunnelClientAuthState.NAME, TunnelClientAuthState::new);
		addState(TunnelClientConnectedState.NAME, TunnelClientConnectedState::new);
	}

	@Override
	public String getInitState() {
		return TunnelClientKAState.NAME;
	}

}
