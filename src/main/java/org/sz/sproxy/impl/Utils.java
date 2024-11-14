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
import java.util.function.BiFunction;

import org.sz.sproxy.Context;
import org.sz.sproxy.Readable;
import org.sz.sproxy.SocksException;
import org.sz.sproxy.Writable;
import org.sz.sproxy.Writable.WR;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Sam Zheng
 *
 */
@Slf4j
public final class Utils {
	
	public static final int PAGE_SIZE = 1 << 12;
	
	private Utils() {
		
	}
	
	public static void sanitizePacketSize(int size) {
		if (!isPlausiblePacketSize(size)) {
			throw new SocksException("Invalid size: " + size);
		}
	}
	
	public static boolean isPlausiblePacketSize(int size) {
		return size <= 2 * PAGE_SIZE;
	}
	
	public static final BiFunction<String, Runnable, Runnable> EXEC_WITH_TH_NAME = (name, r) -> {
		return () -> {
			String n = Thread.currentThread().getName();
			try {
				Thread.currentThread().setName(name);
				r.run();
			} finally {
				Thread.currentThread().setName(n);
			}
		};
	};
	
	public static void pump(Context context, Readable source, Writable to, Runnable onClose) throws IOException {
		int bufSize = context.getConfiguration().getPacketBufferSize();
		ByteBuffer b = ByteBuffer.allocate(bufSize);
		while (true) {
			int n = source.read(b);
			if (n == -1) {
				log.debug("connection closed: {}", source);
				if (onClose != null) {
					onClose.run();
				}
				return;
			}
			b.flip();
			if (b.remaining() > 0) {
				if (to.write(b) == WR.AGAIN) {
					return;
				}
				b.clear();
			} else {
				return;
			}
		}
	}
	
}
