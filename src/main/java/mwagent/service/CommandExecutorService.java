package mwagent.service;

import static mwagent.common.Config.getConfig;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.simple.JSONObject;

import mwagent.common.LogSafe;
import mwagent.OrderCallerThread;
import mwagent.lifecycle.AgentLifecycle;
import mwagent.lifecycle.LifecycleState;

/**
 * 명령 실행을 관리하는 서비스
 *
 * - ExecutorService를 사용하여 명령을 비동기로 실행
 * - Graceful shutdown 지원
 */
public class CommandExecutorService implements AgentLifecycle {

    private final Logger logger;
    private LifecycleState state;
    private ExecutorService executorService;
    private final int shutdownTimeoutSeconds;

    public CommandExecutorService() {
        this(30); // Default 30초 timeout
    }

    public CommandExecutorService(int shutdownTimeoutSeconds) {
        this.logger = getConfig().getLogger();
        this.state = LifecycleState.CREATED;
        this.shutdownTimeoutSeconds = shutdownTimeoutSeconds;
    }

    @Override
    public void start() throws Exception {
        if (!state.canTransitionTo(LifecycleState.STARTING)) {
            throw new IllegalStateException("Cannot start from state: " + state);
        }

        logger.info("Starting CommandExecutor service...");
        state = LifecycleState.STARTING;

        try {
            executorService = Executors.newCachedThreadPool();
            state = LifecycleState.RUNNING;
            logger.info("CommandExecutor service started successfully");

        } catch (Exception e) {
            state = LifecycleState.FAILED;
            logger.log(Level.SEVERE, "Failed to start CommandExecutor service", e);
            throw e;
        }
    }

    @Override
    public void stop() throws Exception {
        if (!state.canTransitionTo(LifecycleState.STOPPING)) {
            logger.warning("Cannot stop from state: " + state);
            return;
        }

        logger.info("Stopping CommandExecutor service...");
        state = LifecycleState.STOPPING;

        try {
            if (executorService != null) {
                // 1. Stop accepting new tasks
                executorService.shutdown();
                logger.info("ExecutorService shutdown initiated, waiting for running tasks to complete...");

                // 2. Wait for existing tasks to complete
                boolean terminated = executorService.awaitTermination(shutdownTimeoutSeconds, TimeUnit.SECONDS);

                if (!terminated) {
                    // 3. Force shutdown if timeout
                    logger.warning("ExecutorService did not terminate in time. Forcing shutdown...");
                    executorService.shutdownNow();

                    // Wait a bit more for forced shutdown
                    if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                        logger.severe("ExecutorService did not terminate even after forced shutdown");
                    }
                }

                logger.info("CommandExecutor service stopped successfully");
            }

            state = LifecycleState.STOPPED;

        } catch (InterruptedException e) {
            state = LifecycleState.FAILED;
            logger.log(Level.SEVERE, "Interrupted while stopping CommandExecutor service", e);
            Thread.currentThread().interrupt();
            throw e;

        } catch (Exception e) {
            state = LifecycleState.FAILED;
            logger.log(Level.SEVERE, "Error stopping CommandExecutor service", e);
            throw e;
        }
    }

    @Override
    public LifecycleState getState() {
        return state;
    }

    /**
     * 명령을 비동기로 실행합니다.
     *
     * @param command 실행할 명령 (JSON)
     * @throws IllegalStateException 서비스가 RUNNING 상태가 아닐 때
     */
    public void executeCommand(JSONObject command) {
        if (state != LifecycleState.RUNNING) {
            throw new IllegalStateException("CommandExecutor is not running: " + state);
        }

        String commandClass = (String) command.get("command_class");

        if (commandClass == null) {
            logger.warning("Command_class not found: " + LogSafe.safe(command.toJSONString(), 1000));
            return;
        }

        try {
            OrderCallerThread thread = new OrderCallerThread("mwagent.order." + commandClass, command);
            executorService.submit(thread);
            logger.info("Submitted command: " + commandClass + " with data: " + LogSafe.safe(command.toJSONString(), 1000));

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to submit command: " + commandClass, e);
        }
    }

    /**
     * 실행 중인 태스크 개수 반환 (테스트/모니터링용)
     * ExecutorService의 내부 상태를 확인하기 어려우므로 근사치
     */
    public int getActiveTaskCount() {
        if (executorService == null || executorService.isShutdown()) {
            return 0;
        }
        // CachedThreadPool은 정확한 active count를 제공하지 않으므로
        // Thread.activeCount()를 사용 (근사치)
        return Thread.activeCount();
    }

    /**
     * ExecutorService가 종료되었는지 확인
     */
    public boolean isShutdown() {
        return executorService != null && executorService.isShutdown();
    }

    /**
     * ExecutorService가 완전히 종료되었는지 확인
     */
    public boolean isTerminated() {
        return executorService != null && executorService.isTerminated();
    }
}
