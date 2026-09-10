package mwagent;

import mwagent.service.registration.BootstrapService;
import mwagent.vo.RawCommandsVO;
import org.json.simple.JSONArray;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AgentRegistrationPhase 테스트
 */
@DisplayName("AgentRegistrationPhase Tests")
class AgentRegistrationPhaseTest {

    @Mock
    private BootstrapService bootstrapService;

    private AgentRegistrationPhase registrationPhase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        registrationPhase = new AgentRegistrationPhase(bootstrapService);
    }

    @Test
    @DisplayName("execute - 부트스트랩 성공 시 명령 반환")
    void execute_WhenBootstrapSucceeds_ShouldReturnCommands() {
        // given
        RawCommandsVO expectedResult = new RawCommandsVO();
        expectedResult.setReturnCode(1);
        expectedResult.setCommands(new JSONArray());

        when(bootstrapService.executeBootstrapProcess()).thenReturn(expectedResult);

        // when
        RawCommandsVO result = registrationPhase.execute();

        // then
        assertThat(result).isNotNull();
        assertThat(result.getReturnCode()).isEqualTo(1);
        verify(bootstrapService).executeBootstrapProcess();
    }

    @Test
    @DisplayName("execute - 부트스트랩 실패 시 에러 코드 반환")
    void execute_WhenBootstrapFails_ShouldReturnErrorCode() {
        // given
        RawCommandsVO expectedResult = new RawCommandsVO();
        expectedResult.setReturnCode(-1); // Agent not registered

        when(bootstrapService.executeBootstrapProcess()).thenReturn(expectedResult);

        // when
        RawCommandsVO result = registrationPhase.execute();

        // then
        assertThat(result.getReturnCode()).isEqualTo(-1);
    }

    @Test
    @DisplayName("기본 생성자로 생성 가능")
    void defaultConstructor_ShouldCreateInstance() {
        AgentRegistrationPhase phase = new AgentRegistrationPhase();
        assertThat(phase).isNotNull();
    }
}
