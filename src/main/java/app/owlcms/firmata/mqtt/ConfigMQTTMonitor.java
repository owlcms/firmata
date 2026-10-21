package app.owlcms.firmata.mqtt;

import java.util.UUID;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.slf4j.LoggerFactory;

import app.owlcms.firmata.data.MQTTConfig;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

/**
 * Deal with configuration events that concern all platforms
 *
 * @author Jean-François Lamy
 */
public class ConfigMQTTMonitor extends AbstractMQTTMonitor {

	static Logger logger = (Logger) LoggerFactory.getLogger(ConfigMQTTMonitor.class);
	private static final String OWLCMS_CONFIG = "owlcms/fop/config/#";
	private static final String DEVICE_IDENTIFIER = "config_" + UUID.randomUUID().toString().replace("-", "");

	public ConfigMQTTMonitor() {
		logger.setLevel(Level.DEBUG);
		this.setName("config");
		this.setSubscription(OWLCMS_CONFIG);
		MQTTConfig.getCurrent().setConfigMqttMonitor(this);
	}

	@Override
	public MqttConnectOptions setupMQTTClient(String userName, String password) {
		MqttConnectOptions connOpts = setUpConnectionOptions(userName != null ? userName : "",
				password != null ? password : "");
		client.setCallback(new ConfigMQTTCallback(this));
		return connOpts;
	}

	@Override
	protected String getDeviceIdentifier() {
		return DEVICE_IDENTIFIER;
	}

	@Override
	protected String getDeviceInfo() {
		return "Server Config";
	}

}
