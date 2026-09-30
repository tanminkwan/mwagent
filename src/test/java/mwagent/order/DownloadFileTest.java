package mwagent.order;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * DownloadFile 테스트 - chmod mode 값 검증 (인자 주입 방지)
 */
class DownloadFileTest {

    @Test
    void isValidChmodMode_WithOctal_ShouldReturnTrue() {
        assertThat(DownloadFile.isValidChmodMode("755")).isTrue();
        assertThat(DownloadFile.isValidChmodMode("0644")).isTrue();
    }

    @Test
    void isValidChmodMode_WithInjection_ShouldReturnFalse() {
        assertThat(DownloadFile.isValidChmodMode("755 /etc/passwd")).isFalse();
        assertThat(DownloadFile.isValidChmodMode("--reference=/etc/shadow")).isFalse();
        assertThat(DownloadFile.isValidChmodMode("u+s")).isFalse();
        assertThat(DownloadFile.isValidChmodMode("888")).isFalse();
        assertThat(DownloadFile.isValidChmodMode("")).isFalse();
        assertThat(DownloadFile.isValidChmodMode(null)).isFalse();
    }
}
