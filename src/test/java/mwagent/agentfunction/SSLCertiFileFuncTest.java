package mwagent.agentfunction;

import mwagent.common.Config;
import mwagent.vo.CommandVO;
import mwagent.vo.ResultVO;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for SSLCertiFileFunc - Local certificate file parsing
 *
 * This test validates reading certificate information from local files:
 * - .crt files (DER or PEM encoded)
 * - .pem files (PEM encoded with BEGIN/END markers)
 */
@DisplayName("SSLCertiFileFunc Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SSLCertiFileFuncTest {

    private static final String CERTS_DIR = "./test-server/certs/";
    private static final String CA_CERT = CERTS_DIR + "ca.crt";
    private static final String SERVER_CERT = CERTS_DIR + "server.crt";
    private static final String AGENT_CERT = CERTS_DIR + "agent-test001.crt";

    private SSLCertiFileFunc func;

    @BeforeAll
    static void setup() {
        Config.getConfig().setLogger(Logger.getLogger("SSLCertiFileFuncTest"));
    }

    @BeforeEach
    void beforeEach() {
        func = new SSLCertiFileFunc();
    }

    @Test
    @Order(1)
    @DisplayName("Read CA certificate file")
    void testReadCACertificate() throws Exception {
        // Skip if cert file doesn't exist
        assumeCertExists(CA_CERT);

        // Given
        CommandVO command = createCommand(CA_CERT);

        // When
        ArrayList<ResultVO> results = func.exeCommand(command);

        // Then
        assertThat(results).hasSize(1);

        ResultVO result = results.get(0);
        assertThat(result.isOk()).isTrue();

        JSONObject json = parseJson(result.getResult());

        assertThat(json.get("certifile"))
                .as("certifile should contain the file path")
                .isNotNull();

        assertThat(json.get("subject"))
                .as("subject should contain CA info")
                .isNotNull();

        assertThat((String) json.get("subject"))
                .as("subject should contain Test CA")
                .contains("Test CA");

        assertThat(json.get("issuer"))
                .as("issuer should be present")
                .isNotNull();

        assertThat(json.get("notafter"))
                .as("notafter (expiry date) should be present")
                .isNotNull();

        assertThat(json.get("notbefore"))
                .as("notbefore (start date) should be present")
                .isNotNull();

        assertThat(json.get("serial"))
                .as("serial number should be present")
                .isNotNull();

        System.out.println("[CA Cert] Subject: " + json.get("subject"));
        System.out.println("[CA Cert] Issuer: " + json.get("issuer"));
        System.out.println("[CA Cert] Valid: " + json.get("notbefore") + " to " + json.get("notafter"));
    }

    @Test
    @Order(2)
    @DisplayName("Read Server certificate file")
    void testReadServerCertificate() throws Exception {
        // Skip if cert file doesn't exist
        assumeCertExists(SERVER_CERT);

        // Given
        CommandVO command = createCommand(SERVER_CERT);

        // When
        ArrayList<ResultVO> results = func.exeCommand(command);

        // Then
        assertThat(results).hasSize(1);

        ResultVO result = results.get(0);
        assertThat(result.isOk()).isTrue();

        JSONObject json = parseJson(result.getResult());

        assertThat((String) json.get("subject"))
                .as("subject should contain localhost")
                .contains("localhost");

        System.out.println("[Server Cert] Subject: " + json.get("subject"));
    }

    @Test
    @Order(3)
    @DisplayName("Read Agent client certificate file")
    void testReadAgentCertificate() throws Exception {
        // Skip if cert file doesn't exist
        assumeCertExists(AGENT_CERT);

        // Given
        CommandVO command = createCommand(AGENT_CERT);

        // When
        ArrayList<ResultVO> results = func.exeCommand(command);

        // Then
        assertThat(results).hasSize(1);

        ResultVO result = results.get(0);
        assertThat(result.isOk()).isTrue();

        JSONObject json = parseJson(result.getResult());

        String subject = (String) json.get("subject");
        assertThat(subject)
                .as("subject should contain agent DN format")
                .contains("testserver01_appuser_J");

        assertThat(subject)
                .as("subject should contain OU=agent")
                .containsIgnoringCase("OU=agent");

        System.out.println("[Agent Cert] Subject: " + subject);
        System.out.println("[Agent Cert] Issuer: " + json.get("issuer"));
    }

    @Test
    @Order(4)
    @DisplayName("Handle non-existent file gracefully")
    void testNonExistentFile() throws Exception {
        // Given
        CommandVO command = createCommand("/path/to/nonexistent/cert.crt");

        // When
        ArrayList<ResultVO> results = func.exeCommand(command);

        // Then
        assertThat(results).hasSize(1);

        ResultVO result = results.get(0);
        assertThat(result.isOk()).isFalse();
        assertThat(result.getResult())
                .as("Should indicate file not found")
                .contains("FileNotFoundException");

        System.out.println("[Non-existent File] Result: " + result.getResult());
    }

    @Test
    @Order(5)
    @DisplayName("Handle invalid certificate file gracefully")
    void testInvalidCertificateFile() throws Exception {
        // Given: Create a temp file with invalid content
        Path tempFile = Files.createTempFile("invalid-cert", ".crt");
        Files.write(tempFile, "This is not a valid certificate".getBytes());

        try {
            CommandVO command = createCommand(tempFile.toString());

            // When
            ArrayList<ResultVO> results = func.exeCommand(command);

            // Then
            assertThat(results).hasSize(1);

            ResultVO result = results.get(0);
            assertThat(result.isOk()).isFalse();
            assertThat(result.getResult())
                    .as("Should indicate certificate exception")
                    .contains("CertificateException");

            System.out.println("[Invalid File] Result: " + result.getResult());

        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @Order(6)
    @DisplayName("Read all certificates in test-server/certs directory")
    void testReadAllCertificates() throws Exception {
        // Given: All .crt files in certs directory
        File certsDir = new File(CERTS_DIR);
        if (!certsDir.exists()) {
            System.out.println("[SKIP] Certs directory not found: " + CERTS_DIR);
            return;
        }

        File[] certFiles = certsDir.listFiles((dir, name) -> name.endsWith(".crt"));
        if (certFiles == null || certFiles.length == 0) {
            System.out.println("[SKIP] No certificate files found");
            return;
        }

        System.out.println("\n=== Reading all certificates in " + CERTS_DIR + " ===\n");

        for (File certFile : certFiles) {
            CommandVO command = createCommand(certFile.getAbsolutePath());

            // When
            ArrayList<ResultVO> results = func.exeCommand(command);

            // Then
            assertThat(results).hasSize(1);
            ResultVO result = results.get(0);

            if (result.isOk()) {
                JSONObject json = parseJson(result.getResult());
                System.out.println("File: " + certFile.getName());
                System.out.println("  Subject: " + json.get("subject"));
                System.out.println("  Issuer: " + json.get("issuer"));
                System.out.println("  Valid until: " + json.get("notafter"));
                System.out.println();
            } else {
                System.out.println("File: " + certFile.getName() + " - Failed: " + result.getResult());
            }
        }
    }

    // ==========================================================================
    // Helper Methods
    // ==========================================================================

    private CommandVO createCommand(String certPath) {
        CommandVO command = new CommandVO();
        command.setAdditionalParams(certPath);
        command.setHostName("testhost");
        command.setTargetFileName("cert-check");
        command.setTargetFilePath("/tmp");
        return command;
    }

    private JSONObject parseJson(String json) throws Exception {
        JSONParser parser = new JSONParser();
        return (JSONObject) parser.parse(json);
    }

    private void assumeCertExists(String path) {
        Path certPath = Paths.get(path);
        if (!Files.exists(certPath)) {
            System.out.println("[SKIP] Certificate file not found: " + path);
            Assumptions.assumeTrue(false, "Certificate file not found: " + path);
        }
    }
}
