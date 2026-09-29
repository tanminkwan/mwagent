package mwagent.lifecycle;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import mwagent.common.Config;
import mwagent.mqtt.MqttService;

/**
 * 명령 폴링에 실리는 X-Mqtt-Status 헤더 게이팅 테스트.
 * mqtt_enabled=true 일 때만 보내고, false 면 헤더 자체가 없어야 한다.
 */
class AgentLifecycleManagerMqttStatusTest {

    private boolean savedEnabled;
    private String savedBroker;

    @BeforeEach
    void setUp() {
        Config config = Config.getConfig();
        config.setLogger(Logger.getLogger("AgentLifecycleManagerMqttStatusTest"));
        savedEnabled = config.isMqtt_enabled();
        savedBroker = config.getMqtt_broker_address();
    }

    @AfterEach
    void tearDown() {
        Config config = Config.getConfig();
        config.setMqtt_enabled(savedEnabled);
        config.setMqtt_broker_address(savedBroker);
    }

    private AgentLifecycleManager newManager(MqttService mqttService) {
        return new AgentLifecycleManager(null, null, null, mqttService);
    }

    @Test
    void mqttStatusHeaders_WhenDisabled_ShouldBeNull() {
        Config.getConfig().setMqtt_enabled(false);

        assertThat(newManager(new MqttService()).mqttStatusHeaders()).isNull();
    }

    @Test
    void mqttStatusHeaders_WhenEnabled_ShouldCarryStatus() {
        Config.getConfig().setMqtt_enabled(true);
        MqttService service = new MqttService();
        service.setBrokerAddress("tcp://127.0.0.1:1");

        Map<String, String> headers = newManager(service).mqttStatusHeaders();

        assertThat(headers).containsOnlyKeys(AgentLifecycleManager.MQTT_STATUS_HEADER);
        assertThat(headers.get("X-Mqtt-Status")).startsWith("not_started;");
    }
}
