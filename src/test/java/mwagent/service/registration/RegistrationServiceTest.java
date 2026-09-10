package mwagent.service.registration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.json.simple.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import mwagent.vo.MwResponseVO;
import mwagent.vo.RegistrationRequest;
import mwagent.vo.RegistrationResponse;

/**
 * RegistrationService 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        // Config를 Mock하는 것이 복잡하므로, 통합 테스트로 전환하거나
        // Config를 주입받도록 리팩토링 필요
        registrationService = new RegistrationService();
    }

    @Test
    void createRegistrationRequest_ShouldReturnValidRequest() {
        // Given & When
        RegistrationRequest request = registrationService.createRegistrationRequest();

        // Then
        assertThat(request).isNotNull();
        assertThat(request.getAgentId()).isNotNull();
        assertThat(request.getAgentType()).isNotNull();
        assertThat(request.getHostId()).isNotNull();
        assertThat(request.getInstallationPath()).isNotNull();
    }

    // TODO: HTTP 호출을 Mock하려면 Common.httpPOST를 주입받아야 함
    // 현재는 static method라서 Mock 불가능
    // 리팩토링 후 테스트 추가 필요
}
