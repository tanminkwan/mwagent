package mwagent.agentfunction;

import mwagent.common.Config;
import mwagent.vo.CommandVO;
import mwagent.vo.ResultVO;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for SSLCertiFunc - SSL certificate inspection via local proxy
 *
 * SSLCertiFunc connects to 127.0.0.1 with SNI (Server Name Indication) to
 * inspect SSL certificates. This design allows certificate checking through
 * a local proxy server.
 *
 * Prerequisites for integration tests:
 * 1. Mock server running with SSL: python test-server/mock_server.py --ssl
 * 2. Set environment variable: SSL_CERT_INTEGRATION_TEST=true
 *
 * To run:
 *   # Terminal 1: Start SSL server
 *   cd test-server && python mock_server.py --ssl
 *
 *   # Terminal 2: Run tests
 *   set SSL_CERT_INTEGRATION_TEST=true
 *   mvn test -Dtest=SSLCertiFuncTest
 */
@DisplayName("SSLCertiFunc Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SSLCertiFuncTest {

    private SSLCertiFunc func;

    @BeforeAll
    static void setup() {
        Config.getConfig().setLogger(Logger.getLogger("SSLCertiFuncTest"));
    }

    @BeforeEach
    void beforeEach() {
        func = new SSLCertiFunc();
    }

    // ==========================================================================
    // Unit Tests - Helper Methods (Always Run)
    // ==========================================================================

    @Test
    @Order(1)
    @DisplayName("getDomainNPort: Parse domain with explicit port")
    void testGetDomainNPort_WithPort() {
        // When
        String[] result = func.getDomainNPort("example.com:8443");

        // Then
        assertThat(result[0]).isEqualTo("example.com");
        assertThat(result[1]).isEqualTo("8443");
    }

    @Test
    @Order(2)
    @DisplayName("getDomainNPort: Parse domain without port (default 443)")
    void testGetDomainNPort_WithoutPort() {
        // When
        String[] result = func.getDomainNPort("example.com");

        // Then
        assertThat(result[0]).isEqualTo("example.com");
        assertThat(result[1]).isEqualTo("443");
    }

    @Test
    @Order(3)
    @DisplayName("getDomainNPort: Parse domain with standard HTTPS port")
    void testGetDomainNPort_Port443() {
        // When
        String[] result = func.getDomainNPort("secure.example.com:443");

        // Then
        assertThat(result[0]).isEqualTo("secure.example.com");
        assertThat(result[1]).isEqualTo("443");
    }

    @Test
    @Order(4)
    @DisplayName("printValidCerts: Format empty certificate array as JSON")
    void testPrintValidCerts_EmptyArray() throws Exception {
        // When: Call with empty array
        ResultVO result = func.printValidCerts("test.example.com", new X509Certificate[0]);

        // Then
        assertThat(result.isOk()).isTrue();

        JSONObject json = parseJson(result.getResult());
        assertThat(json.get("domain")).isEqualTo("test.example.com");
        assertThat(json.get("certs")).isInstanceOf(JSONArray.class);

        JSONArray certs = (JSONArray) json.get("certs");
        assertThat(certs).isEmpty();

        System.out.println("[Unit] printValidCerts with empty array: OK");
    }

    // ==========================================================================
    // Integration Tests - via exeCommand (requires mock_server --ssl on 8443)
    // ==========================================================================

    @Test
    @Order(10)
    @EnabledIfEnvironmentVariable(named = "SSL_CERT_INTEGRATION_TEST", matches = "true")
    @DisplayName("exeCommand: Check localhost:8443 SSL certificate")
    void testExeCommand_Localhost8443() throws Exception {
        // Given: localhost:8443 (mock_server with --ssl)
        // exeCommand connects to 127.0.0.1 with SNI=localhost
        CommandVO command = createCommand("localhost:8443");

        // When
        ArrayList<ResultVO> results = func.exeCommand(command);

        // Then
        assertThat(results).hasSize(1);

        ResultVO result = results.get(0);
        assertThat(result.isOk())
                .as("Should successfully retrieve certificate from localhost:8443")
                .isTrue();

        JSONObject json = parseJson(result.getResult());
        assertThat(json.get("domain")).isEqualTo("localhost:8443");

        JSONArray certs = (JSONArray) json.get("certs");
        assertThat(certs)
                .as("Should have at least one certificate")
                .isNotEmpty();

        JSONObject firstCert = (JSONObject) certs.get(0);
        String subject = (String) firstCert.get("subject");

        assertThat(subject)
                .as("Subject should contain localhost")
                .contains("localhost");

        System.out.println("\n=== localhost:8443 via exeCommand ===");
        System.out.println("Subject: " + firstCert.get("subject"));
        System.out.println("Issuer: " + firstCert.get("issuer"));
        System.out.println("Valid until: " + firstCert.get("notafter"));
    }

    // Note: Wildcard domain (*) test is not possible via exeCommand or checkSSLCertificate
    // because * is used as SNI hostname which requires valid LDH ASCII characters.
    // The wildcard matching in isCertificateValidForDomain (if domain.equals("*")) would work
    // for filtering, but the current implementation uses the same domain for both SNI and filtering.

    @Test
    @Order(12)
    @EnabledIfEnvironmentVariable(named = "SSL_CERT_INTEGRATION_TEST", matches = "true")
    @DisplayName("exeCommand: Verify certificate details")
    void testExeCommand_VerifyCertificateDetails() throws Exception {
        // Given
        CommandVO command = createCommand("localhost:8443");

        // When
        ArrayList<ResultVO> results = func.exeCommand(command);

        // Then
        assertThat(results).hasSize(1);

        ResultVO result = results.get(0);
        assertThat(result.isOk()).isTrue();

        JSONObject json = parseJson(result.getResult());
        JSONArray certs = (JSONArray) json.get("certs");
        assertThat(certs).isNotEmpty();

        JSONObject cert = (JSONObject) certs.get(0);

        // Verify all expected fields are present
        assertThat(cert.get("index")).as("index").isNotNull();
        assertThat(cert.get("subject")).as("subject").isNotNull();
        assertThat(cert.get("issuer")).as("issuer").isNotNull();
        assertThat(cert.get("notbefore")).as("notbefore").isNotNull();
        assertThat(cert.get("notafter")).as("notafter").isNotNull();
        assertThat(cert.get("serial")).as("serial").isNotNull();

        // Verify certificate is from our test CA
        String issuer = (String) cert.get("issuer");
        assertThat(issuer)
                .as("Issuer should be Leebalso Test CA")
                .contains("Leebalso");

        System.out.println("\n=== Certificate Details ===");
        System.out.println("Index: " + cert.get("index"));
        System.out.println("Subject: " + cert.get("subject"));
        System.out.println("Issuer: " + cert.get("issuer"));
        System.out.println("Serial: " + cert.get("serial"));
        System.out.println("Not Before: " + cert.get("notbefore"));
        System.out.println("Not After: " + cert.get("notafter"));
    }

    @Test
    @Order(13)
    @EnabledIfEnvironmentVariable(named = "SSL_CERT_INTEGRATION_TEST", matches = "true")
    @DisplayName("exeCommand: Connection to non-existent port returns empty certs")
    void testExeCommand_NonExistentPort() throws Exception {
        // Given: Port 9999 should not have SSL server
        CommandVO command = createCommand("localhost:9999");

        // When
        ArrayList<ResultVO> results = func.exeCommand(command);

        // Then: Should return OK with empty certs (not throw exception)
        assertThat(results).hasSize(1);

        ResultVO result = results.get(0);
        assertThat(result.isOk()).isTrue();

        JSONObject json = parseJson(result.getResult());
        JSONArray certs = (JSONArray) json.get("certs");
        assertThat(certs)
                .as("Should return empty certs for non-existent port")
                .isEmpty();

        System.out.println("[Non-existent Port] Result: empty certs (expected)");
    }

    @Test
    @Order(14)
    @EnabledIfEnvironmentVariable(named = "SSL_CERT_INTEGRATION_TEST", matches = "true")
    @DisplayName("exeCommand: Domain mismatch returns empty certs for that domain")
    void testExeCommand_DomainMismatch() throws Exception {
        // Given: Request cert for "google.com" but server has "localhost" cert
        // exeCommand connects to 127.0.0.1:8443 with SNI=google.com
        CommandVO command = createCommand("google.com:8443");

        // When
        ArrayList<ResultVO> results = func.exeCommand(command);

        // Then: Should return OK but with empty certs (domain doesn't match)
        assertThat(results).hasSize(1);

        ResultVO result = results.get(0);
        assertThat(result.isOk()).isTrue();

        JSONObject json = parseJson(result.getResult());
        JSONArray certs = (JSONArray) json.get("certs");

        // The server cert is for "localhost", not "google.com"
        // So isCertificateValidForDomain should filter it out
        assertThat(certs)
                .as("Should return empty certs when domain doesn't match certificate")
                .isEmpty();

        System.out.println("[Domain Mismatch] google.com:8443 -> empty certs (expected)");
    }

    // ==========================================================================
    // checkSSLCertificate Direct Tests (for unit testing the method)
    // ==========================================================================

    @Test
    @Order(20)
    @EnabledIfEnvironmentVariable(named = "SSL_CERT_INTEGRATION_TEST", matches = "true")
    @DisplayName("checkSSLCertificate: Direct call to 127.0.0.1:8443")
    void testCheckSSLCertificate_Direct() {
        // Given: Direct call like exeCommand does internally
        String domain = "localhost";
        String ip = "127.0.0.1";
        int port = 8443;

        // When
        X509Certificate[] certs = func.checkSSLCertificate(domain, ip, port);

        // Then
        assertThat(certs)
                .as("Should retrieve certificates from local SSL server")
                .isNotEmpty();

        X509Certificate cert = certs[0];
        System.out.println("\n=== checkSSLCertificate Direct ===");
        System.out.println("Subject: " + cert.getSubjectDN().getName());
        System.out.println("Issuer: " + cert.getIssuerDN().getName());
        System.out.println("Valid: " + cert.getNotBefore() + " to " + cert.getNotAfter());
    }

    // ==========================================================================
    // Helper Methods
    // ==========================================================================

    private CommandVO createCommand(String domainParam) {
        CommandVO command = new CommandVO();
        command.setAdditionalParams(domainParam);
        command.setHostName("testhost");
        command.setTargetFileName("ssl-check");
        command.setTargetFilePath("/tmp");
        return command;
    }

    private JSONObject parseJson(String json) throws Exception {
        JSONParser parser = new JSONParser();
        return (JSONObject) parser.parse(json);
    }
}
