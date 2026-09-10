package mwagent;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.json.simple.JSONArray;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import mwagent.service.registration.BootstrapService;
import mwagent.vo.AgentStatus;
import mwagent.vo.RawCommandsVO;

/**
 * PreWork 테스트
 * BootstrapService를 Mock하여 테스트
 */
@ExtendWith(MockitoExtension.class)
class PreWorkTest {

    @Mock
    private BootstrapService bootstrapService;

    private PreWork preWork;

    @BeforeEach
    void setUp() {
        preWork = new PreWork(bootstrapService);
    }

    @Test
    void doPreWork_WhenBootstrapSucceeds_ShouldReturnCommands() {
        // Given
        RawCommandsVO expectedResult = new RawCommandsVO();
        expectedResult.setReturnCode(1);
        JSONArray commands = new JSONArray();
        commands.add("command1");
        expectedResult.setCommands(commands);

        when(bootstrapService.executeBootstrapProcess()).thenReturn(expectedResult);

        // When
        RawCommandsVO result = preWork.doPreWork();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getReturnCode()).isEqualTo(1);
        assertThat(result.getCommands()).hasSize(1);
        verify(bootstrapService).executeBootstrapProcess();
    }

    @Test
    void doPreWork_WhenBootstrapFails_ShouldReturnErrorCode() {
        // Given
        RawCommandsVO expectedResult = new RawCommandsVO();
        expectedResult.setReturnCode(-20); // Registration error

        when(bootstrapService.executeBootstrapProcess()).thenReturn(expectedResult);

        // When
        RawCommandsVO result = preWork.doPreWork();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getReturnCode()).isEqualTo(-20);
        verify(bootstrapService).executeBootstrapProcess();
    }

    @Test
    void doPreWork_ShouldDelegateToBootstrapService() {
        // Given
        RawCommandsVO mockResult = new RawCommandsVO();
        mockResult.setReturnCode(AgentStatus.APPROVED.getReturnCode());

        when(bootstrapService.executeBootstrapProcess()).thenReturn(mockResult);

        // When
        RawCommandsVO result = preWork.doPreWork();

        // Then
        assertThat(result).isEqualTo(mockResult);
        verify(bootstrapService, times(1)).executeBootstrapProcess();
    }

    @Test
    void constructor_WithoutParameters_ShouldCreateBootstrapService() {
        // When
        PreWork preWorkWithDefaultConstructor = new PreWork();

        // Then
        assertThat(preWorkWithDefaultConstructor).isNotNull();
        // BootstrapService가 내부적으로 생성되었는지 확인
        // (실제 동작 테스트는 통합 테스트에서 수행)
    }
}
