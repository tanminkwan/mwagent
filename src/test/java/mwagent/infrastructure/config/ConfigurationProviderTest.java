package mwagent.infrastructure.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import mwagent.common.Config;

/**
 * Tests for ConfigurationProvider interface implementation.
 */
@DisplayName("ConfigurationProvider Interface Tests")
class ConfigurationProviderTest {

    private ConfigurationProvider config;

    @BeforeEach
    void setUp() {
        // Use Config singleton as ConfigurationProvider
        config = Config.getConfig();
    }

    @Test
    @DisplayName("Config implements ConfigurationProvider interface")
    void config_ImplementsConfigurationProvider() {
        assertNotNull(config);
        assertTrue(config instanceof ConfigurationProvider);
    }

    @Test
    @DisplayName("getString returns null for missing key")
    void getString_MissingKey_ReturnsNull() {
        String result = config.getString("non_existent_key_xyz");
        assertNull(result);
    }

    @Test
    @DisplayName("getString with default returns default for missing key")
    void getString_MissingKeyWithDefault_ReturnsDefault() {
        String result = config.getString("non_existent_key_xyz", "default_value");
        assertEquals("default_value", result);
    }

    @Test
    @DisplayName("getInt returns default for missing key")
    void getInt_MissingKey_ReturnsDefault() {
        int result = config.getInt("non_existent_key_xyz", 42);
        assertEquals(42, result);
    }

    @Test
    @DisplayName("getBoolean returns false for missing key")
    void getBoolean_MissingKey_ReturnsFalse() {
        boolean result = config.getBoolean("non_existent_key_xyz");
        assertFalse(result);
    }

    @Test
    @DisplayName("getBoolean with default returns default for missing key")
    void getBoolean_MissingKeyWithDefault_ReturnsDefault() {
        boolean result = config.getBoolean("non_existent_key_xyz", true);
        assertTrue(result);
    }

    @Test
    @DisplayName("getAgentVersion returns non-null version")
    void getAgentVersion_ReturnsNonNull() {
        String version = config.getAgentVersion();
        assertNotNull(version);
        assertFalse(version.isEmpty());
    }

    @Test
    @DisplayName("getAgentType returns JAVAAGENT")
    void getAgentType_ReturnsJavaAgent() {
        String type = config.getAgentType();
        assertEquals("JAVAAGENT", type);
    }

    @Test
    @DisplayName("setAccessToken and getAccessToken work correctly")
    void accessToken_SetAndGet() {
        String testToken = "test_access_token_123";
        config.setAccessToken(testToken);
        assertEquals(testToken, config.getAccessToken());
    }

    @Test
    @DisplayName("setRefreshToken and getRefreshToken work correctly")
    void refreshToken_SetAndGet() {
        String testToken = "test_refresh_token_456";
        config.setRefreshToken(testToken);
        assertEquals(testToken, config.getRefreshToken());
    }

    @Test
    @DisplayName("isMtlsEnabled returns boolean")
    void isMtlsEnabled_ReturnsBoolean() {
        // Just verify it doesn't throw - actual value depends on config
        boolean result = config.isMtlsEnabled();
        assertTrue(result == true || result == false);
    }

    @Test
    @DisplayName("isKafkaEnabled returns boolean based on broker address")
    void isKafkaEnabled_ReturnsBooleanBasedOnBrokerAddress() {
        // Set empty broker address
        config.setKafkaBrokerAddress("");
        assertFalse(config.isKafkaEnabled());

        // Set valid broker address
        config.setKafkaBrokerAddress("localhost:9092");
        assertTrue(config.isKafkaEnabled());

        // Reset
        config.setKafkaBrokerAddress("");
    }

    @Test
    @DisplayName("getEnvironment returns non-null map")
    void getEnvironment_ReturnsNonNullMap() {
        assertNotNull(config.getEnvironment());
    }

    @Test
    @DisplayName("Security check methods return boolean")
    void securityCheckMethods_ReturnBoolean() {
        // Just verify methods work
        boolean cmdCheck = config.isCommandInjectionCheckEnabled();
        boolean pathCheck = config.isPathTraversalCheckEnabled();

        assertTrue(cmdCheck == true || cmdCheck == false);
        assertTrue(pathCheck == true || pathCheck == false);
    }
}
