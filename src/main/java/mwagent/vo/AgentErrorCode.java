package mwagent.vo;

/**
 * Agent 표준 에러 코드
 *
 * 코드 범위:
 * - 1000-1999: 인증 관련 에러
 * - 2000-2999: 명령 실행 에러
 * - 3000-3999: 네트워크 에러
 * - 4000-4999: 파일 작업 에러
 * - 5000-5999: 설정 에러
 */
public enum AgentErrorCode {

    // Authentication errors (1000-1999)
    AUTH_FAILED(1000, "Authentication failed"),
    AUTH_CERTIFICATE_INVALID(1001, "Certificate is invalid"),
    AUTH_CERTIFICATE_EXPIRED(1002, "Certificate has expired"),
    AUTH_TOKEN_EXPIRED(1003, "Access token has expired"),
    AUTH_TOKEN_INVALID(1004, "Access token is invalid"),
    AUTH_REFRESH_TOKEN_EXPIRED(1005, "Refresh token has expired"),

    // Command execution errors (2000-2999)
    CMD_EXECUTION_FAILED(2000, "Command execution failed"),
    CMD_NOT_WHITELISTED(2001, "Command not in whitelist"),
    CMD_INVALID_PARAMS(2002, "Invalid command parameters"),
    CMD_CLASS_NOT_FOUND(2003, "Command class not found"),
    CMD_TIMEOUT(2004, "Command execution timeout"),

    // Network errors (3000-3999)
    NET_CONNECTION_FAILED(3000, "Network connection failed"),
    NET_TIMEOUT(3001, "Network timeout"),
    NET_SSL_ERROR(3002, "SSL/TLS error"),
    NET_DNS_ERROR(3003, "DNS resolution failed"),

    // File operation errors (4000-4999)
    FILE_NOT_FOUND(4000, "File not found"),
    FILE_PATH_TRAVERSAL(4001, "Path traversal detected"),
    FILE_PERMISSION_DENIED(4002, "Permission denied"),
    FILE_READ_ERROR(4003, "File read error"),
    FILE_WRITE_ERROR(4004, "File write error"),

    // Configuration errors (5000-5999)
    CONFIG_INVALID(5000, "Invalid configuration"),
    CONFIG_MISSING_REQUIRED(5001, "Missing required configuration"),
    CONFIG_PARSE_ERROR(5002, "Configuration parse error"),
    CONFIG_PROTECTED_KEY(5003, "Protected configuration key"),

    // Agent status codes (기존 코드와의 호환성)
    AGENT_NOT_REGISTERED(-1, "Agent not registered"),
    AGENT_PENDING_APPROVAL(-2, "Agent pending approval"),
    AGENT_REJECTED(-3, "Agent rejected"),

    // Success
    SUCCESS(0, "Success");

    private final int code;
    private final String message;

    AgentErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    /**
     * 에러 코드로 AgentErrorCode 조회
     *
     * @param code 에러 코드
     * @return AgentErrorCode 또는 null
     */
    public static AgentErrorCode fromCode(int code) {
        for (AgentErrorCode errorCode : values()) {
            if (errorCode.code == code) {
                return errorCode;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return String.format("[%d] %s", code, message);
    }
}
