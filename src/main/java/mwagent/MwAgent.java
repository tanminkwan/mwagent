package mwagent;

import static mwagent.common.Config.getConfig;

import java.util.logging.Level;
import java.util.logging.Logger;

import mwagent.application.ApplicationContext;
import mwagent.lifecycle.AgentLifecycleManager;

/**
 * MwAgent - Remote Management Agent
 *
 * Phase 1 Refactoring: Lifecycle Management
 * - 기존: PreWork → FirstWork → MainWork (절차적)
 * - 개선: AgentLifecycleManager (생명주기 기반)
 *
 * Phase 3 Refactoring: Dependency Injection
 * - ApplicationContext: DI 컨테이너
 * - ConfigurationProvider: 설정 추상화
 * - HttpClient: HTTP 통신 추상화
 *
 * 아키텍처:
 * - AgentLifecycleManager: 전체 생명주기 관리
 * - BootstrapService: 등록 및 승인
 * - MqttService: MQTT 명령 구독 (선택)
 * - CommandExecutorService: 명령 실행 관리
 * - GracefulShutdownHandler: 정상 종료 처리
 */
public class MwAgent {

    private static Logger logger;

    public static void main(String[] args) {

        try {
            // Print startup banner with version (before logger initialization)
            String version = getConfig().getAgent_version();
            System.out.println("=========================================");
            System.out.println("  MwManger Agent Starting");
            System.out.println("  Version: " + version);
            System.out.println("=========================================");

            // Initialize configuration first (creates logger)
            long configResult = getConfig().setConfig();
            if (configResult < 0) {
                // Error already logged in Config.setConfig()
                System.exit(1);
            }

            // Get logger after config is initialized
            logger = getConfig().getLogger();

            // Exceptions nobody catches (e.g. in a worker thread) go to the log file, not only stderr
            Thread.setDefaultUncaughtExceptionHandler(uncaughtExceptionLogger(logger));

            // Log version info
            logger.info("=========================================");
            logger.info("MwManger Agent Started");
            logger.info("Version: " + version);
            logger.info("=========================================");

            // Initialize ApplicationContext (DI Container)
            ApplicationContext.getInstance().initialize();

            // Create lifecycle manager
            AgentLifecycleManager lifecycleManager = new AgentLifecycleManager();

            // Register shutdown hook for graceful termination
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown signal received");
                try {
                    lifecycleManager.stop();
                    ApplicationContext.getInstance().shutdown();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error during shutdown", e);
                }
            }, "ShutdownHook"));

            // Start agent
            lifecycleManager.start();

            // Wait for termination
            lifecycleManager.awaitTermination();

            logger.info("Agent terminated normally");
            System.exit(0);

        } catch (Exception e) {
            // Logger is always available (created at start of setConfig)
            if (logger != null) {
                logger.log(Level.SEVERE, "Agent failed to start", e);
            } else {
                getConfig().getLogger().log(Level.SEVERE, "Agent failed to start", e);
            }
            System.exit(1);
        }
    }

    static Thread.UncaughtExceptionHandler uncaughtExceptionLogger(final Logger log) {
        return (thread, e) -> {
            log.log(Level.SEVERE, "Uncaught exception in thread " + thread.getName(), e);
            // One line on stderr too: it is redirected to mwagent-console.log (README)
            System.err.println("Uncaught exception in thread " + thread.getName() + ": " + e);
        };
    }
}
