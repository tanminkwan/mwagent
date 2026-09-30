package mwagent.common;

import java.util.regex.Pattern;

/**
 * Makes external values (server responses, command results) safe to write to the log.
 * - masks token / credential values, URL signatures and JWTs
 * - escapes CR/LF so one value cannot forge extra log lines
 * - truncates long values
 */
public final class LogSafe {

    private static final int DEFAULT_MAX = 200;

    // "key":"value" where key contains token/password/credential/secret.
    // Also matches JSON nested as a string (\"key\":\"value\"), e.g. additional_params inside a command
    private static final Pattern SECRET_JSON_FIELD = Pattern.compile(
            "(\\\\?\"[^\"\\\\]*(?:token|password|credential|secret)[^\"\\\\]*\\\\?\"\\s*:\\s*\\\\?\")"
            + "(?:[^\"\\\\]|\\\\[^\"])*(\\\\?\")",
            Pattern.CASE_INSENSITIVE);

    // URL query parameters that carry credentials (presigned URL signatures, tokens)
    private static final Pattern SECRET_QUERY_PARAM = Pattern.compile(
            "([?&](?:[A-Za-z0-9_.-]*(?:signature|token|credential|secret|password)[A-Za-z0-9_.-]*|sig)=)[^&\"\\\\\\s]*",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*");

    private LogSafe() {
    }

    public static String safe(String value) {
        return safe(value, DEFAULT_MAX);
    }

    public static String safe(String value, int max) {
        if (value == null) {
            return "null";
        }
        String s = SECRET_JSON_FIELD.matcher(value).replaceAll("$1***$2");
        s = SECRET_QUERY_PARAM.matcher(s).replaceAll("$1***");
        s = JWT.matcher(s).replaceAll("***");
        s = s.replace("\r", "\\r").replace("\n", "\\n");
        if (s.length() > max) {
            s = s.substring(0, max) + "...(" + value.length() + " chars)";
        }
        return s;
    }
}
