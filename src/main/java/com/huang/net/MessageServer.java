package com.huang.net;

import com.lhstack.tools.plugins.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * @Author lei.huang
 * @Description TODO
 **/
public class MessageServer {

	private static MessageServer messageServer;

	private final AtomicBoolean running = new AtomicBoolean(false);

	private Function<Integer, Boolean> callback;

	private Selector selector;

	private Map<String, Logger> loggers;

	public void setCallback(Function<Integer, Boolean> callback) {
		this.callback = callback;
	}

	public void setLoggers(Map<String, Logger> loggers) {
		this.loggers = loggers;
	}

	public static MessageServer getServer() {
		if (messageServer == null) {
			synchronized (MessageServer.class) {
				if (messageServer == null) {
					messageServer = new MessageServer();
				}
			}
		}
		return messageServer;
	}

	public void start() {
		Thread thread = new Thread(() -> {
			if (!running.compareAndSet(false, true)) {
				return;
			}
			try (DatagramChannel datagramChannel = DatagramChannel.open();
			     Selector selector = Selector.open()) {
				this.selector = selector;
				SocketAddress address = new InetSocketAddress("127.0.0.1", 9527);
				datagramChannel.bind(address);
				datagramChannel.configureBlocking(false);
				datagramChannel.register(selector, SelectionKey.OP_READ, null);
				while (running.get()) {
					selector.select();
					Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
					while (iterator.hasNext()) {
						SelectionKey selectionKey = iterator.next();
						if (selectionKey.interestOps() == SelectionKey.OP_READ) {
							DatagramChannel client = (DatagramChannel) selectionKey.channel();
							ByteBuffer allocate = ByteBuffer.allocate(36);
							client.receive(allocate);
							allocate.flip();
							if (callback != null && callback.apply(allocate.getInt())) {
								stop();
							}
						}
						iterator.remove();
					}
				}
			} catch (IOException e) {
				log("错误: " + e.getMessage());
				throw new RuntimeException(e);
			} finally {
				log("通信已关闭");
			}
		});
		thread.setDaemon(true);
		thread.start();
		Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
	}

	public void stop() {
		if (running.compareAndSet(true, false) && selector != null) {
			selector.wakeup();
		}
	}

	private void log(String msg) {
		if (loggers == null) {
			return;
		}
		for (Map.Entry<String, Logger> entry : loggers.entrySet()) {
			entry.getValue().error(msg);
		}
	}

}
