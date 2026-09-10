package mwagent.agentfunction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AgentFuncFactoryTest {

    @Test
    void testGetAgentFuncHelloFunc() {
        // When
        AgentFunc func = AgentFuncFactory.getAgentFunc("say_hello");

        // Then
        assertThat(func).isNotNull();
        assertThat(func).isInstanceOf(HelloFunc.class);
    }

    @Test
    void testGetAgentFuncJmxStatFunc() {
        // When
        AgentFunc func = AgentFuncFactory.getAgentFunc("get_server_stat");

        // Then
        assertThat(func).isNotNull();
        assertThat(func).isInstanceOf(JmxStatFunc.class);
    }

    @Test
    void testGetAgentFuncSSLCertiFunc() {
        // When
        AgentFunc func = AgentFuncFactory.getAgentFunc("get_ssl_certi");

        // Then
        assertThat(func).isNotNull();
        assertThat(func).isInstanceOf(SSLCertiFunc.class);
    }

    @Test
    void testGetAgentFuncSSLCertiFileFunc() {
        // When
        AgentFunc func = AgentFuncFactory.getAgentFunc("get_ssl_certifile");

        // Then
        assertThat(func).isNotNull();
        assertThat(func).isInstanceOf(SSLCertiFileFunc.class);
    }

    @Test
    void testGetAgentFuncDownloadNUnzipFunc() {
        // When
        AgentFunc func = AgentFuncFactory.getAgentFunc("download_n_unzip");

        // Then
        assertThat(func).isNotNull();
        assertThat(func).isInstanceOf(DownloadNUnzipFunc.class);
    }

    @Test
    void testGetAgentFuncUnknownType() {
        // When - Invalid function type
        AgentFunc func = AgentFuncFactory.getAgentFunc("unknown_function_type");

        // Then - Should return null for unknown types
        assertThat(func).isNull();
    }

    @Test
    void testGetAgentFuncNullType() {
        // When - Null function type
        AgentFunc func = AgentFuncFactory.getAgentFunc(null);

        // Then - Should return null
        assertThat(func).isNull();
    }

    @Test
    void testGetAgentFuncEmptyType() {
        // When - Empty string function type
        AgentFunc func = AgentFuncFactory.getAgentFunc("");

        // Then - Should return null
        assertThat(func).isNull();
    }
}
