package mwagent.mqtt;

import static mwagent.common.Config.getConfig;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.persist.MqttDefaultFilePersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import mwagent.OrderCallerThread;

/**
 * MQTT 명령 구독자.
 *
 * Kafka 의 MwConsumerThread 와 동일한 biz 동작을 수행한다.
 *   - 메시지 수신 → command_class 추출 → OrderCallerThread 로 위임
 *   - 결과 전송은 Order.sendResult() 가 담당한다 (resultReceiver 설정에 따름)
 *
 * 구조는 MQTT 방식을 따른다. poll 루프가 없으므로 Thread 를 상속하지 않고
 * Paho 의 콜백으로 동작한다.
 *
 * 명령 만료(1시간)는 브로커가 Message Expiry Interval 로 처리하므로
 * Agent 측 만료 검사는 두지 않는다.
 *
 * 구독 전용이다. 발행은 일절 하지 않으므로 브로커 ACL 에는
 * cmd/{agent_id}/req 와 cmd/broadcast/req 구독 권한만 있으면 된다.
 */
public class MwMqttSubscriber {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    /** 연결이 이만큼 유지되어야 "복구"로 인정한다. 플래핑 로그 폭증 방지. */
    private static final long STABLE_MS = 60_000L;
    /** 불안정 지속 중 보고 주기. 감시 스레드가 이 간격으로 1줄씩 남긴다. */
    private static final long REPORT_MS = 3_600_000L;
    /** cmdId 멱등성 캐시 크기. QoS1 은 at-least-once 이므로 중복 수신이 정상이다. */
    private static final int SEEN_CACHE_SIZE = 1000;
    /** 전 에이전트 공통 명령 토픽. agent 별 토픽과 함께 구독한다. */
    private static final String BROADCAST_TOPIC = "cmd/broadcast/req";
    /** SUBACK reason code 가 이 값 이상이면 구독 실패다 (MQTT v5 §3.9.3). */
    private static final int SUBACK_FAILURE_MIN = 0x80;

    private final Logger logger = getConfig().getLogger();

    private final String brokerUri;
    private final String agentId;
    private final String credential;

    private MqttClient client;

    /** 최근 처리한 cmdId. 중복 실행 차단용 LRU. */
    private final Set<String> seen = Collections.newSetFromMap(
            new LinkedHashMap<String, Boolean>(16, 0.75f, true) {
                private static final long serialVersionUID = 1L;
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > SEEN_CACHE_SIZE;
                }
            });

    // --- 연결 상태 로그 억제 (시간 기준) ---
    private volatile long connectedAt = 0L;
    private volatile long unstableSince = 0L;
    private volatile long lastReport = 0L;
    private volatile String lastReason = "unknown";
    /** 최초 접속이 한 번이라도 성공했는지. false 면 Paho 자동 재접속이 무장되지 않은 상태다. */
    private volatile boolean everConnected = false;
    private final AtomicInteger events = new AtomicInteger();

    public MwMqttSubscriber(String brokerAddress, String agentId, String credential) {
        this.brokerUri = normalizeUri(brokerAddress);
        this.agentId = agentId;
        this.credential = credential;
    }

    /** Kafka 설정이 host:port 형태이므로 스킴이 없으면 tcp:// 를 붙인다. */
    static String normalizeUri(String address) {
        if (address == null) {
            return null;
        }
        String a = address.trim();
        if (a.isEmpty() || a.contains("://")) {
            return a;
        }
        return "tcp://" + a;
    }

    /**
     * 브로커에 접속한다. 실패해도 client 인스턴스는 남으므로 재호출로 재시도할 수 있다.
     *
     * MqttService 는 최초 실패를 치명적으로 보지 않는다. 브로커가 죽은 상태에서
     * 에이전트가 기동될 수 있고, 그 경우 Paho 의 automaticReconnect 는 무장되지
     * 않으므로(최초 접속 성공 이후부터 동작) checkConnection() 이 직접 재시도한다.
     */
    public void connect() throws MqttException {
        try {
            if (client == null) {
                createClient();
            }
            client.connect(buildOptions());
        } catch (MqttException e) {
            // 호출자가 MqttService 든 checkConnection() 이든, 사유는 여기서 기록한다.
            // 그래야 최초 접속 실패도 첫 보고 줄에 사유가 실린다.
            lastReason = describe(e);
            throw e;
        }
        logger.info("MQTT connected. broker=" + brokerUri + " clientId=" + agentId);
    }

    /** MqttClient 와 콜백은 한 번만 만든다. 재시도 때 재사용된다. */
    private void createClient() throws MqttException {
        // QoS1 in-flight 메시지 보존용. Config 에 log_dir getter 가 없으므로
        // 프로세스 작업 디렉터리 하위를 쓴다 (로그 기본 위치와 동일한 규칙).
        File persistDir = new File(System.getProperty("user.dir"), ".mqtt-persist");
        if (!persistDir.exists() && !persistDir.mkdirs()) {
            logger.warning("Cannot create MQTT persistence dir: " + persistDir.getAbsolutePath());
        }
        MqttDefaultFilePersistence persistence =
                new MqttDefaultFilePersistence(persistDir.getAbsolutePath());

        client = new MqttClient(brokerUri, agentId, persistence);

        client.setCallback(new MqttCallback() {
            @Override
            public void connectComplete(boolean reconnect, String serverUri) {
                connectedAt = System.currentTimeMillis();
                everConnected = true;
                subscribeAll(reconnect);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                dispatch(topic, message);
            }

            @Override
            public void disconnected(MqttDisconnectResponse response) {
                recordUnstable(describe(response));
            }

            @Override
            public void mqttErrorOccurred(MqttException exception) {
                recordUnstable(describe(exception));
            }

            @Override
            public void deliveryComplete(IMqttToken token) {
                // 구독 전용이므로 발행이 없다. 호출되지 않는다.
            }

            @Override
            public void authPacketArrived(int reasonCode, MqttProperties properties) {
                // 확장 인증 미사용
            }
        });
    }

    private MqttConnectionOptions buildOptions() {
        MqttConnectionOptions opts = new MqttConnectionOptions();
        opts.setCleanStart(false);                    // 오프라인 큐잉 수신
        opts.setSessionExpiryInterval(86400L);        // 명령 만료(3600s) 보다 길게
        opts.setKeepAliveInterval(30);
        opts.setAutomaticReconnect(true);
        opts.setReceiveMaximum(20);
        opts.setUserName(agentId);        // MQTT username = agent_id
        if (credential != null && !credential.isEmpty()) {
            opts.setPassword(credential.getBytes(UTF8));
        }
        return opts;
    }

    /** 재접속 시에도 매번 재구독한다. cleanStart=false 라도 안전을 위해. */
    private void subscribeAll(boolean reconnect) {
        String[] topics = { commandTopic(), BROADCAST_TOPIC };
        try {
            // 두 토픽을 한 SUBSCRIBE 로 보낸다. SUBACK 의 reason code 가 topics
            // 순서와 1:1 로 대응하므로 어느 토픽이 거부됐는지 특정할 수 있다.
            IMqttToken token = client.subscribe(topics, new int[] { 1, 1 });
            int[] codes = token == null ? null : token.getReasonCodes();

            String verb = reconnect ? "resubscribed" : "subscribed";
            String granted = grantedTopics(topics, codes);
            if (granted != null) {
                logger.info("MQTT " + verb + ": " + granted);
            }

            // MQTT v5 에서 ACL 거부는 MqttException 이 아니라 SUBACK 의 reason code
            // 로 온다. 토큰을 버리면 "구독 로그는 남았는데 명령이 안 온다" 가 된다.
            String rejected = rejectedTopics(topics, codes);
            if (rejected != null) {
                logger.severe("MQTT subscribe rejected by broker (clientId=" + agentId
                        + "): " + rejected + ". Commands will NOT arrive on those topics."
                        + " Check broker ACL for this client.");
            }
        } catch (MqttException e) {
            logger.log(Level.SEVERE, "MQTT subscribe failed. topics=" + join(topics), e);
        }
    }

    /**
     * SUBACK 에서 허가된 토픽만 "topic(qos=n)" 형태로 모은다. 없으면 null.
     * reason code 를 알 수 없으면(codes == null) 판단 근거가 없으므로 전부 허가로 본다.
     */
    static String grantedTopics(String[] topics, int[] codes) {
        if (topics == null) {
            return null;
        }
        if (codes == null) {
            return join(topics);
        }
        StringBuilder sb = new StringBuilder();
        int n = Math.min(topics.length, codes.length);
        for (int i = 0; i < n; i++) {
            if (codes[i] < SUBACK_FAILURE_MIN) {
                append(sb, topics[i] + "(qos=" + codes[i] + ")");
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /**
     * SUBACK 에서 거부된(reason code 0x80 이상) 토픽만 "topic(rc=135 Not authorized)"
     * 형태로 모은다. 전부 허가되었거나 판단할 수 없으면 null.
     */
    static String rejectedTopics(String[] topics, int[] codes) {
        if (topics == null || codes == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int n = Math.min(topics.length, codes.length);
        for (int i = 0; i < n; i++) {
            if (codes[i] >= SUBACK_FAILURE_MIN) {
                append(sb, topics[i] + "(rc=" + codes[i] + " " + reasonCodeName(codes[i]) + ")");
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** SUBACK 실패 reason code 를 운영자가 바로 알아볼 이름으로 바꾼다. */
    static String reasonCodeName(int code) {
        switch (code) {
            case 128: return "Unspecified error";
            case 131: return "Implementation specific error";
            case 135: return "Not authorized";
            case 143: return "Topic filter invalid";
            case 151: return "Quota exceeded";
            case 158: return "Shared subscriptions not supported";
            case 161: return "Subscription identifiers not supported";
            case 162: return "Wildcard subscriptions not supported";
            default:  return "unknown";
        }
    }

    private static String join(String[] topics) {
        StringBuilder sb = new StringBuilder();
        if (topics != null) {
            for (String t : topics) {
                append(sb, t);
            }
        }
        return sb.toString();
    }

    private static void append(StringBuilder sb, String item) {
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append(item);
    }

    /**
     * 수신 메시지를 order 실행으로 넘긴다.
     * Kafka 경로(MwConsumerThread)와 동일한 규약을 따른다.
     */
    private void dispatch(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), UTF8);
        try {
            JSONObject command = (JSONObject) new JSONParser().parse(payload);

            Object cmdIdObj = command.get("cmdId");
            if (cmdIdObj != null) {
                String cmdId = cmdIdObj.toString();
                synchronized (seen) {
                    if (!seen.add(cmdId)) {
                        logger.info("MQTT duplicate cmdId ignored : " + cmdId);
                        return;
                    }
                }
            }

            String command_class = (String) command.get("command_class");
            if (command_class == null || command_class.isEmpty()) {
                logger.warning("MQTT command without command_class ignored. topic=" + topic);
                return;
            }

            OrderCallerThread thread =
                    new OrderCallerThread("mwagent.order." + command_class, command);
            thread.setDaemon(true);
            thread.start();

            logger.info("Order called by MQTT : topic " + topic + "_" + payload);

        } catch (Exception e) {
            // 파싱 실패 메시지 하나가 구독을 끊지 않도록 여기서 삼킨다
            logger.log(Level.SEVERE, "MQTT message handling failed. topic=" + topic, e);
        }
    }

    // ------------------------------------------------------------------
    // 연결 상태 로그 — 감시 스레드의 주기적 폴링으로 판정한다
    //
    // Paho 는 automaticReconnect 재접속 실패 시 사용자 콜백을 호출하지 않는다
    // (MqttReconnectActionListener.onFailure 는 내부 로그 + 재예약만 한다).
    // 따라서 보고를 콜백에 의존하면 브로커가 장기간 죽어 있어도 최초 1줄 뒤로는
    // 아무것도 남지 않는다. checkConnection() 이 isConnected() 를 직접 보고
    // 판정하므로, 이벤트 유무와 무관하게 REPORT_MS 마다 1줄이 보장된다.
    //
    // 횟수 기준(연속 실패 N회) 억제는 플래핑에서 무력하다. 재접속이 성공할
    // 때마다 카운터가 리셋되어 매 사이클이 "첫 실패"로 취급되기 때문이다.
    // ------------------------------------------------------------------

    /** 끊김 사유를 사람이 읽을 수 있는 문자열로. reasonString 이 비어도 정보를 남긴다. */
    static String describe(MqttDisconnectResponse response) {
        if (response == null) {
            return "no response";
        }
        if (response.getReasonString() != null) {
            return response.getReasonString();
        }
        if (response.getException() != null) {
            return describe(response.getException());
        }
        return "rc=" + response.getReturnCode();
    }

    static String describe(MqttException e) {
        if (e == null) {
            return "unknown";
        }

        StringBuilder sb = new StringBuilder("rc=").append(e.getReasonCode());

        String msg = e.getMessage();
        if (msg != null && !msg.isEmpty()) {
            sb.append(' ').append(msg);
        } else {
            sb.append(' ').append(e.getClass().getSimpleName());
        }

        // 원인 예외를 감싼 경우(rc=0) getMessage() 는 "Untranslated MqttException" 처럼
        // 쓸모없는 문자열이다. 실제 사유는 cause 에만 있으므로 함께 남긴다.
        Throwable cause = e.getCause();
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isEmpty()) {
            sb.append(" cause=").append(cause.getMessage());
        }

        return sb.toString();
    }

    /** 콜백이 알려준 끊김. 즉시 1줄 남기고, 이후 보고는 checkConnection() 이 맡는다. */
    private void recordUnstable(String reason) {
        long now = System.currentTimeMillis();
        events.incrementAndGet();
        lastReason = reason;

        if (unstableSince == 0L) {
            unstableSince = now;
            lastReport = now;
            logger.warning("MQTT unstable: disconnected (" + reason + ")");
        }
        // 이미 불안정으로 표시된 뒤에는 기록하지 않는다 (플래핑 로그 폭증 방지)
    }

    /**
     * 연결 상태를 직접 확인한다. MqttService 의 감시 스레드가 주기적으로 호출한다.
     *
     *  - 연결됨 + STABLE_MS 이상 유지 -> 복구로 판정
     *  - 끊김 지속 -> REPORT_MS 마다 1줄
     *
     * Paho 콜백에 의존하지 않으므로 재접속 실패가 조용히 반복되어도 누락되지 않는다.
     */
    void checkConnection() {
        long now = System.currentTimeMillis();

        if (client != null && client.isConnected()) {
            if (unstableSince != 0L && now - connectedAt >= STABLE_MS) {
                logger.warning("MQTT recovered - was unstable " + ((now - unstableSince) / 60000L)
                        + "m, " + events.getAndSet(0) + " events");
                unstableSince = 0L;
            }
            return;
        }

        // 끊긴 상태. 콜백 없이 끊긴 경우(초기 연결 실패 등)도 여기서 처음 잡힌다.
        if (unstableSince == 0L) {
            unstableSince = now;
            lastReport = now;
            events.incrementAndGet();
            logger.warning("MQTT unstable: not connected (" + lastReason + ")");
        } else if (now - lastReport >= REPORT_MS) {
            lastReport = now;
            logger.warning("MQTT still unstable for " + ((now - unstableSince) / 60000L)
                    + "m - " + events.get() + " events, last: " + lastReason);
        }

        // 최초 접속이 성공한 적 없으면 Paho 자동 재접속이 무장되지 않았다. 직접 시도한다.
        // 한 번이라도 붙은 뒤의 재접속은 Paho 가 맡으므로 건드리지 않는다
        // (연결 진행 중에 connect() 를 또 부르면 예외가 난다).
        if (!everConnected) {
            try {
                connect();
            } catch (MqttException e) {
                // 사유는 connect() 가 lastReason 에 기록한다. 실패 자체는 조용히 넘긴다.
                events.incrementAndGet();
            }
        }
    }

    public void disconnect() {
        if (client == null) {
            return;
        }
        try {
            if (client.isConnected()) {
                client.disconnect(5000);
            }
            client.close();
            logger.info("MQTT disconnected.");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error closing MQTT client", e);
        }
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    private String commandTopic() {
        return "cmd/" + agentId + "/req";
    }

}
