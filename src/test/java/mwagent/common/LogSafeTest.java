package mwagent.common;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * LogSafe 테스트 - 로그에 토큰·비밀값·개행이 그대로 남지 않는지 검증
 */
class LogSafeTest {

    @Test
    void safe_ShouldMaskTokenFields() {
        String body = "{\"access_token\":\"abc.def\",\"refresh_token\":\"r1\",\"token_type\":\"Bearer\",\"expires_in\":3600}";

        String out = LogSafe.safe(body);

        assertThat(out).doesNotContain("abc.def").doesNotContain("\"r1\"");
        assertThat(out).contains("\"access_token\":\"***\"");
        assertThat(out).contains("\"expires_in\":3600");
    }

    @Test
    void safe_ShouldMaskCredentialsInNestedJsonString() {
        // additional_params 가 문자열로 들어간 명령 JSON (toJSONString 결과)
        String command = "{\"command_id\":\"C1\",\"additional_params\":\"{\\\"upsert\\\":[{\\\"mqtt_credential\\\":\\\"s3cret\\\"},"
                + "{\\\"truststore.password\\\":\\\"pw1\\\"}]}\"}";

        String out = LogSafe.safe(command, 1000);

        assertThat(out).doesNotContain("s3cret").doesNotContain("pw1");
        assertThat(out).contains("\"command_id\":\"C1\"");
    }

    @Test
    void safe_ShouldMaskJwt() {
        String out = LogSafe.safe("Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhIn0.sig_part-1 end");

        assertThat(out).isEqualTo("Bearer *** end");
    }

    @Test
    void safe_ShouldEscapeNewlines() {
        String out = LogSafe.safe("line1\r\nINFO: forged");

        assertThat(out).doesNotContain("\n").doesNotContain("\r");
        assertThat(out).isEqualTo("line1\\r\\nINFO: forged");
    }

    @Test
    void safe_ShouldTruncateLongValues() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            sb.append('x');
        }

        String out = LogSafe.safe(sb.toString(), 100);

        assertThat(out).startsWith("xxxxxxxxxx").endsWith("...(500 chars)");
        assertThat(out.length()).isLessThan(130);
    }

    @Test
    void safe_WithNull_ShouldReturnNullText() {
        assertThat(LogSafe.safe(null)).isEqualTo("null");
    }

    @Test
    void safe_ShouldMaskPresignedUrlSignature() {
        String params = "{\"url\":\"https://s3.example.com/f.zip?X-Amz-Credential=AKIA1&X-Amz-Date=20260930&X-Amz-Signature=abcdef\"}";

        String out = LogSafe.safe(params, 1000);

        assertThat(out).doesNotContain("AKIA1").doesNotContain("abcdef");
        assertThat(out).contains("X-Amz-Date=20260930").contains("X-Amz-Signature=***");
    }
}
