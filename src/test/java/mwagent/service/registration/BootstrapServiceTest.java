package mwagent.service.registration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.json.simple.JSONArray;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import mwagent.vo.AgentStatus;
import mwagent.vo.RawCommandsVO;
import mwagent.vo.RegistrationRequest;
import mwagent.vo.RegistrationResponse;

/**
 * BootstrapService 테스트
 * Mock을 활용한 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
class BootstrapServiceTest {

    @Mock
    private RegistrationService registrationService;

    @Mock
    private AgentStatusService statusService;

    private BootstrapService bootstrapService;

    @BeforeEach
    void setUp() {
        // Set command_check_cycle to 0 to avoid sleep in tests
        mwagent.common.Config.getConfig().setCommand_check_cycle(0);
        bootstrapService = new BootstrapService(registrationService, statusService);
    }

    @Test
    void executeBootstrapProcess_WhenAgentApproved_ShouldReturnImmediately() {
        // Given
        RawCommandsVO approvedResponse = new RawCommandsVO();
        approvedResponse.setReturnCode(AgentStatus.APPROVED.getReturnCode());
        JSONArray commands = new JSONArray();
        commands.add("BOOT_COMMAND");
        approvedResponse.setCommands(commands);

        when(statusService.noticeStartAndGetStatus()).thenReturn(approvedResponse);
        when(statusService.getAgentStatus(approvedResponse)).thenReturn(AgentStatus.APPROVED);

        // When
        RawCommandsVO result = bootstrapService.executeBootstrapProcess();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getReturnCode()).isEqualTo(AgentStatus.APPROVED.getReturnCode());
        assertThat(result.getCommands()).hasSize(1);

        verify(statusService).noticeStartAndGetStatus();
        verify(statusService).getAgentStatus(approvedResponse);
        verifyNoInteractions(registrationService);
    }

    @Test
    void executeBootstrapProcess_WhenNotRegistered_ShouldRegisterAndRetry() {
        // Given
        RawCommandsVO notRegisteredResponse = new RawCommandsVO();
        notRegisteredResponse.setReturnCode(AgentStatus.NOT_REGISTERED.getReturnCode());

        RawCommandsVO approvedResponse = new RawCommandsVO();
        approvedResponse.setReturnCode(AgentStatus.APPROVED.getReturnCode());

        when(statusService.noticeStartAndGetStatus())
            .thenReturn(notRegisteredResponse)
            .thenReturn(approvedResponse);

        when(statusService.getAgentStatus(notRegisteredResponse))
            .thenReturn(AgentStatus.NOT_REGISTERED);
        when(statusService.getAgentStatus(approvedResponse))
            .thenReturn(AgentStatus.APPROVED);

        when(registrationService.createRegistrationRequest())
            .thenReturn(new RegistrationRequest("agent-1", "J", "/opt", "host-1"));

        when(registrationService.register(any(RegistrationRequest.class)))
            .thenReturn(RegistrationResponse.success());

        // When
        RawCommandsVO result = bootstrapService.executeBootstrapProcess();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getReturnCode()).isEqualTo(AgentStatus.APPROVED.getReturnCode());

        verify(statusService, times(2)).noticeStartAndGetStatus();
        verify(registrationService).createRegistrationRequest();
        verify(registrationService).register(any(RegistrationRequest.class));
    }

    @Test
    void executeBootstrapProcess_WhenRegistrationFails_ShouldReturnError() {
        // Given
        RawCommandsVO notRegisteredResponse = new RawCommandsVO();
        notRegisteredResponse.setReturnCode(AgentStatus.NOT_REGISTERED.getReturnCode());

        when(statusService.noticeStartAndGetStatus()).thenReturn(notRegisteredResponse);
        when(statusService.getAgentStatus(notRegisteredResponse))
            .thenReturn(AgentStatus.NOT_REGISTERED);

        when(registrationService.createRegistrationRequest())
            .thenReturn(new RegistrationRequest("agent-1", "J", "/opt", "host-1"));

        when(registrationService.register(any(RegistrationRequest.class)))
            .thenReturn(RegistrationResponse.failure(-1, "Registration failed"));

        // When
        RawCommandsVO result = bootstrapService.executeBootstrapProcess();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getReturnCode()).isEqualTo(-20); // Registration error

        verify(registrationService).register(any(RegistrationRequest.class));
    }

    @Test
    void executeBootstrapProcess_WhenPendingApproval_ShouldWaitAndRetry() {
        // Given
        RawCommandsVO pendingResponse = new RawCommandsVO();
        pendingResponse.setReturnCode(AgentStatus.PENDING_APPROVAL.getReturnCode());

        RawCommandsVO approvedResponse = new RawCommandsVO();
        approvedResponse.setReturnCode(AgentStatus.APPROVED.getReturnCode());

        when(statusService.noticeStartAndGetStatus())
            .thenReturn(pendingResponse)
            .thenReturn(approvedResponse);

        when(statusService.getAgentStatus(pendingResponse))
            .thenReturn(AgentStatus.PENDING_APPROVAL);
        when(statusService.getAgentStatus(approvedResponse))
            .thenReturn(AgentStatus.APPROVED);

        // When
        RawCommandsVO result = bootstrapService.executeBootstrapProcess();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getReturnCode()).isEqualTo(AgentStatus.APPROVED.getReturnCode());

        verify(statusService, times(2)).noticeStartAndGetStatus();
        verifyNoInteractions(registrationService); // 이미 등록됨, 재등록 불필요
    }

    @Test
    void executeBootstrapProcess_WhenConnectionError_ShouldReturnError() {
        // Given
        RawCommandsVO errorResponse = new RawCommandsVO();
        errorResponse.setReturnCode(AgentStatus.CONNECTION_ERROR.getReturnCode());

        when(statusService.noticeStartAndGetStatus()).thenReturn(errorResponse);
        when(statusService.getAgentStatus(errorResponse))
            .thenReturn(AgentStatus.CONNECTION_ERROR);

        // When
        RawCommandsVO result = bootstrapService.executeBootstrapProcess();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getReturnCode()).isEqualTo(AgentStatus.CONNECTION_ERROR.getReturnCode());

        verify(statusService).noticeStartAndGetStatus();
    }
}
