package mwagent.lifecycle;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * LifecycleState 테스트
 */
class LifecycleStateTest {

    @Test
    void testStateTransitions() {
        // CREATED can transition to STARTING or FAILED
        assertThat(LifecycleState.CREATED.canTransitionTo(LifecycleState.STARTING)).isTrue();
        assertThat(LifecycleState.CREATED.canTransitionTo(LifecycleState.FAILED)).isTrue();
        assertThat(LifecycleState.CREATED.canTransitionTo(LifecycleState.RUNNING)).isFalse();

        // STARTING can transition to RUNNING or FAILED
        assertThat(LifecycleState.STARTING.canTransitionTo(LifecycleState.RUNNING)).isTrue();
        assertThat(LifecycleState.STARTING.canTransitionTo(LifecycleState.FAILED)).isTrue();
        assertThat(LifecycleState.STARTING.canTransitionTo(LifecycleState.STOPPED)).isFalse();

        // RUNNING can transition to STOPPING or FAILED
        assertThat(LifecycleState.RUNNING.canTransitionTo(LifecycleState.STOPPING)).isTrue();
        assertThat(LifecycleState.RUNNING.canTransitionTo(LifecycleState.FAILED)).isTrue();
        assertThat(LifecycleState.RUNNING.canTransitionTo(LifecycleState.STARTING)).isFalse();

        // STOPPING can transition to STOPPED or FAILED
        assertThat(LifecycleState.STOPPING.canTransitionTo(LifecycleState.STOPPED)).isTrue();
        assertThat(LifecycleState.STOPPING.canTransitionTo(LifecycleState.FAILED)).isTrue();
        assertThat(LifecycleState.STOPPING.canTransitionTo(LifecycleState.RUNNING)).isFalse();

        // STOPPED and FAILED are terminal states
        assertThat(LifecycleState.STOPPED.canTransitionTo(LifecycleState.STARTING)).isFalse();
        assertThat(LifecycleState.FAILED.canTransitionTo(LifecycleState.STARTING)).isFalse();
    }

    @Test
    void testIsStarted() {
        assertThat(LifecycleState.CREATED.isStarted()).isFalse();
        assertThat(LifecycleState.STARTING.isStarted()).isTrue();
        assertThat(LifecycleState.RUNNING.isStarted()).isTrue();
        assertThat(LifecycleState.STOPPING.isStarted()).isFalse();
        assertThat(LifecycleState.STOPPED.isStarted()).isFalse();
        assertThat(LifecycleState.FAILED.isStarted()).isFalse();
    }

    @Test
    void testIsRunning() {
        assertThat(LifecycleState.CREATED.isRunning()).isFalse();
        assertThat(LifecycleState.STARTING.isRunning()).isFalse();
        assertThat(LifecycleState.RUNNING.isRunning()).isTrue();
        assertThat(LifecycleState.STOPPING.isRunning()).isFalse();
        assertThat(LifecycleState.STOPPED.isRunning()).isFalse();
        assertThat(LifecycleState.FAILED.isRunning()).isFalse();
    }

    @Test
    void testIsStopped() {
        assertThat(LifecycleState.CREATED.isStopped()).isFalse();
        assertThat(LifecycleState.STARTING.isStopped()).isFalse();
        assertThat(LifecycleState.RUNNING.isStopped()).isFalse();
        assertThat(LifecycleState.STOPPING.isStopped()).isFalse();
        assertThat(LifecycleState.STOPPED.isStopped()).isTrue();
        assertThat(LifecycleState.FAILED.isStopped()).isTrue();
    }

    @Test
    void testGetDescription() {
        assertThat(LifecycleState.CREATED.getDescription()).isNotEmpty();
        assertThat(LifecycleState.RUNNING.getDescription()).contains("operational");
        assertThat(LifecycleState.FAILED.getDescription()).contains("error");
    }

    @Test
    void testAllStatesHaveDescription() {
        for (LifecycleState state : LifecycleState.values()) {
            assertThat(state.getDescription()).isNotNull().isNotEmpty();
        }
    }
}
