package mqtt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import app.owlcms.firmata.data.MQTTConfig;
import app.owlcms.firmata.mqtt.ConfigMQTTMonitor;
import io.moquette.broker.Server;
import io.moquette.broker.config.IConfig;
import io.moquette.broker.config.MemoryConfig;
import io.moquette.broker.security.IAuthorizatorPolicy;
import io.moquette.broker.subscriptions.Topic;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttQoS;

/**
 * The monitors connect with a clean session, so every connection starts without subscriptions. These tests use a real
 * broker, and a proxy that can cut the connection while the broker stays up, which is the case where the first
 * reconnection attempt succeeds.
 */
public class MonitorSubscriptionTest {

	/** Refuses every subscription, and counts how many were requested. */
	public static class RefuseSubscriptions implements IAuthorizatorPolicy {
		static final AtomicInteger requests = new AtomicInteger();

		@Override
		public boolean canWrite(Topic topic, String user, String client) {
			return true;
		}

		@Override
		public boolean canRead(Topic topic, String user, String client) {
			requests.incrementAndGet();
			return false;
		}
	}

	private static final long TIMEOUT_MS = 15_000;

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private Server broker;
	private ServerSocket proxy;
	private final List<Socket> proxied = new CopyOnWriteArrayList<>();
	private ConfigMQTTMonitor monitor;

	@Before
	public void resetState() {
		MQTTConfig.getCurrent().setFops(new ArrayList<>());
		RefuseSubscriptions.requests.set(0);
	}

	@After
	public void tearDown() throws IOException {
		if (monitor != null) {
			monitor.close();
		}
		if (proxy != null) {
			proxy.close();
		}
		closeProxiedConnections();
		if (broker != null) {
			broker.stopServer();
		}
	}

	@Test
	public void subscribesAgainWhenFirstReconnectionSucceeds() throws Exception {
		startBroker(null);
		startMonitor();
		assertTrue("initial subscription", deliveredBeforeTimeout("first"));

		closeProxiedConnections();

		assertTrue("subscription after the connection was cut", deliveredBeforeTimeout("second"));
	}

	@Test
	public void refusedSubscriptionIsNotRetried() throws Exception {
		startBroker(RefuseSubscriptions.class.getName());
		startMonitor();
		waitFor(() -> RefuseSubscriptions.requests.get() > 0);

		assertFalse(deliveredBeforeTimeout("refused", 2_000));
		assertEquals(1, RefuseSubscriptions.requests.get());
	}

	private void startBroker(String authorizator) throws Exception {
		IConfig config = new MemoryConfig(new Properties());
		config.setProperty(IConfig.ALLOW_ANONYMOUS_PROPERTY_NAME, "true");
		config.setProperty(IConfig.HOST_PROPERTY_NAME, "127.0.0.1");
		config.setProperty(IConfig.PORT_PROPERTY_NAME, Integer.toString(freePort()));
		config.setProperty(IConfig.PERSISTENCE_ENABLED_PROPERTY_NAME, "false");
		config.setProperty(IConfig.DATA_PATH_PROPERTY_NAME, folder.newFolder("mqttData").getAbsolutePath());
		if (authorizator != null) {
			config.setProperty(IConfig.AUTHORIZATOR_CLASS_NAME, authorizator);
		}
		broker = new Server();
		broker.startServer(config);
		startProxy(Integer.parseInt(config.getProperty(IConfig.PORT_PROPERTY_NAME)));
	}

	/** Forwards connections to the broker, so that the test can cut them without stopping the broker. */
	private void startProxy(int brokerPort) throws IOException {
		proxy = new ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress());
		Thread acceptor = new Thread(() -> {
			try {
				while (true) {
					Socket client = proxy.accept();
					Socket upstream = new Socket("127.0.0.1", brokerPort);
					proxied.add(client);
					proxied.add(upstream);
					forward(client, upstream);
					forward(upstream, client);
				}
			} catch (IOException e) {
				// the proxy was closed
			}
		}, "test-proxy");
		acceptor.setDaemon(true);
		acceptor.start();
	}

	private static void forward(Socket from, Socket to) {
		Thread t = new Thread(() -> {
			try {
				from.getInputStream().transferTo(to.getOutputStream());
			} catch (IOException e) {
				// one side was closed
			}
		}, "test-proxy-forward");
		t.setDaemon(true);
		t.start();
	}

	private void closeProxiedConnections() {
		for (Socket s : proxied) {
			try {
				s.close();
			} catch (IOException e) {
				// already closed
			}
		}
		proxied.clear();
	}

	private void startMonitor() throws Exception {
		MQTTConfig.getCurrent().setMqttServer("127.0.0.1");
		MQTTConfig.getCurrent().setMqttPort(Integer.toString(proxy.getLocalPort()));
		monitor = new ConfigMQTTMonitor();
		monitor.start("config");
	}

	private boolean deliveredBeforeTimeout(String platform) throws Exception {
		return deliveredBeforeTimeout(platform, TIMEOUT_MS);
	}

	/** Publishes repeatedly, since the monitor may still be connecting or subscribing. */
	private boolean deliveredBeforeTimeout(String platform, long timeoutMs) throws Exception {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			var message = MqttMessageBuilders.publish().topicName("owlcms/fop/config/test").retained(false)
					.qos(MqttQoS.AT_MOST_ONCE)
					.payload(Unpooled.copiedBuffer("{\"platforms\":[\"" + platform + "\"]}", StandardCharsets.UTF_8))
					.build();
			broker.internalPublish(message, "test");
			Thread.sleep(250);
			List<String> fops = MQTTConfig.getCurrent().getFops();
			if (fops != null && fops.contains(platform)) {
				return true;
			}
		}
		return false;
	}

	private static void waitFor(BooleanSupplier condition) throws InterruptedException {
		long deadline = System.currentTimeMillis() + TIMEOUT_MS;
		while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
			Thread.sleep(50);
		}
	}

	private static int freePort() throws IOException {
		try (ServerSocket s = new ServerSocket(0)) {
			return s.getLocalPort();
		}
	}
}
