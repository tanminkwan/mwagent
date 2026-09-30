package mwagent.common;

import static org.assertj.core.api.Assertions.*;

import java.io.File;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Config.createDefaultLogger 테스트 - 로그는 로테이션되는 파일에만 남고 stderr 로 중복되지 않는다
 */
class ConfigLoggerTest {

    @TempDir
    Path tempDir;

    private Logger created;

    @AfterEach
    void tearDown() {
        if (created != null) {
            for (Handler h : created.getHandlers()) {
                created.removeHandler(h);
                h.close();
            }
        }
    }

    @Test
    void createDefaultLogger_ShouldWriteToFileOnly() {
        created = Config.createDefaultLogger("mwagent.test.file-only", tempDir.toString());

        // 부모(root) ConsoleHandler 로 같은 로그가 stderr 에 한 번 더 나가지 않는다 (nohup.out 중복 방지)
        assertThat(created.getUseParentHandlers()).isFalse();
        assertThat(created.getHandlers()).hasSize(1);
        assertThat(created.getHandlers()[0]).isInstanceOf(FileHandler.class);
        assertThat(created.getHandlers()[0].getFormatter()).isInstanceOf(SafeLogFormatter.class);

        created.info("hello");
        assertThat(new File(tempDir.toFile(), "mwagent.0.0.log")).exists();
    }

    @Test
    void createDefaultLogger_WhenFileCannotBeOpened_ShouldKeepConsole() {
        created = Config.createDefaultLogger("mwagent.test.fallback", tempDir.resolve("no/such/dir").toString());

        // 파일을 못 열면 로그를 잃지 않도록 콘솔 출력을 유지한다
        assertThat(created.getUseParentHandlers()).isTrue();
        assertThat(created.getHandlers()).isEmpty();
    }
}
