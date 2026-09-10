package mwagent.infrastructure.config;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Mock implementation of ConfigurationProvider for testing.
 * Allows tests to configure behavior without depending on real Config.
 */
public class MockConfigurationProvider implements ConfigurationProvider {

    private final Map<String, String> stringConfig = new HashMap<>();
    private final Map<String, Integer> intConfig = new HashMap<>();
    private final Map<String, Boolean> booleanConfig = new HashMap<>();
    private final Map<String, String> env = new HashMap<>();

    private String agentId = "test-agent-001";
    private String agentVersion = "0000.0000.0001-TEST";
    private String hostname = "test-host";
    private String username = "test-user";
    private String os = "TEST";
    private String agentType = "JAVAAGENT";
    private String serverUrl = "http://localhost:8080";
    private String commandUri = "/api/v1/commands";
    private String resultUri = "/api/v1/results";
    private String agentUri = "/api/v1/agents";
    private int commandCheckCycle = 60;

    private String accessToken = "";
    private String refreshToken = "";

    private String kafkaBrokerAddress = "";
    private boolean mtlsEnabled = false;
    private String keystorePath = "";
    private String keystorePassword = "";
    private String truststorePath = "";
    private String truststorePassword = "";

    private boolean commandInjectionCheckEnabled = false;
    private boolean pathTraversalCheckEnabled = true;

    private Logger logger = Logger.getLogger("MockConfig");

    // Builder-style setters for fluent configuration
    public MockConfigurationProvider withAgentId(String agentId) {
        this.agentId = agentId;
        return this;
    }

    public MockConfigurationProvider withServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
        return this;
    }

    public MockConfigurationProvider withMtlsEnabled(boolean enabled) {
        this.mtlsEnabled = enabled;
        return this;
    }

    public MockConfigurationProvider withKafkaBrokerAddress(String address) {
        this.kafkaBrokerAddress = address;
        return this;
    }

    public MockConfigurationProvider withAccessToken(String token) {
        this.accessToken = token;
        return this;
    }

    public MockConfigurationProvider withRefreshToken(String token) {
        this.refreshToken = token;
        return this;
    }

    public MockConfigurationProvider withOs(String os) {
        this.os = os;
        return this;
    }

    public MockConfigurationProvider withLogger(Logger logger) {
        this.logger = logger;
        return this;
    }

    public MockConfigurationProvider withStringConfig(String key, String value) {
        stringConfig.put(key, value);
        return this;
    }

    public MockConfigurationProvider withIntConfig(String key, int value) {
        intConfig.put(key, value);
        return this;
    }

    public MockConfigurationProvider withBooleanConfig(String key, boolean value) {
        booleanConfig.put(key, value);
        return this;
    }

    public MockConfigurationProvider withMtls(String keystorePath, String keystorePassword,
                                               String truststorePath, String truststorePassword) {
        this.mtlsEnabled = true;
        this.keystorePath = keystorePath;
        this.keystorePassword = keystorePassword;
        this.truststorePath = truststorePath;
        this.truststorePassword = truststorePassword;
        return this;
    }

    // ConfigurationProvider implementation
    @Override
    public String getString(String key) {
        return stringConfig.get(key);
    }

    @Override
    public String getString(String key, String defaultValue) {
        return stringConfig.getOrDefault(key, defaultValue);
    }

    @Override
    public int getInt(String key) {
        return intConfig.getOrDefault(key, 0);
    }

    @Override
    public int getInt(String key, int defaultValue) {
        return intConfig.getOrDefault(key, defaultValue);
    }

    @Override
    public boolean getBoolean(String key) {
        return booleanConfig.getOrDefault(key, false);
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        return booleanConfig.getOrDefault(key, defaultValue);
    }

    @Override
    public String getAgentId() {
        return agentId;
    }

    @Override
    public String getAgentVersion() {
        return agentVersion;
    }

    @Override
    public String getHostname() {
        return hostname;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getOs() {
        return os;
    }

    @Override
    public String getAgentType() {
        return agentType;
    }

    @Override
    public String getServerUrl() {
        return serverUrl;
    }

    @Override
    public String getCommandUri() {
        return commandUri;
    }

    @Override
    public String getResultUri() {
        return resultUri;
    }

    @Override
    public String getAgentUri() {
        return agentUri;
    }

    @Override
    public int getCommandCheckCycle() {
        return commandCheckCycle;
    }

    @Override
    public String getAccessToken() {
        return accessToken;
    }

    @Override
    public void setAccessToken(String token) {
        this.accessToken = token;
    }

    @Override
    public String getRefreshToken() {
        return refreshToken;
    }

    @Override
    public void setRefreshToken(String token) {
        this.refreshToken = token;
    }

    @Override
    public boolean isKafkaEnabled() {
        return kafkaBrokerAddress != null && !kafkaBrokerAddress.isEmpty();
    }

    @Override
    public String getKafkaBrokerAddress() {
        return kafkaBrokerAddress;
    }

    @Override
    public void setKafkaBrokerAddress(String address) {
        this.kafkaBrokerAddress = address;
    }

    @Override
    public boolean isMtlsEnabled() {
        return mtlsEnabled;
    }

    @Override
    public String getKeystorePath() {
        return keystorePath;
    }

    @Override
    public String getKeystorePassword() {
        return keystorePassword;
    }

    @Override
    public String getTruststorePath() {
        return truststorePath;
    }

    @Override
    public String getTruststorePassword() {
        return truststorePassword;
    }

    @Override
    public boolean isCommandInjectionCheckEnabled() {
        return commandInjectionCheckEnabled;
    }

    @Override
    public boolean isPathTraversalCheckEnabled() {
        return pathTraversalCheckEnabled;
    }

    @Override
    public Map<String, String> getEnvironment() {
        return env;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public void updateProperty(String key, String value) {
        stringConfig.put(key, value);
    }
}
