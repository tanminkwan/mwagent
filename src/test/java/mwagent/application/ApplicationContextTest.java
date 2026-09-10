package mwagent.application;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import mwagent.infrastructure.config.ConfigurationProvider;
import mwagent.infrastructure.config.MockConfigurationProvider;
import mwagent.infrastructure.http.HttpClient;

/**
 * Tests for ApplicationContext (DI Container).
 */
@DisplayName("ApplicationContext Tests")
class ApplicationContextTest {

    @BeforeEach
    void setUp() {
        // Reset singleton before each test
        ApplicationContext.reset();
    }

    @AfterEach
    void tearDown() {
        ApplicationContext.reset();
    }

    @Test
    @DisplayName("getInstance returns singleton")
    void getInstance_ReturnsSingleton() {
        ApplicationContext ctx1 = ApplicationContext.getInstance();
        ApplicationContext ctx2 = ApplicationContext.getInstance();

        assertSame(ctx1, ctx2);
    }

    @Test
    @DisplayName("isInitialized returns false before initialization")
    void isInitialized_ReturnsFalseBeforeInit() {
        ApplicationContext ctx = ApplicationContext.getInstance();
        assertFalse(ctx.isInitialized());
    }

    @Test
    @DisplayName("initialize with mock config registers beans")
    void initialize_WithMockConfig_RegistersBeans() {
        ApplicationContext ctx = ApplicationContext.getInstance();
        MockConfigurationProvider mockConfig = new MockConfigurationProvider();

        ctx.initialize(mockConfig);

        assertTrue(ctx.isInitialized());
        assertTrue(ctx.hasBean(ConfigurationProvider.class));
        assertSame(mockConfig, ctx.getBean(ConfigurationProvider.class));
    }

    @Test
    @DisplayName("getBean throws exception for unregistered type")
    void getBean_UnregisteredType_ThrowsException() {
        ApplicationContext ctx = ApplicationContext.getInstance();

        assertThrows(IllegalStateException.class, () -> {
            ctx.getBean(String.class);
        });
    }

    @Test
    @DisplayName("getBeanOrNull returns null for unregistered type")
    void getBeanOrNull_UnregisteredType_ReturnsNull() {
        ApplicationContext ctx = ApplicationContext.getInstance();

        String result = ctx.getBeanOrNull(String.class);

        assertNull(result);
    }

    @Test
    @DisplayName("hasBean returns false for unregistered type")
    void hasBean_UnregisteredType_ReturnsFalse() {
        ApplicationContext ctx = ApplicationContext.getInstance();

        assertFalse(ctx.hasBean(String.class));
    }

    @Test
    @DisplayName("register and getBean work correctly")
    void register_AndGetBean() {
        ApplicationContext ctx = ApplicationContext.getInstance();
        String testBean = "test-bean-value";

        ctx.register(String.class, testBean);

        assertTrue(ctx.hasBean(String.class));
        assertEquals(testBean, ctx.getBean(String.class));
    }

    @Test
    @DisplayName("shutdown clears beans and resets initialized flag")
    void shutdown_ClearsBeans() {
        ApplicationContext ctx = ApplicationContext.getInstance();
        MockConfigurationProvider mockConfig = new MockConfigurationProvider();

        ctx.initialize(mockConfig);
        assertTrue(ctx.isInitialized());

        ctx.shutdown();

        assertFalse(ctx.isInitialized());
        assertFalse(ctx.hasBean(ConfigurationProvider.class));
    }

    @Test
    @DisplayName("reset clears singleton")
    void reset_ClearsSingleton() {
        ApplicationContext ctx1 = ApplicationContext.getInstance();
        ctx1.register(String.class, "test");

        ApplicationContext.reset();

        ApplicationContext ctx2 = ApplicationContext.getInstance();
        assertFalse(ctx2.hasBean(String.class));
    }

    @Test
    @DisplayName("double initialization is ignored")
    void doubleInitialization_IsIgnored() {
        ApplicationContext ctx = ApplicationContext.getInstance();
        MockConfigurationProvider mockConfig1 = new MockConfigurationProvider().withAgentId("agent1");
        MockConfigurationProvider mockConfig2 = new MockConfigurationProvider().withAgentId("agent2");

        ctx.initialize(mockConfig1);
        ctx.initialize(mockConfig2); // Should be ignored

        ConfigurationProvider registered = ctx.getBean(ConfigurationProvider.class);
        assertEquals("agent1", registered.getAgentId()); // First one should be kept
    }

    @Test
    @DisplayName("initialize with mock config and logger registers HttpClient")
    void initialize_WithLogger_RegistersHttpClient() {
        ApplicationContext ctx = ApplicationContext.getInstance();
        MockConfigurationProvider mockConfig = new MockConfigurationProvider()
                .withLogger(java.util.logging.Logger.getLogger("TestLogger"));

        ctx.initialize(mockConfig);

        assertTrue(ctx.hasBean(HttpClient.class));
        assertNotNull(ctx.getBean(HttpClient.class));
    }
}
