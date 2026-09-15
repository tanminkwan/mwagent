package mwagent.mqtt;

import static org.assertj.core.api.Assertions.*;

import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import mwagent.common.Config;
import mwagent.lifecycle.LifecycleState;

/**
 * MqttService 테스트
 *
 * MQTT 는 기본 비활성이고 설정 소스가 agent.properties 하나다. 그 게이팅이
 * 흐트러지면 의도하지 않게 브로커에 붙거나, 반대로 켜도 안 뜨게 된다.
 */
class MqttServiceTest {

    private boolean savedEnabled;
    private String savedBroker;
    private String savedAgentId;

    @BeforeEach
    void setUp() {
        Config config = Config.getConfig();
        config.setLogger(Logger.getLogger("MqttServiceTest"));

        // Config 는 싱글턴이므로 테스트 간 오염을 막기 위해 보존/복원한다
        savedEnabled = config.isMqtt_enabled();
        savedBroker = config.getMqtt_broker_address();
        savedAgentId = config.getAgent_id();
    }

    @AfterEach
    void tearDown() {
        Config config = Config.getConfig();
        config.setMqtt_enabled(savedEnabled);
        config.setMqtt_broker_address(savedBroker);
        config.setAgent_id(savedAgentId);
    }

    @Test
    void isConfigured_WhenDisabled_ShouldBeFalseEvenWithBrokerAddress() {
        Config.getConfig().setMqtt_enabled(false);
        MqttService service = new MqttService();
        service.setBrokerAddress("tcp://localhost:1883");

        // 로컬 설정이 우선이다. 주소가 있어도 켜지 않았으면 기동하지 않는다
        assertThat(service.isConfigured()).isFalse();
    }

    @Test
    void isConfigured_WhenEnabledWithoutBroker_ShouldBeFalse() {
        Config.getConfig().setMqtt_enabled(true);

        assertThat(new MqttService().isConfigured()).isFalse();
    }

    @Test
    void isConfigured_WhenEnabledWithBroker_ShouldBeTrue() {
        Config.getConfig().setMqtt_enabled(true);
        MqttService service = new MqttService();
        service.setBrokerAddress("tcp://localhost:1883");

        assertThat(service.isConfigured()).isTrue();
    }

    @Test
    void setBrokerAddress_ShouldPropagateToConfig() {
        MqttService service = new MqttService();

        service.setBrokerAddress("tcp://broker.example.com:1883");

        assertThat(Config.getConfig().getMqtt_broker_address())
                .isEqualTo("tcp://broker.example.com:1883");
    }

    @Test
    void newService_ShouldStartInCreatedState() {
        assertThat(new MqttService().getState()).isEqualTo(LifecycleState.CREATED);
    }

    @Test
    void start_WhenDisabled_ShouldReachRunningWithoutSubscriber() throws Exception {
        Config.getConfig().setMqtt_enabled(false);
        MqttService service = new MqttService();

        service.start();

        // Paho 클라이언트도, 감시 스레드도 만들지 않는다
        assertThat(service.getState()).isEqualTo(LifecycleState.RUNNING);
        assertThat(service.getSubscriber()).isNull();
    }

    @Test
    void start_WhenBrokerUnreachable_ShouldStillRunAndKeepRetrying() throws Exception {
        // 브로커가 죽은 상태에서 기동되는 상황. 예전에는 여기서 포기하고
        // 감시 스레드조차 띄우지 않아 브로커가 살아나도 복구되지 않았다.
        Config.getConfig().setMqtt_enabled(true);
        Config.getConfig().setAgent_id("test-agent");

        MqttService service = new MqttService();
        service.setBrokerAddress("tcp://127.0.0.1:1");   // 접속 불가 포트

        try {
            service.start();

            assertThat(service.getState()).isEqualTo(LifecycleState.RUNNING);
            assertThat(service.getSubscriber()).isNotNull();
            assertThat(service.getSubscriber().isConnected()).isFalse();
        } finally {
            service.stop();
        }
    }

    @Test
    void stop_AfterDisabledStart_ShouldReachStopped() throws Exception {
        Config.getConfig().setMqtt_enabled(false);
        MqttService service = new MqttService();
        service.start();

        service.stop();

        assertThat(service.getState()).isEqualTo(LifecycleState.STOPPED);
    }
}
