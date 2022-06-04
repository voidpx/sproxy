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

import java.nio.channels.NetworkChannel;

/**
 * A {@linkplain StatefulHandler} can be in some specific states, its {@linkplain StateManager} defines these states
 * and the transition between these states.
 * 
 * @author Sam Zheng
 *
 */
public interface StatefulHandler<C extends NetworkChannel, H extends StatefulHandler<C, H>>
		extends ChannelHandler<C>, Readable, Writable {

	State<C, H> getState();

	void setState(State<C, H> state);
	
	State<C, H> moveTo(String state, Object initInfo);
	
	StateManager getStateManager();
}
