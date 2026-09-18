package mwagent.mqtt;

import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MwMqttSubscriber 테스트
 *
 * 브로커 없이 검증 가능한 부분만 다룬다. client 가 null 이면 checkConnection() 은
 * 끊긴 상태로 취급하므로, 접속 없이도 보고 주기를 그대로 확인할 수 있다.
 */
class MwMqttSubscriberTest {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private Logger logger;
    private CapturingHandler captured;

    /** 로그 메시지를 모아두는 핸들러. 보고 주기 검증에 쓴다. */
    private static final class CapturingHandler extends Handler {
        private final List<String> messages = new ArrayList<String>();

        @Override
        public void publish(LogRecord record) {
            messages.add(record.getMessage());
        }

        @Override
        public void flush() { }

        @Override
        public void close() { }

        List<String> warnings() {
            return messages;
        }

        void clear() {
            messages.clear();
        }
    }

    @BeforeEach
    void setUp() {
        logger = Logger.getLogger("MwMqttSubscriberTest");
        logger.setUseParentHandlers(false);
        captured = new CapturingHandler();
        logger.addHandler(captured);
        mwagent.common.Config.getConfig().setLogger(logger);
    }

    @AfterEach
    void tearDown() {
        logger.removeHandler(captured);
    }

    /**
     * 도달 불가 주소를 쓴다. 실제 브로커(1883)가 떠 있는 환경에서도 결과가 흔들리지
     * 않아야 하므로 절대 붙지 않는 포트를 고정한다.
     */
    private MwMqttSubscriber newSubscriber() {
        return new MwMqttSubscriber("tcp://127.0.0.1:1", "test-agent", "");
    }

    /**
     * 이미 한 번 접속에 성공한 상태. 이 경우 재접속은 Paho 가 맡으므로
     * checkConnection() 은 재시도하지 않고 보고만 한다. 보고 주기만 검증할 때 쓴다.
     */
    private MwMqttSubscriber newAlreadyConnectedOnce() throws Exception {
        MwMqttSubscriber s = newSubscriber();
        Field f = MwMqttSubscriber.class.getDeclaredField("everConnected");
        f.setAccessible(true);
        f.setBoolean(s, true);
        return s;
    }

    // ------------------------------------------------------------------
    // normalizeUri - Kafka 식 host:port 설정을 MQTT URI 로 보정한다
    // ------------------------------------------------------------------

    @Test
    void normalizeUri_WithoutScheme_ShouldPrependTcp() {
        assertThat(MwMqttSubscriber.normalizeUri("localhost:1883")).isEqualTo("tcp://localhost:1883");
    }

    @Test
    void normalizeUri_WithScheme_ShouldKeepAsIs() {
        assertThat(MwMqttSubscriber.normalizeUri("tcp://broker:1883")).isEqualTo("tcp://broker:1883");
        assertThat(MwMqttSubscriber.normalizeUri("ssl://broker:8883")).isEqualTo("ssl://broker:8883");
    }

    @Test
    void normalizeUri_ShouldTrimWhitespace() {
        assertThat(MwMqttSubscriber.normalizeUri("  localhost:1883  ")).isEqualTo("tcp://localhost:1883");
    }

    @Test
    void normalizeUri_WithNullOrEmpty_ShouldNotPrepend() {
        assertThat(MwMqttSubscriber.normalizeUri(null)).isNull();
        assertThat(MwMqttSubscriber.normalizeUri("")).isEmpty();
        assertThat(MwMqttSubscriber.normalizeUri("   ")).isEmpty();
    }

    // ------------------------------------------------------------------
    // describe - 끊김 사유를 남긴다. reasonString 이 비어도 정보가 있어야 한다
    //            (1시간에 1줄만 남기므로 그 1줄의 정보량이 중요하다)
    // ------------------------------------------------------------------

    @Test
    void describe_WithReasonString_ShouldUseIt() {
        MqttDisconnectResponse r =
                new MqttDisconnectResponse(0, "server shutting down", null, null);

        assertThat(MwMqttSubscriber.describe(r)).isEqualTo("server shutting down");
    }

    @Test
    void describe_WithoutReasonString_ShouldFallBackToReturnCode() {
        MqttDisconnectResponse r = new MqttDisconnectResponse(142, null, null, null);

        assertThat(MwMqttSubscriber.describe(r)).isEqualTo("rc=142");
    }

    @Test
    void describe_WithException_ShouldIncludeReasonCodeAndMessage() {
        MqttDisconnectResponse r = new MqttDisconnectResponse(
                new MqttException(MqttException.REASON_CODE_MALFORMED_PACKET));

        String described = MwMqttSubscriber.describe(r);

        assertThat(described).startsWith("rc=" + MqttException.REASON_CODE_MALFORMED_PACKET);
    }

    @Test
    void describe_WithWrappedCause_ShouldSurfaceCauseMessage() {
        // 원인 예외를 감싼 MqttException 은 getMessage() 가
        // "Untranslated MqttException - RC: 0" 처럼 쓸모없다. cause 를 함께 남겨야 한다.
        MqttException wrapped = new MqttException(new java.net.ConnectException("Connection refused"));

        assertThat(MwMqttSubscriber.describe(wrapped))
                .contains("rc=0")
                .contains("cause=Connection refused");
    }

    @Test
    void describe_ShouldNeverReturnBareNullText() {
        // reasonString 이 null 이어도 "null" 이 그대로 찍히면 안 된다
        MqttDisconnectResponse r = new MqttDisconnectResponse(0, null, null, null);

        assertThat(MwMqttSubscriber.describe(r)).isNotEqualTo("null").isNotEmpty();
    }

    @Test
    void describe_WithNullResponse_ShouldNotReturnNullText() {
        assertThat(MwMqttSubscriber.describe((MqttDisconnectResponse) null)).isEqualTo("no response");
    }

    @Test
    void describe_WithNullException_ShouldNotReturnNullText() {
        assertThat(MwMqttSubscriber.describe((MqttException) null)).isEqualTo("unknown");
    }

    // ------------------------------------------------------------------
    // checkConnection - 감시 스레드가 부르는 주기 보고
    //
    // Paho 는 재접속 실패 시 콜백을 호출하지 않으므로, 보고가 콜백이 아니라
    // 이 폴링에 달려 있어야 한다. 아래 테스트가 그 보장을 고정한다.
    // ------------------------------------------------------------------

    @Test
    void checkConnection_WhenDisconnected_ShouldReportOnce() throws Exception {
        MwMqttSubscriber s = newAlreadyConnectedOnce();

        s.checkConnection();

        assertThat(captured.warnings())
                .hasSize(1)
                .allSatisfy(m -> assertThat(m).contains("MQTT unstable"));
    }

    @Test
    void checkConnection_WhileStillDisconnected_ShouldSuppressRepeats() throws Exception {
        MwMqttSubscriber s = newAlreadyConnectedOnce();

        s.checkConnection();
        captured.clear();

        // 1시간이 지나지 않았으므로 몇 번을 불러도 조용해야 한다
        s.checkConnection();
        s.checkConnection();
        s.checkConnection();

        assertThat(captured.warnings()).isEmpty();
    }

    @Test
    void checkConnection_AfterReportInterval_ShouldReportAgain() throws Exception {
        MwMqttSubscriber s = newAlreadyConnectedOnce();
        s.checkConnection();
        captured.clear();

        backdateReportClock(s, reportIntervalMs() + 1000L);
        s.checkConnection();

        assertThat(captured.warnings()).hasSize(1);
        assertThat(captured.warnings().get(0)).contains("still unstable");
    }

    @Test
    void checkConnection_AfterReportInterval_ShouldIncludeLastReason() throws Exception {
        MwMqttSubscriber s = newAlreadyConnectedOnce();
        setLastReason(s, "rc=32103 Unable to connect to server");
        s.checkConnection();
        captured.clear();

        backdateReportClock(s, reportIntervalMs() + 1000L);
        s.checkConnection();

        assertThat(captured.warnings().get(0)).contains("rc=32103 Unable to connect to server");
    }

    @Test
    void checkConnection_ShouldReportRepeatedlyOverLongOutage() throws Exception {
        MwMqttSubscriber s = newAlreadyConnectedOnce();
        s.checkConnection();          // 최초 1줄
        captured.clear();

        // 1시간씩 세 번 경과 -> 세 줄이어야 한다 (콜백이 전혀 오지 않아도)
        for (int i = 0; i < 3; i++) {
            backdateReportClock(s, reportIntervalMs() + 1000L);
            s.checkConnection();
        }

        assertThat(captured.warnings()).hasSize(3);
        assertThat(captured.warnings()).allSatisfy(m -> assertThat(m).contains("still unstable"));
    }

    @Test
    void checkConnection_WhenNeverConnected_ShouldAttemptReconnect() {
        // 최초 접속이 성공한 적 없으면 Paho 자동 재접속이 무장되지 않는다.
        // checkConnection() 이 직접 connect() 를 시도해야 하고, 그 실패 사유가 기록되어야 한다.
        MwMqttSubscriber s = newSubscriber();

        s.checkConnection();

        assertThat(lastReasonOf(s))
                .as("connect() 를 시도했다면 실패 사유가 기록된다")
                .isNotEqualTo("unknown");
    }

    @Test
    void checkConnection_WhenAlreadyConnectedOnce_ShouldNotCallConnectItself() throws Exception {
        // 한 번 붙은 뒤의 재접속은 Paho 담당이다. 여기서 connect() 를 또 부르면
        // "연결 진행 중" 예외가 나므로 건드리지 않아야 한다.
        MwMqttSubscriber s = newAlreadyConnectedOnce();

        s.checkConnection();

        assertThat(lastReasonOf(s)).isEqualTo("unknown");
    }

    // ------------------------------------------------------------------
    // dispatch - cmdId 멱등 처리. QoS1 은 at-least-once 라 중복 수신이 정상이다
    // ------------------------------------------------------------------

    @Test
    void dispatch_WithDuplicateCmdId_ShouldIgnoreSecondDelivery() throws Exception {
        MwMqttSubscriber s = newSubscriber();
        String payload = "{\"cmdId\":\"dup-1\"}";      // command_class 없음 -> 실행까지 가지 않는다

        invokeDispatch(s, payload);
        captured.clear();
        invokeDispatch(s, payload);

        assertThat(captured.warnings())
                .anySatisfy(m -> assertThat(m).contains("duplicate cmdId ignored"));
    }

    @Test
    void dispatch_WithDifferentCmdId_ShouldNotBeTreatedAsDuplicate() throws Exception {
        MwMqttSubscriber s = newSubscriber();

        invokeDispatch(s, "{\"cmdId\":\"a\"}");
        captured.clear();
        invokeDispatch(s, "{\"cmdId\":\"b\"}");

        assertThat(captured.warnings())
                .noneSatisfy(m -> assertThat(m).contains("duplicate cmdId ignored"));
    }

    @Test
    void dispatch_WithoutCommandClass_ShouldIgnoreAndNotThrow() throws Exception {
        MwMqttSubscriber s = newSubscriber();

        invokeDispatch(s, "{\"cmdId\":\"no-class\"}");

        assertThat(captured.warnings())
                .anySatisfy(m -> assertThat(m).contains("without command_class"));
    }

    @Test
    void dispatch_WithMalformedJson_ShouldSwallowSoSubscriptionSurvives() throws Exception {
        MwMqttSubscriber s = newSubscriber();

        // 파싱 실패 메시지 하나가 구독을 끊어서는 안 된다
        invokeDispatch(s, "not json at all");

        assertThat(captured.warnings())
                .anySatisfy(m -> assertThat(m).contains("MQTT message handling failed"));
    }

    @Test
    void isConnected_WithoutClient_ShouldBeFalse() {
        assertThat(newSubscriber().isConnected()).isFalse();
    }

    @Test
    void disconnect_WithoutClient_ShouldNotThrow() {
        MwMqttSubscriber s = newSubscriber();

        assertThatCode(() -> s.disconnect()).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------
    // SUBACK reason code 해석
    //
    // MQTT v5 에서 ACL 거부는 MqttException 이 아니라 SUBACK 의 reason code 로
    // 온다. 이 판정이 틀리면 "구독 성공 로그는 남았는데 명령이 안 온다" 는
    // 진단 불가 상태가 된다.
    // ------------------------------------------------------------------

    @Test
    void rejectedTopics_WhenAllGranted_ShouldBeNull() {
        String[] topics = { "cmd/agent-1/req", "cmd/broadcast/req" };

        assertThat(MwMqttSubscriber.rejectedTopics(topics, new int[] { 1, 1 })).isNull();
    }

    @Test
    void rejectedTopics_WhenNotAuthorized_ShouldNameTopicAndReason() {
        String[] topics = { "cmd/agent-1/req", "cmd/broadcast/req" };

        String rejected = MwMqttSubscriber.rejectedTopics(topics, new int[] { 1, 135 });

        // 어느 토픽이 왜 거부됐는지가 로그의 유일한 단서다
        assertThat(rejected).contains("cmd/broadcast/req")
                .contains("rc=135")
                .contains("Not authorized")
                .doesNotContain("cmd/agent-1/req");
    }

    @Test
    void grantedTopics_ShouldExcludeRejectedOnes() {
        String[] topics = { "cmd/agent-1/req", "cmd/broadcast/req" };

        String granted = MwMqttSubscriber.grantedTopics(topics, new int[] { 1, 135 });

        // 거부된 토픽이 성공 줄에 섞이면 안 된다 (이게 원래의 오진 원인이었다)
        assertThat(granted).contains("cmd/agent-1/req").doesNotContain("cmd/broadcast/req");
    }

    @Test
    void grantedTopics_WithQos0Grant_ShouldStillCountAsGranted() {
        String[] topics = { "cmd/agent-1/req" };

        assertThat(MwMqttSubscriber.grantedTopics(topics, new int[] { 0 }))
                .contains("cmd/agent-1/req");
        assertThat(MwMqttSubscriber.rejectedTopics(topics, new int[] { 0 })).isNull();
    }

    @Test
    void reasonCodes_WhenUnavailable_ShouldAssumeAllGranted() {
        String[] topics = { "cmd/agent-1/req", "cmd/broadcast/req" };

        // 토큰이 reason code 를 주지 않으면 거부라고 단정할 근거가 없다
        assertThat(MwMqttSubscriber.grantedTopics(topics, null))
                .contains("cmd/agent-1/req").contains("cmd/broadcast/req");
        assertThat(MwMqttSubscriber.rejectedTopics(topics, null)).isNull();
    }

    @Test
    void reasonCodeName_WithUnknownCode_ShouldNotReturnNull() {
        assertThat(MwMqttSubscriber.reasonCodeName(200)).isEqualTo("unknown");
        assertThat(MwMqttSubscriber.reasonCodeName(143)).isEqualTo("Topic filter invalid");
    }

    // ------------------------------------------------------------------
    // helpers
    //
    // 보고 주기는 1시간이라 실시간으로 기다릴 수 없다. 시계 대신 내부 타임스탬프를
    // 과거로 밀어 경과 상황을 만든다. dispatch 는 private 이므로 리플렉션으로 부른다.
    // ------------------------------------------------------------------

    private static long reportIntervalMs() throws Exception {
        Field f = MwMqttSubscriber.class.getDeclaredField("REPORT_MS");
        f.setAccessible(true);
        return ((Long) f.get(null)).longValue();
    }

    private static void backdateReportClock(MwMqttSubscriber s, long millis) throws Exception {
        shiftLongField(s, "lastReport", millis);
        shiftLongField(s, "unstableSince", millis);
    }

    private static void shiftLongField(MwMqttSubscriber s, String name, long millis) throws Exception {
        Field f = MwMqttSubscriber.class.getDeclaredField(name);
        f.setAccessible(true);
        f.setLong(s, f.getLong(s) - millis);
    }

    private static String lastReasonOf(MwMqttSubscriber s) {
        try {
            Field f = MwMqttSubscriber.class.getDeclaredField("lastReason");
            f.setAccessible(true);
            return (String) f.get(s);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setLastReason(MwMqttSubscriber s, String reason) throws Exception {
        Field f = MwMqttSubscriber.class.getDeclaredField("lastReason");
        f.setAccessible(true);
        f.set(s, reason);
    }

    private static void invokeDispatch(MwMqttSubscriber s, String payload) throws Exception {
        Method m = MwMqttSubscriber.class.getDeclaredMethod(
                "dispatch", String.class, MqttMessage.class);
        m.setAccessible(true);
        m.invoke(s, "cmd/test-agent/req", new MqttMessage(payload.getBytes(UTF8)));
    }
}
