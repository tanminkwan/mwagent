package mwagent.vo;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * AgentStatus enum 테스트
 */
class AgentStatusTest {

    @ParameterizedTest
    @CsvSource({
        "-1, NOT_REGISTERED",
        "-2, PENDING_APPROVAL",
        "0, APPROVED",
        "1, APPROVED",
        "-10, CONNECTION_ERROR",
        "-20, REGISTRATION_ERROR",
        "-99, UNKNOWN_ERROR",
        "-999, UNKNOWN_ERROR"
    })
    void fromReturnCode_ShouldReturnCorrectStatus(long returnCode, String expectedStatus) {
        // When
        AgentStatus status = AgentStatus.fromReturnCode(returnCode);

        // Then
        assertThat(status.name()).isEqualTo(expectedStatus);
    }

    @Test
    void notRegistered_ShouldNeedRegistration() {
        // Given
        AgentStatus status = AgentStatus.NOT_REGISTERED;

        // Then
        assertThat(status.needsRegistration()).isTrue();
        assertThat(status.isPending()).isFalse();
        assertThat(status.isApproved()).isFalse();
        assertThat(status.isError()).isFalse();
    }

    @Test
    void pendingApproval_ShouldBePending() {
        // Given
        AgentStatus status = AgentStatus.PENDING_APPROVAL;

        // Then
        assertThat(status.isPending()).isTrue();
        assertThat(status.needsRegistration()).isFalse();
        assertThat(status.isApproved()).isFalse();
        assertThat(status.isError()).isFalse();
    }

    @Test
    void approved_ShouldBeApproved() {
        // Given
        AgentStatus status = AgentStatus.APPROVED;

        // Then
        assertThat(status.isApproved()).isTrue();
        assertThat(status.needsRegistration()).isFalse();
        assertThat(status.isPending()).isFalse();
        assertThat(status.isError()).isFalse();
    }

    @Test
    void errorStatuses_ShouldBeError() {
        // Connection Error
        assertThat(AgentStatus.CONNECTION_ERROR.isError()).isTrue();

        // Registration Error
        assertThat(AgentStatus.REGISTRATION_ERROR.isError()).isTrue();

        // Unknown Error
        assertThat(AgentStatus.UNKNOWN_ERROR.isError()).isTrue();
    }

    @Test
    void getReturnCode_ShouldReturnCorrectCode() {
        assertThat(AgentStatus.NOT_REGISTERED.getReturnCode()).isEqualTo(-1);
        assertThat(AgentStatus.PENDING_APPROVAL.getReturnCode()).isEqualTo(-2);
        assertThat(AgentStatus.APPROVED.getReturnCode()).isEqualTo(0);
        assertThat(AgentStatus.CONNECTION_ERROR.getReturnCode()).isEqualTo(-10);
        assertThat(AgentStatus.REGISTRATION_ERROR.getReturnCode()).isEqualTo(-20);
    }

    @Test
    void getDescription_ShouldReturnMeaningfulDescription() {
        assertThat(AgentStatus.NOT_REGISTERED.getDescription())
            .isEqualTo("Agent Not Exists");
        assertThat(AgentStatus.PENDING_APPROVAL.getDescription())
            .isEqualTo("Not Approved Yet");
        assertThat(AgentStatus.APPROVED.getDescription())
            .isEqualTo("Approved");
    }
}
