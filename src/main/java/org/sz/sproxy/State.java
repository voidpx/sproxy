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

import java.io.IOException;
import java.nio.channels.NetworkChannel;

/**
 * This interface represents a specific state of a {@linkplain StatefulHandler} and defines how data
 * is processed in this state.
 * 
 * @author Sam Zheng
 *
 */
public interface State<C extends NetworkChannel, H extends StatefulHandler<C, H>> {
	
	String getName();
	
	/**
	 * Initializes this state when this state is entered.
	 * 
	 * @param handler
	 * @param info
	 * @return
	 * @throws IOException
	 */
	default State<C, H> init(H handler, Object info) throws IOException {
		return this;
	}
	
	/**
	 * Processes the connection.
	 * 
	 * @param handler
	 * @throws IOException
	 */
	void process(H handler) throws IOException;
	
}
