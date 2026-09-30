package mwagent.application;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import mwagent.common.Config;
import mwagent.infrastructure.config.ConfigurationProvider;
import mwagent.infrastructure.http.ApacheHttpClientAdapter;
import mwagent.infrastructure.http.HttpClient;

/**
 * Simple dependency injection container for the MWM Agent application.
 * Manages singleton beans and their lifecycle.
 *
 * Usage:
 *   ApplicationContext ctx = ApplicationContext.getInstance();
 *   ConfigurationProvider config = ctx.getBean(ConfigurationProvider.class);
 *   HttpClient httpClient = ctx.getBean(HttpClient.class);
 */
public class ApplicationContext {

    private static volatile ApplicationContext instance;
    private final Map<Class<?>, Object> beans = new ConcurrentHashMap<>();
    private volatile boolean initialized = false;

    private ApplicationContext() {
        // Private constructor for singleton
    }

    /**
     * Get the singleton ApplicationContext instance.
     */
    public static ApplicationContext getInstance() {
        if (instance == null) {
            synchronized (ApplicationContext.class) {
                if (instance == null) {
                    instance = new ApplicationContext();
                }
            }
        }
        return instance;
    }

    /**
     * Initialize the application context with default beans.
     * This should be called once at application startup after Config is initialized.
     */
    public synchronized void initialize() {
        if (initialized) {
            return;
        }

        // 1. Register ConfigurationProvider (using existing Config singleton)
        Config config = Config.getConfig();
        register(ConfigurationProvider.class, config);
        register(Config.class, config);

        // 2. Register HttpClient
        Logger logger = config.getLogger();
        HttpClient httpClient = new ApacheHttpClientAdapter(config, logger);
        register(HttpClient.class, httpClient);

        initialized = true;
    }

    /**
     * Initialize with a custom ConfigurationProvider (for testing).
     */
    public synchronized void initialize(ConfigurationProvider config) {
        if (initialized) {
            return;
        }

        // 1. Register ConfigurationProvider
        register(ConfigurationProvider.class, config);

        // 2. Register HttpClient
        Logger logger = config.getLogger();
        if (logger != null) {
            HttpClient httpClient = new ApacheHttpClientAdapter(config, logger);
            register(HttpClient.class, httpClient);
        }

        initialized = true;
    }

    /**
     * Register a bean instance.
     * @param type the bean type/interface
     * @param instance the bean instance
     */
    public <T> void register(Class<T> type, T instance) {
        beans.put(type, instance);
    }

    /**
     * Get a bean by type.
     * @param type the bean type/interface
     * @return the bean instance
     * @throws IllegalStateException if no bean is registered for the type
     */
    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> type) {
        Object bean = beans.get(type);
        if (bean == null) {
            throw new IllegalStateException("No bean registered for type: " + type.getName());
        }
        return (T) bean;
    }

    /**
     * Get a bean by type, or null if not registered.
     * @param type the bean type/interface
     * @return the bean instance or null
     */
    @SuppressWarnings("unchecked")
    public <T> T getBeanOrNull(Class<T> type) {
        return (T) beans.get(type);
    }

    /**
     * Check if a bean is registered.
     * @param type the bean type/interface
     * @return true if registered
     */
    public boolean hasBean(Class<?> type) {
        return beans.containsKey(type);
    }

    /**
     * Check if the context is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Shutdown and cleanup all beans.
     */
    public synchronized void shutdown() {
        // Close HttpClient if registered
        HttpClient httpClient = getBeanOrNull(HttpClient.class);
        if (httpClient != null) {
            httpClient.close();
        }

        beans.clear();
        initialized = false;
    }

    /**
     * Reset the singleton instance (for testing only).
     */
    public static synchronized void reset() {
        if (instance != null) {
            instance.shutdown();
            instance = null;
        }
    }
}
