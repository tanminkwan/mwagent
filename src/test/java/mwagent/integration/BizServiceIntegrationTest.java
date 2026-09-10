package mwagent.integration;

import mwagent.common.Common;
import mwagent.common.Config;
import mwagent.vo.MwResponseVO;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for Agent -> Auth Server -> Biz Service flow
 *
 * This test validates the complete OAuth2 token flow:
 * 1. Agent acquires access token from Auth Server
 * 2. Agent calls Biz Service API with the token
 * 3. Biz Service validates the token and returns data
 *
 * Prerequisites:
 * 1. Auth Server (mock_server.py) running on port 8080
 * 2. Biz Service (app.py) running on port 5000
 * 3. Set environment variable: BIZ_SERVICE_INTEGRATION_TEST=true
 *
 * To run:
 *   # Terminal 1: Start Auth Server
 *   cd test-server && python mock_server.py
 *
 *   # Terminal 2: Start Biz Service
 *   cd biz-service && python app.py
 *
 *   # Terminal 3: Run tests
 *   set BIZ_SERVICE_INTEGRATION_TEST=true
 *   ./gradlew test --tests BizServiceIntegrationTest
 */
@EnabledIfEnvironmentVariable(named = "BIZ_SERVICE_INTEGRATION_TEST", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Biz Service Integration Tests")
class BizServiceIntegrationTest {

    private static final String AUTH_SERVER_URL = "http://localhost:8080";
    private static final String BIZ_SERVICE_URL = "http://localhost:5000";
    private static final String REFRESH_TOKEN = "refresh-token-test001";

    private static String accessToken;
    private static Config config;

    @BeforeAll
    static void setup() {
        config = Config.getConfig();
        config.setLogger(Logger.getLogger("BizServiceIntegrationTest"));
        config.setServer_url(AUTH_SERVER_URL);
        config.setRefresh_token(REFRESH_TOKEN);

        // Verify services are running
        assertThat(isServiceRunning(AUTH_SERVER_URL + "/health"))
                .as("Auth Server should be running on " + AUTH_SERVER_URL)
                .isTrue();

        assertThat(isServiceRunning(BIZ_SERVICE_URL + "/health"))
                .as("Biz Service should be running on " + BIZ_SERVICE_URL)
                .isTrue();
    }

    // ==========================================================================
    // Token Acquisition Tests
    // ==========================================================================

    @Test
    @Order(1)
    @DisplayName("Acquire access token from Auth Server using refresh_token")
    void testAcquireAccessToken() {
        // When: Request access token with refresh_token
        int result = Common.updateToken();

        // Then: Token acquisition should succeed
        assertThat(result)
                .as("Token acquisition should return success (1)")
                .isEqualTo(1);

        accessToken = config.getAccess_token();

        assertThat(accessToken)
                .as("Access token should be set")
                .isNotNull()
                .isNotEmpty()
                .startsWith("eyJ");  // JWT format

        System.out.println("[Test] Access token acquired: ..." +
                accessToken.substring(accessToken.length() - 20));
    }

    // ==========================================================================
    // Biz Service API Tests with Valid Token
    // ==========================================================================

    @Test
    @Order(2)
    @DisplayName("Call Biz Service /api/whoami with valid token")
    void testBizServiceWhoami() throws Exception {
        // Given: Valid access token from previous test
        assertThat(accessToken).isNotNull();

        // When: Call /api/whoami
        HttpResponse response = httpGet(BIZ_SERVICE_URL + "/api/whoami", accessToken);

        // Then: Should return agent identity
        assertThat(response.statusCode)
                .as("Response status should be 200 OK")
                .isEqualTo(200);

        JSONObject json = parseJson(response.body);

        assertThat(json.get("agent_id"))
                .as("agent_id should match")
                .isEqualTo("testserver01_appuser_J");

        assertThat(json.get("hostname"))
                .as("hostname should match")
                .isEqualTo("testserver01");

        assertThat(json.get("username"))
                .as("username should match")
                .isEqualTo("appuser");

        assertThat(json.get("usertype"))
                .as("usertype should be agent")
                .isEqualTo("agent");

        assertThat((String) json.get("scope"))
                .as("scope should contain agent:commands")
                .contains("agent:commands");

        System.out.println("[Test] /api/whoami response: " + json.toJSONString());
    }

    @Test
    @Order(3)
    @DisplayName("Call Biz Service /api/commands with valid token")
    void testBizServiceGetCommands() throws Exception {
        // Given: Valid access token
        assertThat(accessToken).isNotNull();

        // When: Call /api/commands
        HttpResponse response = httpGet(BIZ_SERVICE_URL + "/api/commands", accessToken);

        // Then: Should return commands list
        assertThat(response.statusCode)
                .as("Response status should be 200 OK")
                .isEqualTo(200);

        JSONObject json = parseJson(response.body);

        assertThat(json.get("agent_id"))
                .as("agent_id should be present")
                .isEqualTo("testserver01_appuser_J");

        assertThat(json.get("commands"))
                .as("commands array should be present")
                .isNotNull();

        JSONArray commands = (JSONArray) json.get("commands");
        assertThat(commands.size())
                .as("Should have at least one command")
                .isGreaterThan(0);

        System.out.println("[Test] /api/commands returned " + commands.size() + " commands");
    }

    @Test
    @Order(4)
    @DisplayName("Call Biz Service /api/results with valid token (POST)")
    void testBizServicePostResults() throws Exception {
        // Given: Valid access token and result data
        assertThat(accessToken).isNotNull();

        String resultJson = "{\"command_id\": \"cmd-001\", \"status\": \"completed\", \"result\": \"success\"}";

        // When: POST to /api/results
        HttpResponse response = httpPost(BIZ_SERVICE_URL + "/api/results", accessToken, resultJson);

        // Then: Should accept the result
        assertThat(response.statusCode)
                .as("Response status should be 200 OK")
                .isEqualTo(200);

        JSONObject json = parseJson(response.body);

        assertThat(json.get("status"))
                .as("status should be accepted")
                .isEqualTo("accepted");

        assertThat(json.get("command_id"))
                .as("command_id should match")
                .isEqualTo("cmd-001");

        System.out.println("[Test] /api/results response: " + json.toJSONString());
    }

    @Test
    @Order(5)
    @DisplayName("Call Biz Service /api/config with valid token")
    void testBizServiceGetConfig() throws Exception {
        // Given: Valid access token
        assertThat(accessToken).isNotNull();

        // When: Call /api/config
        HttpResponse response = httpGet(BIZ_SERVICE_URL + "/api/config", accessToken);

        // Then: Should return config
        assertThat(response.statusCode)
                .as("Response status should be 200 OK")
                .isEqualTo(200);

        JSONObject json = parseJson(response.body);

        assertThat(json.get("config"))
                .as("config object should be present")
                .isNotNull();

        System.out.println("[Test] /api/config response received");
    }

    // ==========================================================================
    // Token Validation Error Tests
    // ==========================================================================

    @Test
    @Order(10)
    @DisplayName("Biz Service rejects request without token")
    void testBizServiceRejectsNoToken() throws Exception {
        // When: Call without Authorization header
        HttpResponse response = httpGet(BIZ_SERVICE_URL + "/api/commands", null);

        // Then: Should return 401 Unauthorized
        assertThat(response.statusCode)
                .as("Response status should be 401 Unauthorized")
                .isEqualTo(401);

        JSONObject json = parseJson(response.body);

        assertThat(json.get("error"))
                .as("error should be missing_token")
                .isEqualTo("missing_token");

        System.out.println("[Test] No token rejected: " + json.get("error"));
    }

    @Test
    @Order(11)
    @DisplayName("Biz Service rejects invalid token")
    void testBizServiceRejectsInvalidToken() throws Exception {
        // When: Call with invalid token
        HttpResponse response = httpGet(BIZ_SERVICE_URL + "/api/commands", "invalid-token-here");

        // Then: Should return 401 Unauthorized
        assertThat(response.statusCode)
                .as("Response status should be 401 Unauthorized")
                .isEqualTo(401);

        JSONObject json = parseJson(response.body);

        assertThat(json.get("error"))
                .as("error should be invalid_token")
                .isEqualTo("invalid_token");

        System.out.println("[Test] Invalid token rejected: " + json.get("error_description"));
    }

    @Test
    @Order(12)
    @DisplayName("Biz Service rejects expired token")
    void testBizServiceRejectsExpiredToken() throws Exception {
        // Given: An expired token (manually crafted or from test endpoint)
        // This is a token with exp in the past
        String expiredToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
                "eyJzdWIiOiJ0ZXN0IiwiZXhwIjoxMDAwMDAwMDAwfQ." +
                "invalid_signature";

        // When: Call with expired token
        HttpResponse response = httpGet(BIZ_SERVICE_URL + "/api/commands", expiredToken);

        // Then: Should return 401 Unauthorized
        assertThat(response.statusCode)
                .as("Response status should be 401 Unauthorized")
                .isEqualTo(401);

        System.out.println("[Test] Expired/invalid token rejected");
    }

    // ==========================================================================
    // End-to-End Flow Test
    // ==========================================================================

    @Test
    @Order(20)
    @DisplayName("E2E: Complete flow - get token, fetch commands, submit results")
    void testEndToEndFlow() throws Exception {
        // Step 1: Get fresh token from Auth Server
        config.setServer_url(AUTH_SERVER_URL);
        config.setRefresh_token(REFRESH_TOKEN);

        int tokenResult = Common.updateToken();
        assertThat(tokenResult).isEqualTo(1);

        String token = config.getAccess_token();
        System.out.println("[E2E] Step 1: Token acquired");

        // Step 2: Fetch commands from Biz Service
        HttpResponse commandsResponse = httpGet(BIZ_SERVICE_URL + "/api/commands", token);
        assertThat(commandsResponse.statusCode).isEqualTo(200);

        JSONObject commandsJson = parseJson(commandsResponse.body);
        JSONArray commands = (JSONArray) commandsJson.get("commands");
        System.out.println("[E2E] Step 2: Fetched " + commands.size() + " commands");

        // Step 3: Process each command and submit results
        for (Object cmd : commands) {
            JSONObject command = (JSONObject) cmd;
            String commandId = (String) command.get("command_id");

            // Simulate command execution
            String resultJson = String.format(
                    "{\"command_id\": \"%s\", \"status\": \"completed\", \"result\": \"executed successfully\"}",
                    commandId
            );

            HttpResponse resultResponse = httpPost(BIZ_SERVICE_URL + "/api/results", token, resultJson);
            assertThat(resultResponse.statusCode).isEqualTo(200);

            System.out.println("[E2E] Step 3: Submitted result for " + commandId);
        }

        System.out.println("[E2E] Complete flow succeeded!");
    }

    // ==========================================================================
    // Using Common.httpGET to test integration with existing code
    // ==========================================================================

    @Test
    @Order(30)
    @DisplayName("Test Common.httpGET with Biz Service")
    void testCommonHttpGetWithBizService() {
        // Given: Configure to use Biz Service URL
        String originalUrl = config.getServer_url();
        config.setServer_url(BIZ_SERVICE_URL);

        try {
            // When: Use Common.httpGET to call Biz Service
            MwResponseVO response = Common.httpGET("/api/whoami", accessToken);

            // Then: Should get successful response
            assertThat(response.getStatusCode())
                    .as("Status code should be 200")
                    .isEqualTo(200);

            assertThat(response.getResponse())
                    .as("Response should contain agent_id")
                    .isNotNull();

            JSONObject json = response.getResponse();
            assertThat(json.get("agent_id"))
                    .isEqualTo("testserver01_appuser_J");

            System.out.println("[Test] Common.httpGET with Biz Service succeeded");

        } finally {
            // Restore original URL
            config.setServer_url(originalUrl);
        }
    }

    // ==========================================================================
    // Helper Methods
    // ==========================================================================

    private static boolean isServiceRunning(String healthUrl) {
        try {
            URL url = new URL(healthUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            int status = conn.getResponseCode();
            conn.disconnect();
            return status == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private HttpResponse httpGet(String urlString, String token) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Content-Type", "application/json");

        if (token != null) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }

        int status = conn.getResponseCode();
        String body = readResponse(conn);
        conn.disconnect();

        return new HttpResponse(status, body);
    }

    private HttpResponse httpPost(String urlString, String token, String jsonBody) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        if (token != null) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int status = conn.getResponseCode();
        String body = readResponse(conn);
        conn.disconnect();

        return new HttpResponse(status, body);
    }

    private String readResponse(HttpURLConnection conn) throws Exception {
        BufferedReader reader;
        if (conn.getResponseCode() >= 400) {
            reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
        } else {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        }

        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        return response.toString();
    }

    private JSONObject parseJson(String json) throws Exception {
        org.json.simple.parser.JSONParser parser = new org.json.simple.parser.JSONParser();
        return (JSONObject) parser.parse(json);
    }

    private static class HttpResponse {
        final int statusCode;
        final String body;

        HttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }
}
