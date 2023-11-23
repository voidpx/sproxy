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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.sz.sproxy.Acceptor;
import org.sz.sproxy.BlackListAware;
import org.sz.sproxy.ChannelHandler;
import org.sz.sproxy.Configuration;
import org.sz.sproxy.Context;
import org.sz.sproxy.Server;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Sam Zheng
 *
 */
@Slf4j
public class AcceptorImpl implements Acceptor, NioChannelHandler<ServerSocketChannel>, Runnable, BlackListAware {

	private static final long BL_TIMEOUT = 2 * 60 * 1000;

	@Getter
	Server server;

	@Getter
	Context context;

	@Getter
	ServerSocketChannel channel;

	Selector selector;

	SelectionKey selectionKey;

	Map<InetAddress, Long> blackList = new ConcurrentHashMap<>();

	AcceptorImpl(Server server) {
		this.server = server;
		this.context = server.getContext();
		if (context instanceof ContextImpl) {
			((ContextImpl)context).setBlackListAware(this);
		}
	}

	@Override
	public void startAccepting() throws IOException {
		StandardProtocolFamily f = "true".equals(context.getConfiguration().get(Configuration.SERVER_IPV6))
				? StandardProtocolFamily.INET6
				: StandardProtocolFamily.INET;
		channel = ServerSocketChannel.open(f);
		channel.configureBlocking(false);

		channel.bind(new InetSocketAddress(server.getHost(), server.getPort()));
		selector = context.getSelector();
		selectionKey = channel.register(selector, SelectionKey.OP_ACCEPT, this);
		Thread select = new Thread(this);
		select.setName("selector");
		select.start();

		Thread blackListWatcher = new Thread(() -> {
			while (true) {
				long t = System.currentTimeMillis();
				blackList.entrySet().stream().filter(e -> t - e.getValue() > BL_TIMEOUT).map(Entry::getKey).toList()
						.forEach(k -> blackList.remove(k));
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e1) {
					Thread.currentThread().interrupt();
					log.debug("blacklist watcher interrupted", e1);
				}
			}
		});
		blackListWatcher.setDaemon(true);
		blackListWatcher.setName("blacklist_watcher");
		blackListWatcher.start();
	}

	private void processSelection(SelectionKey key) {
		NioChannelHandler<?> handler = (NioChannelHandler<?>) key.attachment();
		if (handler == null) {
			log.debug("No handler, possibly the channel has been switched");
		}
		try {
			if (!key.isValid()) {
				log.debug("invalid selection key {}, skip", key);
				return;
			}
			int ops = key.readyOps();
			key.interestOpsAnd(~ops); // reset interest until the handler finishes handling and set it again
			handler.getExecutor().execute(Utils.EXEC_WITH_TH_NAME.apply(handler.getChannel().toString(), () -> {
				try {
					int expectedNext = handler.handle(ops);
					if (expectedNext == -1) {
						// closing
						log.debug("closing in progress");
					}
				} catch (Throwable e) {
					log.debug("Error handling channel, close", e);
					handler.close();
				}
			}));
		} catch (Throwable e) {
			Optional.ofNullable(handler).ifPresent(t -> t.close());
			log.debug("Error processing selection key: {}", key);
		}

	}

	@Override
	public void run() {
		try {
			while (true) {
				selector.select();
				Iterator<SelectionKey> it = selector.selectedKeys().iterator();
				while (it.hasNext()) {
					SelectionKey k = it.next();
					it.remove();
					processSelection(k);

				}
			}
		} catch (IOException e) {
			log.error("Error", e);
		}
	}

	@Override
	public int handle(int ops) throws IOException {
		if (selectionKey.isAcceptable()) {
			SocketChannel sc;
			while ((sc = channel.accept()) != null) {
				InetAddress addr = ((InetSocketAddress)sc.getRemoteAddress()).getAddress();
				if (blackList.containsKey(addr)) {
					sc.close();
					log.debug("address {} on blacklist, refused", addr);
					continue;
				}
				ChannelHandler<SocketChannel> conn = context.getChannelHandlerFactory().createHandler(context, sc);
				log.debug("connection created: {}", conn.getChannel());
			}
		} else {
			log.error("Acceptor not acceptable, bug!");
		}
		selectionKey.interestOps(SelectionKey.OP_ACCEPT);
		selector.wakeup();
		return ops;
	}

	@Override
	public void close() {
		try {
			channel.close();
			selector.close();
			log.info("closed");
		} catch (IOException e) {
			log.error("Error closing acceptor", e);
		}
	}

	@Override
	public void addBlackList(InetAddress address) {
		if (!blackList.containsKey(address)) {
			blackList.put(address, System.currentTimeMillis());
		}
	}

}
