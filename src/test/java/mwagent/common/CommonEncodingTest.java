package mwagent.common;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Common URL 경로 인코딩 · Windows cmd 경로 테스트
 */
class CommonEncodingTest {

    @Test
    void encodePathSegment_ShouldKeepOrdinaryIds() {
        // 일반적인 agent_id / 버전 / 파일명은 그대로 나가야 한다 (서버 쪽 변경 불필요)
        assertThat(Common.encodePathSegment("host01_user_J")).isEqualTo("host01_user_J");
        assertThat(Common.encodePathSegment("0000.0010.0002")).isEqualTo("0000.0010.0002");
        assertThat(Common.encodePathSegment("my-file~1.zip")).isEqualTo("my-file~1.zip");
    }

    @Test
    void encodePathSegment_ShouldEncodeReservedCharacters() {
        assertThat(Common.encodePathSegment("John Doe")).isEqualTo("John%20Doe");
        assertThat(Common.encodePathSegment("a/b?c#d&e")).isEqualTo("a%2Fb%3Fc%23d%26e");
        assertThat(Common.encodePathSegment("../x")).isEqualTo("..%2Fx");
        assertThat(Common.encodePathSegment("한")).isEqualTo("%ED%95%9C");
        assertThat(Common.encodePathSegment(null)).isEmpty();
    }

    @Test
    void encodePath_ShouldKeepSlashes() {
        assertThat(Common.encodePath("sub/my file.zip")).isEqualTo("sub/my%20file.zip");
        assertThat(Common.encodePath("file.zip")).isEqualTo("file.zip");
        assertThat(Common.encodePath(null)).isEmpty();
    }

    @Test
    void windowsCmd_ShouldUseSystemRoot() {
        assertThat(Common.windowsCmd("C:\\Windows")).isEqualTo("C:\\Windows\\System32\\cmd.exe");
        assertThat(Common.windowsCmd("C:\\Windows\\")).isEqualTo("C:\\Windows\\System32\\cmd.exe");
        assertThat(Common.windowsCmd(null)).isEqualTo("cmd.exe");
        assertThat(Common.windowsCmd(" ")).isEqualTo("cmd.exe");
    }
}
