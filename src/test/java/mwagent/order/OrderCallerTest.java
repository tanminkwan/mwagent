package mwagent.order;

import static mwagent.common.Config.getConfig;
import static org.assertj.core.api.Assertions.*;

import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * OrderCaller 테스트 - command_class 로 실행할 수 있는 클래스 제한
 *
 * REST 폴링·Executor·MQTT 세 경로가 모두 OrderCaller 를 거치므로 여기서 한 번 막는다.
 */
class OrderCallerTest {

    @BeforeEach
    void setUp() {
        getConfig().setLogger(Logger.getLogger("TestLogger"));
    }

    @Test
    void resolveOrderClass_WithConcreteOrder_ShouldResolve() {
        assertThat(OrderCaller.resolveOrderClass("mwagent.order.ExeAgentFunc")).isEqualTo(ExeAgentFunc.class);
        assertThat(OrderCaller.resolveOrderClass("mwagent.order.ReadPlainFile")).isEqualTo(ReadPlainFile.class);
    }

    @Test
    void resolveOrderClass_WithNonOrderOrAbstract_ShouldReject() {
        assertThat(OrderCaller.resolveOrderClass("mwagent.order.Order")).isNull();        // abstract
        assertThat(OrderCaller.resolveOrderClass("mwagent.order.ReadFile")).isNull();     // abstract
        assertThat(OrderCaller.resolveOrderClass("mwagent.order.OrderCaller")).isNull();  // not an Order
    }

    @Test
    void resolveOrderClass_OutsideOrderPackage_ShouldReject() {
        assertThat(OrderCaller.resolveOrderClass("java.lang.Runtime")).isNull();
        assertThat(OrderCaller.resolveOrderClass("mwagent.order.sub.Evil")).isNull();
        assertThat(OrderCaller.resolveOrderClass("mwagent.order.ExeShell$Inner")).isNull();
        assertThat(OrderCaller.resolveOrderClass("mwagent.order.")).isNull();
        assertThat(OrderCaller.resolveOrderClass("mwagent.order.NoSuchOrder")).isNull();
        assertThat(OrderCaller.resolveOrderClass(null)).isNull();
    }

    @Test
    void executeOrder_WithRejectedClass_ShouldReturnError() {
        assertThat(OrderCaller.executeOrder("mwagent.order.OrderCaller", new org.json.simple.JSONObject())).isEqualTo(-2);
    }
}
