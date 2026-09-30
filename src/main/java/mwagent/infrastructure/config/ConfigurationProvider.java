package mwagent.infrastructure.config;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Configuration provider interface for dependency injection.
 * Abstracts configuration access to enable testing and flexibility.
 */
public interface ConfigurationProvider {

    // ========== Generic Configuration Access ==========

    /**
     * Get a string configuration value.
     * @param key configuration key
     * @return value or null if not found
     */
    String getString(String key);

    /**
     * Get a string configuration value with default.
     * @param key configuration key
     * @param defaultValue default value if not found
     * @return value or defaultValue
     */
    String getString(String key, String defaultValue);

    /**
     * Get an integer configuration value.
     * @param key configuration key
     * @return value or 0 if not found
     */
    int getInt(String key);

    /**
     * Get an integer configuration value with default.
     * @param key configuration key
     * @param defaultValue default value if not found
     * @return value or defaultValue
     */
    int getInt(String key, int defaultValue);

    /**
     * Get a boolean configuration value.
     * @param key configuration key
     * @return value or false if not found
     */
    boolean getBoolean(String key);

    /**
     * Get a boolean configuration value with default.
     * @param key configuration key
     * @param defaultValue default value if not found
     * @return value or defaultValue
     */
    boolean getBoolean(String key, boolean defaultValue);

    // ========== Agent Information ==========

    /**
     * Get the unique agent identifier.
     */
    String getAgentId();

    /**
     * Get the agent version.
     */
    String getAgentVersion();

    /**
     * Get the hostname where agent is running.
     */
    String getHostname();

    /**
     * Get the username under which agent is running.
     */
    String getUsername();

    /**
     * Get the operating system name.
     */
    String getOs();

    /**
     * Get the agent type.
     */
    String getAgentType();

    // ========== Server Configuration ==========

    /**
     * Get the base server URL.
     */
    String getServerUrl();

    /**
     * Get the URI for fetching commands.
     */
    String getCommandUri();

    /**
     * Get the URI for posting agent results.
     */
    String getResultUri();

    /**
     * Get the URI for agent registration.
     */
    String getAgentUri();

    /**
     * Get the command check cycle in milliseconds.
     */
    int getCommandCheckCycle();

    // ========== Token Management ==========

    /**
     * Get the current access token.
     */
    String getAccessToken();

    /**
     * Set the access token.
     */
    void setAccessToken(String token);

    /**
     * Get the current refresh token.
     */
    String getRefreshToken();

    /**
     * Set the refresh token.
     */
    void setRefreshToken(String token);

    // ========== mTLS Configuration ==========

    /**
     * Check if mTLS is enabled.
     */
    boolean isMtlsEnabled();

    /**
     * Get the client keystore path (PKCS12).
     */
    String getKeystorePath();

    /**
     * Get the client keystore password.
     */
    String getKeystorePassword();

    /**
     * Get the truststore path (JKS).
     */
    String getTruststorePath();

    /**
     * Get the truststore password.
     */
    String getTruststorePassword();

    /**
     * Check if HTTPS server certificates are verified (chain + hostname).
     * false (default) trusts any certificate.
     */
    boolean isSslVerify();

    // ========== Security Configuration ==========

    /**
     * Check if command injection validation is enabled.
     */
    boolean isCommandInjectionCheckEnabled();

    /**
     * Check if path traversal validation is enabled.
     */
    boolean isPathTraversalCheckEnabled();

    // ========== Environment & Logging ==========

    /**
     * Get environment variables map.
     */
    Map<String, String> getEnvironment();

    /**
     * Get the logger instance.
     */
    Logger getLogger();

    // ========== Property Persistence ==========

    /**
     * Update a property value and persist to file.
     * @param key property key
     * @param value property value
     */
    void updateProperty(String key, String value);
}
