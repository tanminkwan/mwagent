package mwagent.lifecycle;

import mwagent.common.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.*;

/**
 * Test for AgentLifecycleManager mTLS functionality
 */
class AgentLifecycleManagerMtlsTest {

    @BeforeEach
    void setUp() {
        // Initialize logger
        Config.getConfig().setLogger(Logger.getLogger("Test"));
    }

    @Test
    void testLifecycleStateTransitions() {
        // Given: New lifecycle manager
        AgentLifecycleManager manager = new AgentLifecycleManager();

        // Then: Initial state should be CREATED
        assertThat(manager.getState())
                .as("Initial state should be CREATED")
                .isEqualTo(LifecycleState.CREATED);
    }

    @Test
    void testConfigurationWithMtlsEnabled() {
        // Given: Configuration with mTLS enabled
        Config config = Config.getConfig();
        config.setUseMtls(true);
        config.setServer_url("https://localhost:8443");

        // When: Creating lifecycle manager
        AgentLifecycleManager manager = new AgentLifecycleManager();

        // Then: Manager should be created successfully
        assertThat(manager).isNotNull();
        assertThat(manager.getState()).isEqualTo(LifecycleState.CREATED);
    }

    @Test
    void testConfigurationWithMtlsDisabled() {
        // Given: Configuration with mTLS disabled
        Config config = Config.getConfig();
        config.setUseMtls(false);
        config.setRefresh_token("test-refresh-token");
        config.setServer_url("http://localhost:8080");

        // When: Creating lifecycle manager
        AgentLifecycleManager manager = new AgentLifecycleManager();

        // Then: Manager should be created successfully
        assertThat(manager).isNotNull();
        assertThat(manager.getState()).isEqualTo(LifecycleState.CREATED);
    }

    @Test
    void testLifecycleStateValidTransitions() {
        // Test valid state transitions
        LifecycleState created = LifecycleState.CREATED;
        LifecycleState starting = LifecycleState.STARTING;
        LifecycleState running = LifecycleState.RUNNING;
        LifecycleState stopping = LifecycleState.STOPPING;
        LifecycleState stopped = LifecycleState.STOPPED;

        // CREATED can transition to STARTING
        assertThat(created.canTransitionTo(starting)).isTrue();

        // RUNNING can transition to STOPPING
        assertThat(running.canTransitionTo(stopping)).isTrue();

        // STOPPING can transition to STOPPED
        assertThat(stopping.canTransitionTo(stopped)).isTrue();
    }

    @Test
    void testLifecycleStateInvalidTransitions() {
        // Test invalid state transitions
        LifecycleState stopped = LifecycleState.STOPPED;
        LifecycleState starting = LifecycleState.STARTING;

        // STOPPED cannot transition to STARTING (can't restart)
        assertThat(stopped.canTransitionTo(starting)).isFalse();
    }

    @Test
    void testMtlsConfigurationPersistence() {
        // Given: mTLS configuration
        Config config = Config.getConfig();
        config.setUseMtls(true);
        config.setClientKeystorePath("/path/to/keystore.p12");
        config.setClientKeystorePassword("password");

        // When: Retrieving configuration
        boolean useMtls = config.isUseMtls();
        String keystorePath = config.getClientKeystorePath();

        // Then: Configuration should be preserved
        assertThat(useMtls).isTrue();
        assertThat(keystorePath).isEqualTo("/path/to/keystore.p12");
    }
}
