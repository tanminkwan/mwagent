package mwagent.order;

import static mwagent.common.Config.getConfig;
import static org.assertj.core.api.Assertions.*;

import java.io.File;
import java.io.FileOutputStream;
import java.util.logging.Logger;

import org.json.simple.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import mwagent.vo.ResultVO;

/**
 * ReadFullPathFile 테스트
 *
 * 기본 허용 디렉토리 및 security.allowed_read_paths 설정 검증
 */
class ReadFullPathFileTest {

    private File tempDir;
    private File testFile;

    @BeforeEach
    void setUp() throws Exception {
        getConfig().setLogger(Logger.getLogger("TestLogger"));
        getConfig().setHostName("test-host");
        getConfig().setSecurityPathTraversalCheck(true);
        getConfig().setSecurityAllowedReadPaths("");

        // 기본 허용 목록 밖의 디렉토리에 테스트 파일 생성
        tempDir = new File(System.getProperty("java.io.tmpdir"), "mwagent-read-test-" + System.nanoTime());
        File confDir = new File(tempDir, "config");
        assertThat(confDir.mkdirs()).isTrue();

        testFile = new File(confDir, "rewrite_https.conf");
        FileOutputStream out = new FileOutputStream(testFile);
        out.write("RewriteRule test".getBytes("UTF-8"));
        out.close();
    }

    @AfterEach
    void tearDown() {
        getConfig().setSecurityAllowedReadPaths("");
        testFile.delete();
        new File(tempDir, "config").delete();
        tempDir.delete();
    }

    private ReadFullPathFile newOrder(String fullPath) {
        JSONObject command = new JSONObject();
        command.put("command_id", "CMD-001");
        command.put("repetition_seq", 1L);
        command.put("additional_params", fullPath);
        command.put("target_file_path", "/sw/webtob/config/");
        command.put("target_file_name", "rewrite_https.conf");
        command.put("result_hash", "");
        command.put("result_receiver", "SERVER");
        command.put("target_object", "test-object");

        return new ReadFullPathFile(command);
    }

    @Test
    void getAllowedReadPaths_ShouldReturnDefaults_WhenNothingConfigured() {
        // Given: security.allowed_read_paths 미설정

        // When
        String[] paths = newOrder(testFile.getAbsolutePath()).getAllowedReadPaths();

        // Then: 기본 목록만 포함
        assertThat(paths).contains(System.getProperty("user.dir"), "/var/log", "/opt");
    }

    @Test
    void getAllowedReadPaths_ShouldAppendConfiguredPaths() {
        // Given
        getConfig().setSecurityAllowedReadPaths(" /sw/webtob , /usr/local/tomcat/logs ,, ");

        // When
        String[] paths = newOrder(testFile.getAbsolutePath()).getAllowedReadPaths();

        // Then: 기본 목록 + 설정값(공백 제거, 빈 항목 무시)
        assertThat(paths).contains("/var/log", "/sw/webtob", "/usr/local/tomcat/logs");
        assertThat(paths).doesNotContain("");
    }

    @Test
    void execute_ShouldReadFile_WhenPathIsUnderConfiguredRoot() {
        // Given: 기본 허용 목록 밖 디렉토리를 설정으로 추가
        getConfig().setSecurityAllowedReadPaths(tempDir.getAbsolutePath());
        ReadFullPathFile order = newOrder(testFile.getAbsolutePath());

        // When
        int rtn = order.execute();

        // Then
        ResultVO result = order.getResultVo();
        assertThat(rtn).isEqualTo(1);
        assertThat(result.isOk()).isTrue();
        assertThat(result.getResult()).contains("RewriteRule test");
    }

    @Test
    void getFileFullName_ShouldReturnNull_WhenPathIsNotAllowed() {
        // Given: 허용되지 않은 경로
        ReadFullPathFile order = newOrder("/etc/shadow");

        // When / Then
        assertThat(order.getFileFullName()).isNull();
    }

    @Test
    void getFileFullName_ShouldReturnPath_WhenTraversalCheckIsOff() {
        // Given
        getConfig().setSecurityPathTraversalCheck(false);
        ReadFullPathFile order = newOrder("/etc/hosts");

        // When / Then
        assertThat(order.getFileFullName()).isEqualTo("/etc/hosts");

        getConfig().setSecurityPathTraversalCheck(true);
    }
}
