package mwagent.vo;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * RegistrationRequest VO 테스트
 */
class RegistrationRequestTest {

    @Test
    void constructor_ShouldSetAllFields() {
        // Given
        String agentId = "agent-001";
        String agentType = "J";
        String installationPath = "/opt/mwagent";
        String hostId = "server01";

        // When
        RegistrationRequest request = new RegistrationRequest(
            agentId, agentType, installationPath, hostId
        );

        // Then
        assertThat(request.getAgentId()).isEqualTo(agentId);
        assertThat(request.getAgentType()).isEqualTo(agentType);
        assertThat(request.getInstallationPath()).isEqualTo(installationPath);
        assertThat(request.getHostId()).isEqualTo(hostId);
    }

    @Test
    void toString_ShouldContainAllFields() {
        // Given
        RegistrationRequest request = new RegistrationRequest(
            "agent-001", "J", "/opt/mwagent", "server01"
        );

        // When
        String result = request.toString();

        // Then
        assertThat(result)
            .contains("agent-001")
            .contains("J")
            .contains("/opt/mwagent")
            .contains("server01");
    }
}
