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

import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.nio.channels.NetworkChannel;
import java.util.HashMap;
import java.util.Map;

import org.sz.sproxy.SocksException;
import org.sz.sproxy.State;
import org.sz.sproxy.StatefulHandler;
import org.sz.sproxy.Writable.WR;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Sam Zheng
 *
 */
@Slf4j
public abstract class TunnelCmdState<C extends ByteChannel & NetworkChannel, T extends StatefulHandler<C, T>>
		implements State<C, T> {

	protected Map<Byte, TunnelCmd> handlers = new HashMap<>();

	protected String name;

	private TunnelPacketReader reader = new TunnelPacketReader(this::getHandler);

	public TunnelCmdState(String name) {
		this.name = name;
	}

	@Override
	public String getName() {
		return name;
	}
	
	protected void addHandler(byte cmd, TunnelCmd handler) {
		handlers.put(Byte.valueOf(cmd), handler);
	}

	protected TunnelCmd getHandler(byte cmd) {
		TunnelCmd handler = handlers.get(Byte.valueOf(cmd));
		if (handler == null) {
			throw new SocksException("Invalid tunnel command " + cmd + " for state " + name);
		}
		return handler;
	}

	@Override
	public void process(T handler) throws IOException {
		while (true) {
			if (reader.read(handler)) {
				try {
					if (processPacket(handler, reader) == WR.AGAIN) {
						if (log.isDebugEnabled()) {
							log.debug("unable to write once, try at next poll");
						}
						break;
					}
				} finally {
					reader.reset();
				}
			} else {
				return;
			}
		}
	}

	protected WR processPacket(T handler, TunnelPacketReader reader) {
		Tunnel t = (Tunnel) handler;
		TunneledConnection tunneled = t.getTunneledConnection(reader.getChannelId());
		try {
			return getHandler(reader.getCmd()).execute(t, reader, (o) -> {
			}, reader.getChannelId());
		} catch (Throwable e) {
			log.debug("Error handling tunnel command " + reader.getCmd(), e);
			if (tunneled != null) {
				tunneled.close();
			}
			return WR.DONE;
		}

	}

}
