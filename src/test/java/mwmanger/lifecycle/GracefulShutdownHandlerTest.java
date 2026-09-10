package mwagent.lifecycle;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * GracefulShutdownHandler 테스트
 */
@ExtendWith(MockitoExtension.class)
class GracefulShutdownHandlerTest {

    @Mock
    private AgentLifecycle mockService1;

    @Mock
    private AgentLifecycle mockService2;

    @Mock
    private AgentLifecycle mockService3;

    private GracefulShutdownHandler handler;

    @BeforeEach
    void setUp() {
        // Initialize Config for testing
        mwagent.common.Config.getConfig().setLogger(Logger.getLogger("TestLogger"));
        handler = new GracefulShutdownHandler(10); // 10 second timeout
    }

    @Test
    void constructor_ShouldCreateEmptyHandler() {
        assertThat(handler.getServiceCount()).isZero();
    }

    @Test
    void registerService_ShouldAddService() {
        // When
        handler.registerService(mockService1);
        handler.registerService(mockService2);

        // Then
        assertThat(handler.getServiceCount()).isEqualTo(2);
    }

    @Test
    void registerService_WithNull_ShouldNotAdd() {
        // When
        handler.registerService(null);

        // Then
        assertThat(handler.getServiceCount()).isZero();
    }

    @Test
    void shutdown_WithNoServices_ShouldCompleteQuickly() {
        // When
        handler.shutdown();

        // Then - no exception
        assertThat(handler.getServiceCount()).isZero();
    }

    @Test
    void shutdown_ShouldStopServicesInReverseOrder() throws Exception {
        // Given
        when(mockService1.getState()).thenReturn(LifecycleState.RUNNING);
        when(mockService2.getState()).thenReturn(LifecycleState.RUNNING);
        when(mockService3.getState()).thenReturn(LifecycleState.RUNNING);

        handler.registerService(mockService1);
        handler.registerService(mockService2);
        handler.registerService(mockService3);

        // When
        handler.shutdown();

        // Then - verify reverse order (LIFO)
        verify(mockService3).stop(); // Registered last, stopped first
        verify(mockService2).stop();
        verify(mockService1).stop(); // Registered first, stopped last
    }

    @Test
    void shutdown_WhenServiceThrowsException_ShouldContinueWithOthers() throws Exception {
        // Given
        when(mockService1.getState()).thenReturn(LifecycleState.RUNNING);
        when(mockService2.getState()).thenReturn(LifecycleState.RUNNING);

        doThrow(new RuntimeException("Stop failed")).when(mockService1).stop();

        handler.registerService(mockService1);
        handler.registerService(mockService2);

        // When
        handler.shutdown();

        // Then - should still stop service2 despite service1 failure
        verify(mockService1).stop();
        verify(mockService2).stop();
    }

    @Test
    void getServiceStatuses_ShouldReturnAllStatuses() {
        // Given
        when(mockService1.getState()).thenReturn(LifecycleState.RUNNING);
        when(mockService2.getState()).thenReturn(LifecycleState.STOPPED);

        handler.registerService(mockService1);
        handler.registerService(mockService2);

        // When
        List<GracefulShutdownHandler.ServiceStatus> statuses = handler.getServiceStatuses();

        // Then
        assertThat(statuses).hasSize(2);
        assertThat(statuses.get(0).getState()).isEqualTo(LifecycleState.RUNNING);
        assertThat(statuses.get(1).getState()).isEqualTo(LifecycleState.STOPPED);
        assertThat(statuses.get(0).getServiceName()).isNotEmpty();
        assertThat(statuses.get(1).getServiceName()).isNotEmpty();
    }

    @Test
    void serviceStatus_ShouldHaveToString() {
        // Given
        GracefulShutdownHandler.ServiceStatus status =
            new GracefulShutdownHandler.ServiceStatus("TestService", LifecycleState.RUNNING);

        // When
        String result = status.toString();

        // Then
        assertThat(result).contains("TestService");
        assertThat(result).contains("RUNNING");
    }
}
