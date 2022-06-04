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
import java.nio.channels.NetworkChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.sz.sproxy.SocksException;
import org.sz.sproxy.State;
import org.sz.sproxy.StateManager;
import org.sz.sproxy.StatefulHandler;

/**
 * @author Sam Zheng
 *
 */
public abstract class AbstractStateManager implements StateManager {

	Map<String, Supplier<State<?, ?>>> states = new HashMap<>();

	protected void addState(String name, Supplier<State<?, ?>> state) {
		states.put(name, state);
	}
	
	protected Supplier<State<?, ?>> getState(String name) {
		Supplier<State<?, ?>> state = states.get(name);
		Objects.requireNonNull(state, "Invalid state: " + name);
		return state;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <C extends NetworkChannel, H extends StatefulHandler<C, H>> State<C, H> moveTo(String state, H connection,
			Object initInfo) {
		State<C, H> s = (State<C, H>) getState(state).get();
		try {
			connection.setState(s.init(connection, initInfo));
		} catch (IOException e) {
			throw new SocksException(e);
		}
		return s;
	}

}
