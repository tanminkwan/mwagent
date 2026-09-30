package mwagent;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

/**
 * MwAgent 테스트 - 잡히지 않은 예외가 로그 파일에 남는지
 */
class MwAgentTest {

    @Test
    void uncaughtExceptionHandler_ShouldLogSevereWithThrowable() throws Exception {
        Logger logger = Logger.getLogger("mwagent.test.uncaught");
        logger.setUseParentHandlers(false);
        final List<LogRecord> records = new ArrayList<LogRecord>();
        Handler capture = new Handler() {
            @Override public void publish(LogRecord r) { records.add(r); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        logger.addHandler(capture);

        Thread.UncaughtExceptionHandler handler = MwAgent.uncaughtExceptionLogger(logger);
        Thread t = new Thread(() -> { throw new IllegalStateException("boom"); }, "worker-1");
        t.setUncaughtExceptionHandler(handler);
        t.start();
        t.join();

        logger.removeHandler(capture);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getLevel()).isEqualTo(Level.SEVERE);
        assertThat(records.get(0).getMessage()).contains("worker-1");
        assertThat(records.get(0).getThrown()).isInstanceOf(IllegalStateException.class);
    }
}
