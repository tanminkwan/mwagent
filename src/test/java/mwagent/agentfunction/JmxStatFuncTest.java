package mwagent.agentfunction;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * JmxStatFunc 테스트 - JNDI 이름 / ObjectName 에 들어가는 값 검증
 */
class JmxStatFuncTest {

    @Test
    void isValidJmxName_WithPlainNames_ShouldReturnTrue() {
        assertThat(JmxStatFunc.isValidJmxName("adminServer")).isTrue();
        assertThat(JmxStatFunc.isValidJmxName("jeus_domain")).isTrue();
        assertThat(JmxStatFunc.isValidJmxName("server-1.a")).isTrue();
    }

    @Test
    void isValidJmxName_WithInjection_ShouldReturnFalse() {
        assertThat(JmxStatFunc.isValidJmxName("../other")).isFalse();
        assertThat(JmxStatFunc.isValidJmxName("a,name=*")).isFalse();
        assertThat(JmxStatFunc.isValidJmxName("ldap://evil/x")).isFalse();
        assertThat(JmxStatFunc.isValidJmxName("")).isFalse();
        assertThat(JmxStatFunc.isValidJmxName(null)).isFalse();
    }
}
