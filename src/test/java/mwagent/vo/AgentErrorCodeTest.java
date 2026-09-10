package mwagent.vo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.*;

/**
 * AgentErrorCode 테스트
 */
@DisplayName("AgentErrorCode Tests")
class AgentErrorCodeTest {

    @Test
    @DisplayName("에러 코드 조회 - 존재하는 코드")
    void fromCode_WithValidCode_ShouldReturnErrorCode() {
        AgentErrorCode result = AgentErrorCode.fromCode(1000);

        assertThat(result).isEqualTo(AgentErrorCode.AUTH_FAILED);
        assertThat(result.getCode()).isEqualTo(1000);
        assertThat(result.getMessage()).isEqualTo("Authentication failed");
    }

    @Test
    @DisplayName("에러 코드 조회 - 존재하지 않는 코드")
    void fromCode_WithInvalidCode_ShouldReturnNull() {
        AgentErrorCode result = AgentErrorCode.fromCode(9999);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Agent 상태 코드 호환성")
    void agentStatusCodes_ShouldBeCompatible() {
        assertThat(AgentErrorCode.AGENT_NOT_REGISTERED.getCode()).isEqualTo(-1);
        assertThat(AgentErrorCode.AGENT_PENDING_APPROVAL.getCode()).isEqualTo(-2);
        assertThat(AgentErrorCode.AGENT_REJECTED.getCode()).isEqualTo(-3);
    }

    @Test
    @DisplayName("toString 형식 확인")
    void toString_ShouldReturnFormattedString() {
        String result = AgentErrorCode.AUTH_FAILED.toString();

        assertThat(result).isEqualTo("[1000] Authentication failed");
    }

    @Test
    @DisplayName("에러 코드 범위 확인 - 인증 에러")
    void authErrorCodes_ShouldBeInRange() {
        assertThat(AgentErrorCode.AUTH_FAILED.getCode()).isBetween(1000, 1999);
        assertThat(AgentErrorCode.AUTH_TOKEN_EXPIRED.getCode()).isBetween(1000, 1999);
    }

    @Test
    @DisplayName("에러 코드 범위 확인 - 명령 실행 에러")
    void cmdErrorCodes_ShouldBeInRange() {
        assertThat(AgentErrorCode.CMD_EXECUTION_FAILED.getCode()).isBetween(2000, 2999);
        assertThat(AgentErrorCode.CMD_NOT_WHITELISTED.getCode()).isBetween(2000, 2999);
    }

    @Test
    @DisplayName("에러 코드 범위 확인 - 네트워크 에러")
    void netErrorCodes_ShouldBeInRange() {
        assertThat(AgentErrorCode.NET_CONNECTION_FAILED.getCode()).isBetween(3000, 3999);
        assertThat(AgentErrorCode.NET_TIMEOUT.getCode()).isBetween(3000, 3999);
    }

    @Test
    @DisplayName("에러 코드 범위 확인 - 파일 에러")
    void fileErrorCodes_ShouldBeInRange() {
        assertThat(AgentErrorCode.FILE_NOT_FOUND.getCode()).isBetween(4000, 4999);
        assertThat(AgentErrorCode.FILE_PATH_TRAVERSAL.getCode()).isBetween(4000, 4999);
    }

    @Test
    @DisplayName("SUCCESS 코드는 0")
    void successCode_ShouldBeZero() {
        assertThat(AgentErrorCode.SUCCESS.getCode()).isEqualTo(0);
    }
}
