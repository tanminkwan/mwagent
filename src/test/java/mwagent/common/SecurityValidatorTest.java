package mwagent.common;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * SecurityValidator 테스트
 *
 * Command Injection과 Path Traversal 방어 로직 검증
 */
class SecurityValidatorTest {

    @TempDir
    Path tempDir;

    // ==================== Command Injection Tests ====================

    @Test
    void isValidCommandParam_WithSafeParams_ShouldReturnTrue() {
        assertThat(SecurityValidator.isValidCommandParam("param1")).isTrue();
        assertThat(SecurityValidator.isValidCommandParam("file.txt")).isTrue();
        assertThat(SecurityValidator.isValidCommandParam("--option=value")).isTrue();
        assertThat(SecurityValidator.isValidCommandParam("-f")).isTrue();
        assertThat(SecurityValidator.isValidCommandParam("123")).isTrue();
        assertThat(SecurityValidator.isValidCommandParam("")).isTrue();
        assertThat(SecurityValidator.isValidCommandParam(null)).isTrue();
    }

    @Test
    void isValidCommandParam_WithSemicolon_ShouldReturnFalse() {
        assertThat(SecurityValidator.isValidCommandParam("; rm -rf /")).isFalse();
        assertThat(SecurityValidator.isValidCommandParam("test; echo")).isFalse();
    }

    @Test
    void isValidCommandParam_WithPipe_ShouldReturnFalse() {
        assertThat(SecurityValidator.isValidCommandParam("| cat /etc/passwd")).isFalse();
        assertThat(SecurityValidator.isValidCommandParam("test | grep")).isFalse();
    }

    @Test
    void isValidCommandParam_WithAmpersand_ShouldReturnFalse() {
        assertThat(SecurityValidator.isValidCommandParam("& whoami")).isFalse();
        assertThat(SecurityValidator.isValidCommandParam("test && rm")).isFalse();
    }

    @Test
    void isValidCommandParam_WithBacktick_ShouldReturnFalse() {
        assertThat(SecurityValidator.isValidCommandParam("`id`")).isFalse();
        assertThat(SecurityValidator.isValidCommandParam("test `whoami`")).isFalse();
    }

    @Test
    void isValidCommandParam_WithDollarSign_ShouldReturnFalse() {
        assertThat(SecurityValidator.isValidCommandParam("$(whoami)")).isFalse();
        assertThat(SecurityValidator.isValidCommandParam("${PATH}")).isFalse();
    }

    @Test
    void isValidCommandParam_WithRedirection_ShouldReturnFalse() {
        assertThat(SecurityValidator.isValidCommandParam("> /etc/passwd")).isFalse();
        assertThat(SecurityValidator.isValidCommandParam("< input")).isFalse();
    }

    @Test
    void isValidCommandParam_WithNewline_ShouldReturnFalse() {
        assertThat(SecurityValidator.isValidCommandParam("test\nrm -rf")).isFalse();
        assertThat(SecurityValidator.isValidCommandParam("test\r\nrm")).isFalse();
    }

    @Test
    void sanitizeCommandParam_ShouldRemoveDangerousChars() {
        assertThat(SecurityValidator.sanitizeCommandParam("; rm -rf /"))
            .isEqualTo(" rm -rf /");
        assertThat(SecurityValidator.sanitizeCommandParam("test | grep"))
            .isEqualTo("test  grep");
        assertThat(SecurityValidator.sanitizeCommandParam("$(whoami)"))
            .isEqualTo("whoami");
        assertThat(SecurityValidator.sanitizeCommandParam(null))
            .isEqualTo("");
    }

    // ==================== Path Traversal Tests ====================

    @Test
    void isValidPath_WithSafePath_ShouldReturnTrue() {
        String baseDir = tempDir.toString();

        assertThat(SecurityValidator.isValidPath(baseDir, "subdir")).isTrue();
        assertThat(SecurityValidator.isValidPath(baseDir, "subdir/file.txt")).isTrue();
        assertThat(SecurityValidator.isValidPath(baseDir, "file.txt")).isTrue();
    }

    @Test
    void isValidPath_WithPathTraversal_ShouldReturnFalse() {
        String baseDir = tempDir.toString();

        assertThat(SecurityValidator.isValidPath(baseDir, "../etc/passwd")).isFalse();
        assertThat(SecurityValidator.isValidPath(baseDir, "..\\Windows\\System32")).isFalse();
        assertThat(SecurityValidator.isValidPath(baseDir, "subdir/../../etc")).isFalse();
    }

    @Test
    void isValidPath_WithNullOrEmpty_ShouldReturnFalse() {
        String baseDir = tempDir.toString();

        assertThat(SecurityValidator.isValidPath(baseDir, null)).isFalse();
        assertThat(SecurityValidator.isValidPath(baseDir, "")).isFalse();
    }

    @Test
    void isValidAbsolutePath_WithAllowedPath_ShouldReturnTrue() {
        String userDir = System.getProperty("user.dir");
        String tempDir = System.getProperty("java.io.tmpdir");

        assertThat(SecurityValidator.isValidAbsolutePath(
            userDir + File.separator + "test.txt", userDir)).isTrue();
        assertThat(SecurityValidator.isValidAbsolutePath(
            tempDir + File.separator + "test.txt", tempDir)).isTrue();
    }

    @Test
    void isValidAbsolutePath_WithDisallowedPath_ShouldReturnFalse() {
        String userDir = System.getProperty("user.dir");

        // /etc/passwd is not under userDir
        assertThat(SecurityValidator.isValidAbsolutePath("/etc/passwd", userDir)).isFalse();
        assertThat(SecurityValidator.isValidAbsolutePath("C:\\Windows\\System32\\config", userDir)).isFalse();
    }

    @Test
    void isValidAbsolutePath_WithPathTraversal_ShouldReturnFalse() {
        String userDir = System.getProperty("user.dir");

        assertThat(SecurityValidator.isValidAbsolutePath(
            userDir + "/../../../etc/passwd", userDir)).isFalse();
    }

    @Test
    void getValidatedPath_WithSafePath_ShouldReturnCanonicalPath() {
        String baseDir = tempDir.toString();

        String result = SecurityValidator.getValidatedPath(baseDir, "subdir");
        assertThat(result).startsWith(baseDir);
    }

    @Test
    void getValidatedPath_WithPathTraversal_ShouldThrowException() {
        String baseDir = tempDir.toString();

        assertThatThrownBy(() ->
            SecurityValidator.getValidatedPath(baseDir, "../etc/passwd"))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("Path traversal detected");
    }

    // ==================== Filename Validation Tests ====================

    @Test
    void isValidFilename_WithSafeFilename_ShouldReturnTrue() {
        assertThat(SecurityValidator.isValidFilename("test.sh")).isTrue();
        assertThat(SecurityValidator.isValidFilename("script.bat")).isTrue();
        assertThat(SecurityValidator.isValidFilename("file-name_123.txt")).isTrue();
    }

    @Test
    void isValidFilename_WithPathSeparator_ShouldReturnFalse() {
        assertThat(SecurityValidator.isValidFilename("../test.sh")).isFalse();
        assertThat(SecurityValidator.isValidFilename("subdir/test.sh")).isFalse();
        assertThat(SecurityValidator.isValidFilename("..\\test.sh")).isFalse();
        assertThat(SecurityValidator.isValidFilename("subdir\\test.sh")).isFalse();
    }

    @Test
    void isValidFilename_WithDotDot_ShouldReturnFalse() {
        assertThat(SecurityValidator.isValidFilename("..")).isFalse();
        assertThat(SecurityValidator.isValidFilename(".")).isFalse();
    }

    @Test
    void isValidFilename_WithNullOrEmpty_ShouldReturnFalse() {
        assertThat(SecurityValidator.isValidFilename(null)).isFalse();
        assertThat(SecurityValidator.isValidFilename("")).isFalse();
    }
}
