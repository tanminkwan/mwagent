package mwagent.common;

import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

/**
 * SimpleFormatter that keeps each log message on one line.
 *
 * Many log calls include values from the server or a command (paths, parameters, responses).
 * A CR/LF inside such a value would let it forge extra log lines (CRLF_INJECTION_LOGS).
 * Escaping here covers every call site at once. Stack traces of logged exceptions are
 * printed by SimpleFormatter outside formatMessage, so they stay multi-line as before.
 */
public class SafeLogFormatter extends SimpleFormatter {

    @Override
    public synchronized String formatMessage(LogRecord record) {
        String message = super.formatMessage(record);
        if (message == null) {
            return null;
        }
        return message.replace("\r", "\\r").replace("\n", "\\n");
    }
}
