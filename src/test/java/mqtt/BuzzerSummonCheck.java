package mqtt;

import java.nio.charset.StandardCharsets;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;

/**
 * Manual hardware check: listen to the referee buzzers while it runs.
 *
 * Requires an MQTT broker on 127.0.0.1:1883 (owlcms running) and the firmata application connected to a referee
 * device on the platform. Not named *Test so that it is not run by regular builds.
 *
 * System properties: platform (default A), mqttUsername, mqttPassword.
 */
public class BuzzerSummonCheck {
	static Logger logger = (Logger) LoggerFactory.getLogger(BuzzerSummonCheck.class);

	/** the Referees.xlsx summon tune lasts 1250ms */
	private static final long TUNE_GAP_MS = 2500;

	@Test
	public void summonEachRefereeThenAll() throws MqttException, InterruptedException {
		String platform = System.getProperty("platform", "A");
		String topic = "owlcms/fop/summon/" + platform;

		MqttClient client = MQTTRoundTrip.createMQTTClient(platform);
		MqttConnectOptions connOpts = new MqttConnectOptions();
		connOpts.setCleanSession(true);
		String username = System.getProperty("mqttUsername");
		String password = System.getProperty("mqttPassword");
		if (username != null && !username.isBlank()) {
			connOpts.setUserName(username);
		}
		if (password != null && !password.isBlank()) {
			connOpts.setPassword(password.toCharArray());
		}
		client.connect(connOpts);
		try {
			for (int ref = 1; ref <= 3; ref++) {
				logger.info("summoning referee {} alone", ref);
				summon(client, topic, ref);
				Thread.sleep(TUNE_GAP_MS);
			}
			// same sequence as owlcms when the jury summons all the referees
			logger.info("summoning all referees");
			for (int ref = 1; ref <= 3; ref++) {
				summon(client, topic, ref);
			}
			Thread.sleep(TUNE_GAP_MS);
		} finally {
			client.disconnect();
			client.close();
		}
	}

	private void summon(MqttClient client, String topic, int ref) throws MqttException {
		client.publish(topic, new MqttMessage(Integer.toString(ref).getBytes(StandardCharsets.UTF_8)));
	}
}
