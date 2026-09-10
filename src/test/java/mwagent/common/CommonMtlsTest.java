package mwagent.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.*;

class CommonMtlsTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Initialize logger
        Config.getConfig().setLogger(Logger.getLogger("Test"));
    }

    @Test
    void testCreateMtlsClientWhenDisabled() {
        // Given: mTLS is disabled
        Config.getConfig().setUseMtls(false);

        // When: Creating mTLS client
        // Then: No exception should be thrown, client creation should be skipped
        assertThatCode(() -> Common.createMtlsClient()).doesNotThrowAnyException();
    }

    @Test
    void testCreateMtlsClientWithInvalidKeystore() {
        // Given: mTLS enabled but invalid keystore path
        Config config = Config.getConfig();
        config.setUseMtls(true);
        config.setClientKeystorePath("/invalid/path/client.p12");
        config.setClientKeystorePassword("password");
        config.setTruststorePath("/invalid/path/truststore.jks");
        config.setTruststorePassword("password");

        // When & Then: Creating mTLS client should throw exception
        assertThatThrownBy(() -> Common.createMtlsClient())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("mTLS client creation failed");
    }

    @Test
    void testRenewAccessTokenWithMtlsWhenDisabled() {
        // Given: mTLS is disabled
        Config.getConfig().setUseMtls(false);

        // When: Attempting to renew token with mTLS
        int result = Common.renewAccessTokenWithMtls();

        // Then: Should return error code
        assertThat(result).isEqualTo(-1);
    }

    @Test
    void testUpdateTokenMethod() {
        // Given: Config with refresh token
        Config config = Config.getConfig();
        config.setRefresh_token("test-refresh-token");
        config.setServer_url("http://localhost:8080");

        // When: Calling updateToken
        // Note: This will fail without a real server, but we test the method exists
        int result = Common.updateToken();

        // Then: Method should return error code (server returned 401 or connection failed)
        assertThat(result).isNegative();
    }

    @Test
    void testCreateHttpsClient() {
        // Given: Clean state
        // When: Creating HTTPS client
        // Then: No exception should be thrown
        assertThatCode(() -> Common.createHttpsClient()).doesNotThrowAnyException();
    }

    @Test
    void testMtlsClientNotInitializedError() {
        // Given: mTLS enabled but client not initialized
        Config config = Config.getConfig();
        config.setUseMtls(true);
        config.setClientKeystorePath("/invalid/path.p12");
        config.setClientKeystorePassword("password");
        config.setTruststorePath("/invalid/path.jks");
        config.setTruststorePassword("password");
        config.setServer_url("https://localhost:8443");

        // When: Attempting to renew token without initialized client
        int result = Common.renewAccessTokenWithMtls();

        // Then: Should return error code -2 (client creation failed)
        assertThat(result).isEqualTo(-2);
    }
}
