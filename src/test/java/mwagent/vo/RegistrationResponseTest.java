package mwagent.vo;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * RegistrationResponse VO 테스트
 */
class RegistrationResponseTest {

    @Test
    void success_ShouldCreateSuccessResponse() {
        // When
        RegistrationResponse response = RegistrationResponse.success();

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getReturnCode()).isEqualTo(1);
        assertThat(response.getMessage()).contains("successful");
    }

    @Test
    void failure_ShouldCreateFailureResponse() {
        // Given
        long returnCode = -1;
        String message = "Agent already exists";

        // When
        RegistrationResponse response = RegistrationResponse.failure(returnCode, message);

        // Then
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getReturnCode()).isEqualTo(returnCode);
        assertThat(response.getMessage()).isEqualTo(message);
    }

    @Test
    void toString_ShouldContainAllFields() {
        // Given
        RegistrationResponse response = RegistrationResponse.failure(-1, "Error");

        // When
        String result = response.toString();

        // Then
        assertThat(result)
            .contains("success=false")
            .contains("returnCode=-1")
            .contains("message='Error'");
    }
}
