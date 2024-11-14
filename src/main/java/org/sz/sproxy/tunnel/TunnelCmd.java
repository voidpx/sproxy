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
package org.sz.sproxy.tunnel;

import java.util.function.Consumer;

import org.sz.sproxy.Writable.WR;

/**
 * A tunnel command.
 * 
 * @author Sam Zheng
 *
 */
public interface TunnelCmd {
	
	/**
	 * Return true if this is a channel command, i.e. associated with a channel.
	 * 
	 * @return
	 */
	boolean isChannelCmd();
	
	/**
	 * Executes this command.
	 * 
	 * @param tunnel
	 * @param reader
	 * @param onFinish
	 * @param ctx
	 */
	WR execute(Tunnel tunnel, TunnelPacketReader reader, Consumer<Object> onFinish, Object ctx);

}
