package mwagent.mqtt;

import static mwagent.common.Config.getConfig;

import java.util.logging.Level;
import java.util.logging.Logger;

import mwagent.lifecycle.AgentLifecycle;
import mwagent.lifecycle.LifecycleState;

/**
 * MQTT 통신을 관리하는 서비스.
 *
 * KafkaService 와 병행 동작한다. 둘 다 설정되어 있으면 두 경로 모두로
 * 명령을 수신하며, 어느 쪽으로 들어오든 동일하게 command_class 규약에 따라
 * mwagent.order.* 로 위임된다.
 *
 * Kafka 와 달리 구독 스레드를 두지 않는다. Paho 가 자체 네트워크 스레드로
 * 콜백을 돌리므로, 여기서는 연결 수명주기와 안정성 감시만 담당한다.
 *
 * MQTT 는 실시간 command 수령만 담당한다. 결과 전송·토큰 갱신·명령 폴링은
 * 계속 REST API 가 담당하므로 이 서비스는 발행 기능을 일절 갖지 않는다.
 */
public class MqttService implements AgentLifecycle {

    /** 연결 상태 확인 주기. 이 주기로 isConnected() 를 직접 본다. */
    private static final long WATCH_INTERVAL_MS = 60_000L;

    private final Logger logger;
    private LifecycleState state;

    private String brokerAddress;
    private MwMqttSubscriber subscriber;
    private Thread watchThread;
    private volatile boolean watching;

    public MqttService() {
        this.logger = getConfig().getLogger();
        this.state = LifecycleState.CREATED;
    }

    /**
     * MQTT 브로커 주소 설정. start() 호출 전에 설정되어야 한다.
     * agent.properties 의 mqtt_broker_address 가 유일한 주입 경로다.
     */
    public void setBrokerAddress(String brokerAddress) {
        if (state != LifecycleState.CREATED) {
            throw new IllegalStateException("Cannot set broker address after service has started");
        }
        this.brokerAddress = brokerAddress;
        getConfig().setMqtt_broker_address(brokerAddress);
    }

    /**
     * MQTT 비활성 사유를 로그용으로 반환한다. 활성 상태면 null.
     */
    private String disabledReason() {
        if (!getConfig().isMqtt_enabled()) {
            return "mqtt_enabled=false";
        }
        if (brokerAddress == null || brokerAddress.isEmpty()) {
            return "mqtt_broker_address not set";
        }
        return null;
    }

    @Override
    public void start() throws Exception {
        if (!state.canTransitionTo(LifecycleState.STARTING)) {
            throw new IllegalStateException("Cannot start from state: " + state);
        }

        String disabled = disabledReason();
        if (disabled != null) {
            // 구독자 스레드도, Paho 클라이언트도 만들지 않는다
            logger.info("MQTT subscriber not started (" + disabled + ").");
            state = LifecycleState.RUNNING;
            return;
        }

        logger.info("Starting MQTT service with broker: " + brokerAddress);
        state = LifecycleState.STARTING;

        try {
            // clientId == username == agent_id. ACL 의 %u 치환이 그대로 성립한다 (§2.1)
            String agentId = getConfig().getAgent_id();

            subscriber = new MwMqttSubscriber(
                    brokerAddress, agentId, getConfig().getMqtt_credential());

            // 최초 접속 실패는 치명적이지 않다. 브로커가 죽은 상태에서 에이전트가
            // 기동될 수 있으므로, 실패해도 감시 스레드를 띄워 60초마다 재시도한다.
            // (Paho 의 automaticReconnect 는 최초 접속 성공 이후부터만 동작한다)
            try {
                subscriber.connect();
            } catch (Exception e) {
                logger.warning("MQTT initial connect failed (" + e.getMessage()
                        + "). Retrying every " + (WATCH_INTERVAL_MS / 1000L) + "s.");
            }

            watching = true;
            watchThread = new Thread(this::watchLoop, "MwMqttWatch");
            watchThread.setDaemon(true);
            watchThread.start();

            state = LifecycleState.RUNNING;
            logger.info("MQTT service started successfully");

        } catch (Exception e) {
            state = LifecycleState.FAILED;
            logger.log(Level.SEVERE, "Failed to start MQTT service", e);
            throw e;

        } catch (LinkageError e) {
            // paho jar 이 lib/ 에 없거나 버전이 맞지 않으면 MwMqttSubscriber 로딩 시점에
            // NoClassDefFoundError 가 난다. Error 는 Exception 이 아니어서 호출자의
            // catch(Exception) 을 모두 통과해 main 을 그대로 죽이고, 로거가 한 번도
            // 호출되지 않아 로그 파일에 아무 단서도 남지 않는다. MQTT 는 선택적
            // 경로이므로 여기서 Exception 으로 감싸 기동이 계속되게 한다.
            state = LifecycleState.FAILED;
            logger.log(Level.SEVERE,
                    "Failed to start MQTT service (MQTT library missing or incompatible)", e);
            throw new Exception("MQTT service start failed: " + e, e);
        }
    }

    /**
     * 60초마다 연결 상태를 확인한다.
     *
     * Paho 는 재접속 실패 시 사용자 콜백을 호출하지 않으므로, 끊김이 지속되는
     * 동안의 보고는 콜백이 아니라 이 루프가 담당해야 한다 (checkConnection 참조).
     */
    private void watchLoop() {
        while (watching) {
            try {
                Thread.sleep(WATCH_INTERVAL_MS);
                if (subscriber != null) {
                    subscriber.checkConnection();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                logger.log(Level.WARNING, "MQTT watch loop error", e);
            }
        }
    }

    @Override
    public void stop() throws Exception {
        if (!state.canTransitionTo(LifecycleState.STOPPING)) {
            logger.warning("Cannot stop from state: " + state);
            return;
        }

        logger.info("Stopping MQTT service...");
        state = LifecycleState.STOPPING;

        try {
            watching = false;
            if (watchThread != null && watchThread.isAlive()) {
                watchThread.interrupt();
                watchThread.join(5000);
            }

            if (subscriber != null) {
                subscriber.disconnect();
            }

            state = LifecycleState.STOPPED;
            logger.info("MQTT service stopped successfully");

        } catch (Exception e) {
            state = LifecycleState.FAILED;
            logger.log(Level.SEVERE, "Error stopping MQTT service", e);
            throw e;
        }
    }

    @Override
    public LifecycleState getState() {
        return state;
    }

    /**
     * MQTT 를 기동할지 여부.
     * mqtt_enabled=false 면 브로커 주소가 있어도 false 다 (로컬 설정이 우선).
     */
    public boolean isConfigured() {
        return disabledReason() == null;
    }

    /** 구독자 반환 (테스트용) */
    MwMqttSubscriber getSubscriber() {
        return subscriber;
    }
}
