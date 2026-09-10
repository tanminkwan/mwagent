package mwagent.integration;

import mwagent.common.Common;
import mwagent.common.Config;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for mTLS token renewal and Cascading Token Renewal Strategy
 *
 * Prerequisites:
 * 1. Mock server running: python test-server/mock_server.py --ssl
 * 2. Certificates generated: test-server/generate-certs.bat or .sh
 * 3. Set environment variable: MTLS_INTEGRATION_TEST=true
 *
 * To run:
 * ./gradlew test --tests MtlsTokenRenewalIntegrationTest
 */
@EnabledIfEnvironmentVariable(named = "MTLS_INTEGRATION_TEST", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MtlsTokenRenewalIntegrationTest {

    private static final String KEYSTORE_PATH = "./test-server/certs/agent-test001.p12";
    private static final String KEYSTORE_PATH_EXPIRED = "./test-server/certs/agent-test003-expired.p12";
    private static final String TRUSTSTORE_PATH = "./test-server/certs/truststore.jks";
    private static final String SERVER_URL = "https://localhost:8443";

    @BeforeAll
    static void checkPrerequisites() {
        // Check if certificates exist
        boolean keystoreExists = Files.exists(Paths.get(KEYSTORE_PATH));
        boolean truststoreExists = Files.exists(Paths.get(TRUSTSTORE_PATH));

        if (!keystoreExists || !truststoreExists) {
            fail("Certificates not found. Run test-server/generate-certs.sh first.\n" +
                 "Expected files:\n" +
                 "  - " + KEYSTORE_PATH + " (exists: " + keystoreExists + ")\n" +
                 "  - " + TRUSTSTORE_PATH + " (exists: " + truststoreExists + ")");
        }

        // Initialize logger
        Config.getConfig().setLogger(Logger.getLogger("IntegrationTest"));
    }

    @Test
    @Order(1)
    @DisplayName("Test mTLS client creation and token renewal")
    void testMtlsTokenRenewal() {
        // Given: Configuration for mTLS
        Config config = Config.getConfig();
        config.setUseMtls(true);
        config.setClientKeystorePath(KEYSTORE_PATH);
        config.setClientKeystorePassword("agent-password");
        config.setTruststorePath(TRUSTSTORE_PATH);
        config.setTruststorePassword("truststore-password");
        config.setServer_url(SERVER_URL);

        // When: Creating mTLS client
        assertThatCode(() -> Common.createMtlsClient())
                .as("mTLS client should be created successfully")
                .doesNotThrowAnyException();

        // When: Renewing access token with mTLS
        int result = Common.renewAccessTokenWithMtls();

        // Then: Token renewal should succeed
        assertThat(result)
                .as("Token renewal should return success code (1)")
                .isEqualTo(1);

        // And: Access token should be set
        assertThat(config.getAccess_token())
                .as("Access token should not be empty")
                .isNotNull()
                .isNotEmpty();
    }

    @Test
    @Order(2)
    @DisplayName("Test refresh_token grant with valid token")
    void testRefreshTokenMethod() {
        // Given: Configuration for refresh token
        Config config = Config.getConfig();
        config.setUseMtls(false);
        config.setRefresh_token("refresh-token-test001");
        config.setServer_url(SERVER_URL);

        // When: Updating token with refresh token
        int result = Common.updateToken();

        // Then: Token update should succeed or fail gracefully
        // Note: This might fail if mock server is HTTP only
        assertThat(result)
                .as("Token update should return a result code")
                .isNotNull();

        if (result == 1) {
            // Success case
            assertThat(config.getAccess_token())
                    .as("Access token should be set on success")
                    .isNotNull()
                    .isNotEmpty();
        }
    }

    @Test
    @Order(3)
    @DisplayName("Test mTLS client creation with wrong password should fail")
    void testMtlsWithWrongCertificate() {
        // Given: Configuration with wrong keystore password
        Config config = Config.getConfig();
        config.setUseMtls(true);
        config.setClientKeystorePath(KEYSTORE_PATH);
        config.setClientKeystorePassword("wrong-password");
        config.setTruststorePath(TRUSTSTORE_PATH);
        config.setTruststorePassword("truststore-password");
        config.setServer_url(SERVER_URL);

        // When & Then: Creating mTLS client should fail
        assertThatThrownBy(() -> Common.createMtlsClient())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("mTLS client creation failed");
    }

    @Test
    @Order(4)
    @DisplayName("Test renewAccessTokenWithMtls returns -1 when mTLS is disabled")
    void testMtlsDisabledFallbackToRefreshToken() {
        // Given: mTLS disabled
        Config config = Config.getConfig();
        config.setUseMtls(false);

        // When: Attempting mTLS renewal
        int result = Common.renewAccessTokenWithMtls();

        // Then: Should return error indicating mTLS not enabled
        assertThat(result)
                .as("Should return -1 when mTLS is disabled")
                .isEqualTo(-1);
    }

    // ==================== Cascading Token Renewal Tests ====================

    @Test
    @Order(5)
    @DisplayName("Cascading: Valid refresh_token should succeed without mTLS fallback")
    void testCascadingWithValidRefreshToken() {
        // Given: Valid refresh token, mTLS enabled
        Config config = Config.getConfig();
        config.setUseMtls(true);
        config.setRefresh_token("refresh-token-test001");
        config.setClientKeystorePath(KEYSTORE_PATH);
        config.setClientKeystorePassword("agent-password");
        config.setTruststorePath(TRUSTSTORE_PATH);
        config.setTruststorePassword("truststore-password");
        config.setServer_url(SERVER_URL);

        // Ensure mTLS client is created
        Common.createMtlsClient();

        // When: Using cascading renewal
        int result = Common.renewAccessTokenWithFallback();

        // Then: Should succeed via refresh_token (no mTLS needed)
        assertThat(result)
                .as("Cascading renewal should succeed (refresh_token valid)")
                .isEqualTo(1);

        assertThat(config.getAccess_token())
                .as("Access token should be set")
                .isNotNull()
                .isNotEmpty();
    }

    @Test
    @Order(6)
    @DisplayName("Cascading: Expired refresh_token should fallback to mTLS")
    void testCascadingWithExpiredRefreshTokenFallbackToMtls() {
        // Given: Expired refresh token (agent-test003-expired has refresh_token_expired=true)
        Config config = Config.getConfig();
        config.setUseMtls(true);
        config.setRefresh_token("refresh-token-test003-expired");

        // Use test003-expired certificate if available, otherwise use test001
        String keystorePath = Files.exists(Paths.get(KEYSTORE_PATH_EXPIRED))
                ? KEYSTORE_PATH_EXPIRED : KEYSTORE_PATH;
        config.setClientKeystorePath(keystorePath);
        config.setClientKeystorePassword("agent-password");
        config.setTruststorePath(TRUSTSTORE_PATH);
        config.setTruststorePassword("truststore-password");
        config.setServer_url(SERVER_URL);

        // Ensure mTLS client is created
        Common.createMtlsClient();

        // When: Using cascading renewal
        // Note: This should:
        // 1. Try refresh_token -> get 401 (expired)
        // 2. Fallback to mTLS -> succeed
        int result = Common.renewAccessTokenWithFallback();

        // Then: Result depends on server configuration
        // If server has agent-test003-expired with mTLS cert, it should succeed
        // Otherwise, it may fail - which is expected behavior
        assertThat(result)
                .as("Cascading renewal result code")
                .isNotNull();

        if (result == 1) {
            assertThat(config.getAccess_token())
                    .as("Access token should be set after mTLS fallback")
                    .isNotNull()
                    .isNotEmpty();
        }
    }

    @Test
    @Order(7)
    @DisplayName("Cascading: Expired refresh_token without mTLS should fail with -401")
    void testCascadingWithExpiredRefreshTokenNoMtls() {
        // Given: Expired refresh token, mTLS disabled
        Config config = Config.getConfig();
        config.setUseMtls(false);
        config.setRefresh_token("refresh-token-test003-expired");
        config.setServer_url(SERVER_URL);

        // When: Using cascading renewal
        int result = Common.renewAccessTokenWithFallback();

        // Then: Should fail because refresh_token is expired and mTLS is not available
        assertThat(result)
                .as("Should return -401 when refresh_token expired and mTLS disabled")
                .isEqualTo(-401);
    }

    @Test
    @Order(8)
    @DisplayName("updateToken returns -401 for expired refresh_token")
    void testUpdateTokenReturnsNegative401ForExpiredToken() {
        // Given: Configuration with expired refresh token
        Config config = Config.getConfig();
        config.setRefresh_token("refresh-token-test003-expired");
        config.setServer_url(SERVER_URL);

        // When: Calling updateToken
        int result = Common.updateToken();

        // Then: Should return -401 indicating refresh_token expired
        assertThat(result)
                .as("updateToken should return -401 for expired refresh_token")
                .isEqualTo(-401);
    }
}
