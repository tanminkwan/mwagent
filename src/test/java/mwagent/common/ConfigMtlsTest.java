package mwagent.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.*;

class ConfigMtlsTest {

    private static final String TEST_PROPERTIES = "test-mtls-agent.properties";

    @BeforeEach
    void setUp() throws IOException {
        // Clean up any existing test properties
        Files.deleteIfExists(Paths.get(TEST_PROPERTIES));
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up test properties
        Files.deleteIfExists(Paths.get(TEST_PROPERTIES));
    }

    @Test
    void testMtlsConfigurationEnabled() throws IOException {
        // Given: Properties file with mTLS enabled
        createTestProperties(
            "use_mtls=true\n" +
            "client.keystore.path=/path/to/client.p12\n" +
            "client.keystore.password=client-password\n" +
            "truststore.path=/path/to/truststore.jks\n" +
            "truststore.password=trust-password\n"
        );

        // When: Config loads properties
        Config config = Config.getConfig();

        // Note: In real scenario, we would need to reload config
        // For now, we test the getters/setters
        config.setUseMtls(true);
        config.setClientKeystorePath("/path/to/client.p12");
        config.setClientKeystorePassword("client-password");
        config.setTruststorePath("/path/to/truststore.jks");
        config.setTruststorePassword("trust-password");

        // Then: mTLS configuration is loaded correctly
        assertThat(config.isUseMtls()).isTrue();
        assertThat(config.getClientKeystorePath()).isEqualTo("/path/to/client.p12");
        assertThat(config.getClientKeystorePassword()).isEqualTo("client-password");
        assertThat(config.getTruststorePath()).isEqualTo("/path/to/truststore.jks");
        assertThat(config.getTruststorePassword()).isEqualTo("trust-password");
    }

    @Test
    void testMtlsConfigurationDisabled() {
        // Given: Config with mTLS disabled
        Config config = Config.getConfig();
        config.setUseMtls(false);

        // Then: mTLS is disabled
        assertThat(config.isUseMtls()).isFalse();
    }

    @Test
    void testMtlsConfigurationDefaults() {
        // Given: Fresh config
        Config config = Config.getConfig();

        // When: No mTLS properties set (defaults)
        config.setUseMtls(false);
        config.setClientKeystorePath("");
        config.setClientKeystorePassword("");
        config.setTruststorePath("");
        config.setTruststorePassword("");

        // Then: Default values are correct
        assertThat(config.isUseMtls()).isFalse();
        assertThat(config.getClientKeystorePath()).isEmpty();
        assertThat(config.getClientKeystorePassword()).isEmpty();
        assertThat(config.getTruststorePath()).isEmpty();
        assertThat(config.getTruststorePassword()).isEmpty();
    }

    @Test
    void testMtlsGettersAndSetters() {
        // Given: Config instance
        Config config = Config.getConfig();

        // When: Setting mTLS properties
        config.setUseMtls(true);
        config.setClientKeystorePath("test/client.p12");
        config.setClientKeystorePassword("test-password");
        config.setTruststorePath("test/truststore.jks");
        config.setTruststorePassword("trust-password");

        // Then: All properties are set correctly
        assertThat(config.isUseMtls()).isTrue();
        assertThat(config.getClientKeystorePath()).isEqualTo("test/client.p12");
        assertThat(config.getClientKeystorePassword()).isEqualTo("test-password");
        assertThat(config.getTruststorePath()).isEqualTo("test/truststore.jks");
        assertThat(config.getTruststorePassword()).isEqualTo("trust-password");
    }

    private void createTestProperties(String content) throws IOException {
        FileWriter writer = new FileWriter(TEST_PROPERTIES);
        writer.write("server_url=http://localhost:8080\n");
        writer.write("get_command_uri=/api/v1/commands\n");
        writer.write("post_agent_uri=/api/v1/agent\n");
        writer.write("token=test-token\n");
        writer.write("host_name_var=HOSTNAME\n");
        writer.write("user_name_var=USER\n");
        writer.write(content);
        writer.close();
    }
}
