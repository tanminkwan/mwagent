package mwagent.service;

import static org.assertj.core.api.Assertions.*;

import java.util.logging.Logger;

import org.json.simple.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import mwagent.lifecycle.LifecycleState;

/**
 * CommandExecutorService 테스트
 */
class CommandExecutorServiceTest {

    private CommandExecutorService service;

    @BeforeEach
    void setUp() {
        // Initialize Config for testing
        mwagent.common.Config.getConfig().setLogger(Logger.getLogger("TestLogger"));
        service = new CommandExecutorService(5); // 5 second timeout for tests
    }

    @AfterEach
    void tearDown() throws Exception {
        if (service != null && service.getState() == LifecycleState.RUNNING) {
            service.stop();
        }
    }

    @Test
    void constructor_ShouldCreateServiceInCreatedState() {
        assertThat(service.getState()).isEqualTo(LifecycleState.CREATED);
        assertThat(service.isRunning()).isFalse();
        assertThat(service.isStopped()).isFalse();
    }

    @Test
    void start_ShouldTransitionToRunning() throws Exception {
        // When
        service.start();

        // Then
        assertThat(service.getState()).isEqualTo(LifecycleState.RUNNING);
        assertThat(service.isRunning()).isTrue();
        assertThat(service.isShutdown()).isFalse();
    }

    @Test
    void start_WhenAlreadyRunning_ShouldThrowException() throws Exception {
        // Given
        service.start();

        // When & Then
        assertThatThrownBy(() -> service.start())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot start");
    }

    @Test
    void stop_ShouldTransitionToStopped() throws Exception {
        // Given
        service.start();

        // When
        service.stop();

        // Then
        assertThat(service.getState()).isEqualTo(LifecycleState.STOPPED);
        assertThat(service.isStopped()).isTrue();
        assertThat(service.isShutdown()).isTrue();
        assertThat(service.isTerminated()).isTrue();
    }

    @Test
    void executeCommand_WhenNotRunning_ShouldThrowException() {
        // Given
        JSONObject command = new JSONObject();
        command.put("command_class", "TestCommand");

        // When & Then
        assertThatThrownBy(() -> service.executeCommand(command))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not running");
    }

    @Test
    void executeCommand_WithNullCommandClass_ShouldLogWarning() throws Exception {
        // Given
        service.start();
        JSONObject command = new JSONObject();
        // No command_class field

        // When - should not throw
        service.executeCommand(command);

        // Then - no exception thrown
        assertThat(service.isRunning()).isTrue();
    }

    @Test
    void gracefulShutdown_ShouldWaitForRunningTasks() throws Exception {
        // Given
        service.start();

        // When
        long startTime = System.currentTimeMillis();
        service.stop();
        long elapsedTime = System.currentTimeMillis() - startTime;

        // Then - should shutdown quickly when no tasks running
        assertThat(elapsedTime).isLessThan(1000);
        assertThat(service.isStopped()).isTrue();
    }

    @Test
    void stop_WhenAlreadyStopped_ShouldLogWarning() throws Exception {
        // Given
        service.start();
        service.stop();

        // When - stop again
        service.stop();

        // Then - no exception, just warning logged
        assertThat(service.getState()).isEqualTo(LifecycleState.STOPPED);
    }
}
