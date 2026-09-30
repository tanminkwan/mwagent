package mwagent.common;

import static org.assertj.core.api.Assertions.*;

import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.Test;

/**
 * SafeLogFormatter 테스트 - 로그 메시지 안의 개행으로 로그 줄을 위조하지 못하게 한다
 */
class SafeLogFormatterTest {

    private final SafeLogFormatter formatter = new SafeLogFormatter();

    @Test
    void format_ShouldEscapeNewlinesInMessage() {
        LogRecord r = new LogRecord(Level.INFO, "path=/tmp/a\nSEVERE: forged line\r\nx");

        String out = formatter.format(r);

        // SimpleFormatter 는 헤더 1줄 + 메시지 1줄. 메시지 안의 개행은 이스케이프된다
        assertThat(out.split("\n", -1)).hasSize(3); // header, message, trailing ""
        assertThat(out).contains("path=/tmp/a\\nSEVERE: forged line\\r\\nx");
    }

    @Test
    void format_ShouldEscapeNewlinesInParameters() {
        LogRecord r = new LogRecord(Level.INFO, "value={0}");
        r.setParameters(new Object[] {"a\nb"});

        assertThat(formatter.formatMessage(r)).isEqualTo("value=a\\nb");
    }

    @Test
    void format_ShouldKeepStackTraceMultiline() {
        LogRecord r = new LogRecord(Level.WARNING, "failed");
        r.setThrown(new IllegalStateException("boom"));

        String out = formatter.format(r);

        assertThat(out).contains("java.lang.IllegalStateException: boom");
        assertThat(out.split("\n").length).isGreaterThan(3);
    }
}
