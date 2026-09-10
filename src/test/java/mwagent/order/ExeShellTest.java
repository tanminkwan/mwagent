package mwagent.order;

import static mwagent.common.Config.getConfig;
import static org.assertj.core.api.Assertions.*;

import java.util.logging.Logger;

import org.json.simple.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import mwagent.vo.ResultVO;

/**
 * ExeShell 테스트
 *
 * 기본 동작 및 보안 검증 테스트
 */
class ExeShellTest {

    private ExeShell exeShell;
    private JSONObject testCommand;

    @BeforeEach
    void setUp() {
        // Initialize Config for testing
        getConfig().setLogger(Logger.getLogger("TestLogger"));
        getConfig().setOs("LINUX");
        getConfig().setHostName("test-host");

        // Given: 테스트용 명령어 JSON 생성
        testCommand = new JSONObject();
        testCommand.put("command_id", "CMD-001");
        testCommand.put("repetition_seq", 1L);
        testCommand.put("host_name", "test-host");
        testCommand.put("user_name", "test-user");
        testCommand.put("additional_params", "");
        testCommand.put("target_file_path", "test/");
        testCommand.put("target_file_name", "test.sh");
        testCommand.put("result_hash", "");
        testCommand.put("result_receiver", "SERVER");
        testCommand.put("target_object", "test-object");

        exeShell = new ExeShell(testCommand);
    }

    @Test
    void constructor_ShouldInitializeWithCommand() {
        // Then
        assertThat(exeShell).isNotNull();
        assertThat(exeShell.getCommandVo()).isNotNull();
    }

    @Test
    void execute_ShouldReturnOne() {
        // When
        int result = exeShell.execute();

        // Then
        assertThat(result).isEqualTo(1);
    }

    @Test
    void execute_ShouldSetResultVO() {
        // When
        exeShell.execute();

        // Then
        ResultVO resultVo = exeShell.getResultVo();
        assertThat(resultVo).isNotNull();
        assertThat(resultVo.getHostName()).isEqualTo("test-host");
        assertThat(resultVo.getTargetFileName()).isEqualTo("test.sh");
    }

    @Test
    void constructor_WithAdditionalParams_ShouldIncludeInCommand() {
        // Given
        JSONObject commandWithParams = new JSONObject();
        commandWithParams.put("command_id", "CMD-002");
        commandWithParams.put("repetition_seq", 1L);
        commandWithParams.put("host_name", "test-host");
        commandWithParams.put("user_name", "test-user");
        commandWithParams.put("additional_params", "param1 param2");
        commandWithParams.put("target_file_path", "test/");
        commandWithParams.put("target_file_name", "test.sh");
        commandWithParams.put("result_hash", "");
        commandWithParams.put("result_receiver", "SERVER");
        commandWithParams.put("target_object", "test-object");

        // When
        ExeShell shellWithParams = new ExeShell(commandWithParams);
        shellWithParams.execute();

        // Then
        assertThat(shellWithParams.getCommandVo().getAdditionalParams())
            .isEqualTo("param1 param2");
    }

    @Test
    void execute_WithEmptyAdditionalParams_ShouldSucceed() {
        // Given
        testCommand.put("additional_params", "");

        // When
        ExeShell shell = new ExeShell(testCommand);
        int result = shell.execute();

        // Then
        assertThat(result).isEqualTo(1);
    }

    // ==================== Security Tests ====================
    // Note: Command injection check is OFF by default (configurable)
    // These tests enable it explicitly to verify the security feature works

    @Test
    void execute_WithCommandInjection_Semicolon_ShouldFail() {
        // Given: Command injection attempt with semicolon
        getConfig().setSecurityCommandInjectionCheck(true);  // Enable command injection check
        testCommand.put("additional_params", "; rm -rf /");
        ExeShell maliciousShell = new ExeShell(testCommand);

        // When
        maliciousShell.execute();

        // Then: Should fail with security error
        ResultVO resultVo = maliciousShell.getResultVo();
        assertThat(resultVo.isOk()).isFalse();
        assertThat(resultVo.getResult()).contains("SecurityException");

        // Cleanup
        getConfig().setSecurityCommandInjectionCheck(false);
    }

    @Test
    void execute_WithCommandInjection_Pipe_ShouldFail() {
        // Given: Command injection attempt with pipe
        getConfig().setSecurityCommandInjectionCheck(true);  // Enable command injection check
        testCommand.put("additional_params", "| cat /etc/passwd");
        ExeShell maliciousShell = new ExeShell(testCommand);

        // When
        maliciousShell.execute();

        // Then: Should fail with security error
        ResultVO resultVo = maliciousShell.getResultVo();
        assertThat(resultVo.isOk()).isFalse();
        assertThat(resultVo.getResult()).contains("SecurityException");

        // Cleanup
        getConfig().setSecurityCommandInjectionCheck(false);
    }

    @Test
    void execute_WithCommandInjection_Backtick_ShouldFail() {
        // Given: Command injection attempt with backtick
        getConfig().setSecurityCommandInjectionCheck(true);  // Enable command injection check
        testCommand.put("additional_params", "`whoami`");
        ExeShell maliciousShell = new ExeShell(testCommand);

        // When
        maliciousShell.execute();

        // Then: Should fail with security error
        ResultVO resultVo = maliciousShell.getResultVo();
        assertThat(resultVo.isOk()).isFalse();
        assertThat(resultVo.getResult()).contains("SecurityException");

        // Cleanup
        getConfig().setSecurityCommandInjectionCheck(false);
    }

    @Test
    void execute_WithCommandInjection_DollarSign_ShouldFail() {
        // Given: Command injection attempt with $()
        getConfig().setSecurityCommandInjectionCheck(true);  // Enable command injection check
        testCommand.put("additional_params", "$(whoami)");
        ExeShell maliciousShell = new ExeShell(testCommand);

        // When
        maliciousShell.execute();

        // Then: Should fail with security error
        ResultVO resultVo = maliciousShell.getResultVo();
        assertThat(resultVo.isOk()).isFalse();
        assertThat(resultVo.getResult()).contains("SecurityException");

        // Cleanup
        getConfig().setSecurityCommandInjectionCheck(false);
    }

    @Test
    void execute_WithCommandInjection_Disabled_ShouldNotBlock() {
        // Given: Command injection attempt but check is disabled (default)
        getConfig().setSecurityCommandInjectionCheck(false);  // Ensure disabled (default)
        testCommand.put("additional_params", "; rm -rf /");
        ExeShell maliciousShell = new ExeShell(testCommand);

        // When
        maliciousShell.execute();

        // Then: Should NOT be blocked by command injection check
        // (may still fail for other reasons like file not found, but not SecurityException for command injection)
        ResultVO resultVo = maliciousShell.getResultVo();
        // When disabled, the command is allowed to proceed (may fail for other reasons)
        // The key is it should NOT contain "Invalid parameters" security error
        if (!resultVo.isOk()) {
            assertThat(resultVo.getResult()).doesNotContain("Invalid parameters");
        }
    }

    @Test
    void execute_WithPathTraversal_ShouldFail() {
        // Given: Path traversal attempt
        testCommand.put("target_file_path", "../../../etc/");
        ExeShell maliciousShell = new ExeShell(testCommand);

        // When
        maliciousShell.execute();

        // Then: Should fail with security error
        ResultVO resultVo = maliciousShell.getResultVo();
        assertThat(resultVo.isOk()).isFalse();
        assertThat(resultVo.getResult()).contains("SecurityException");
    }

    @Test
    void execute_WithInvalidFilename_ShouldFail() {
        // Given: Invalid filename with path separator
        testCommand.put("target_file_name", "../passwd");
        ExeShell maliciousShell = new ExeShell(testCommand);

        // When
        maliciousShell.execute();

        // Then: Should fail with security error
        ResultVO resultVo = maliciousShell.getResultVo();
        assertThat(resultVo.isOk()).isFalse();
        assertThat(resultVo.getResult()).contains("SecurityException");
    }
}
