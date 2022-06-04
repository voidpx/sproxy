package org.sz.sproxy.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sz.sproxy.start.Launcher;

public class TestProxy {
	
	static int sp;
	
	static int cp;
	
	static int serverPort;
	
	private static int getAvailablePort() throws IOException {
		ServerSocket ss = new ServerSocket(0);
		int port = ss.getLocalPort();
		ss.close();
		return port;
	}
	
	private static void startTestServer() throws IOException {
		serverPort = getAvailablePort();
		ServerSocket ss = new ServerSocket(serverPort);
		Thread t = new Thread(() -> {
			while (true) {
				try {
					Socket s = ss.accept();
					BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream()));
					String l = r.readLine() + "\n";
					s.getOutputStream().write(l.getBytes(StandardCharsets.UTF_8));
					s.close();
					ss.close();
					break;
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		});
		t.start();
	}
	
	@BeforeAll
	public static void setup() throws Exception {
		startTestServer();
		// generate keys
		sp = getAvailablePort();
		cp = getAvailablePort();
		Launcher.main(new String[] {"genKey"});
		new Thread(() -> {
			Launcher.main(new String[] {"server", "-p", String.valueOf(sp)});
		}).start();
		new Thread(() -> {
			Launcher.main(new String[] {"client", "-h", "localhost", "-p", String.valueOf(cp), "-H", "localhost", "-P", String.valueOf(sp)});
		}).start();
		
		Thread.sleep(2000); // wait a bit while servers are starting.
	}

	@Test
	public void testProxy() throws IOException {
		Proxy proxy  = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("localhost", cp));
		Socket s = new Socket(proxy);
		s.connect(new InetSocketAddress("localhost", serverPort));
		String greeting = "hello";
		s.getOutputStream().write((greeting + "\n").getBytes(StandardCharsets.UTF_8));
		BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream()));
		String l = r.readLine();
		Assertions.assertEquals(greeting, l);
		s.close();
	}
	
}
