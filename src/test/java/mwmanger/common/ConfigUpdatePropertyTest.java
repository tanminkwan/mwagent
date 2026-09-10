package mwagent.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for Config.updatePropertyInFile / updatePropertyLegacy:
 * the token update must only touch the "token" line, preserve everything else,
 * and never leave a truncated file behind even under concurrent callers.
 */
class ConfigUpdatePropertyTest {

    private static final String ORIGINAL =
            "# MwManger agent configuration\n" +
            "server_url=https://app.mwm.local:20443/\n" +
            "\n" +
            "token=old-token\n" +
            "get_command_uri=/api/v1/command\n" +
            "log_level=FINE\n" +
            "host_name_var=HOSTNAME\n" +
            "user_name_var=USER\n" +
            "command_check_cycle=60\n" +
            "post_agent_uri=/api/v1/agent/agent\n";

    @TempDir
    Path tempDir;

    private File writeFile(String name, String content) throws IOException {
        File f = tempDir.resolve(name).toFile();
        Files.write(f.toPath(), content.getBytes(Charset.defaultCharset()));
        return f;
    }

    private String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), Charset.defaultCharset());
    }

    private Properties load(File f) throws IOException {
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(f)) {
            p.load(in);
        }
        return p;
    }

    @Test
    void replacesOnlyTokenLineAndPreservesEverythingElse() throws IOException {
        File f = writeFile("agent.properties", ORIGINAL);

        Config.updatePropertyInFile(f, "token", "new-token-123");

        String expected = ORIGINAL.replace("token=old-token", "token=new-token-123");
        assertThat(read(f)).isEqualTo(expected);

        Properties p = load(f);
        assertThat(p.getProperty("token")).isEqualTo("new-token-123");
        assertThat(p.getProperty("server_url")).isEqualTo("https://app.mwm.local:20443/");
        assertThat(p.size()).isEqualTo(8);
    }

    @Test
    void appendsKeyWhenMissing() throws IOException {
        File f = writeFile("agent.properties", "server_url=https://x/\nlog_level=INFO"); // no trailing newline

        Config.updatePropertyInFile(f, "token", "abc");

        assertThat(read(f)).isEqualTo("server_url=https://x/\nlog_level=INFO\ntoken=abc\n");
        assertThat(load(f).getProperty("token")).isEqualTo("abc");
    }

    @Test
    void preservesCrLfLineEndings() throws IOException {
        File f = writeFile("agent.properties", ORIGINAL.replace("\n", "\r\n"));

        Config.updatePropertyInFile(f, "token", "crlf-token");

        String content = read(f);
        assertThat(content).isEqualTo(ORIGINAL.replace("\n", "\r\n").replace("token=old-token", "token=crlf-token"));
        assertThat(content).doesNotContain("\n\n"); // no stray LF-only lines introduced
    }

    @Test
    void matchesKeyWrittenWithColonOrWhitespaceSeparator() throws IOException {
        File f = writeFile("agent.properties", "  token : old\nother=1\n");

        Config.updatePropertyInFile(f, "token", "new");

        assertThat(read(f)).isEqualTo("token=new\nother=1\n");
    }

    @Test
    void doesNotTouchKeysThatMerelyStartWithTheSamePrefix() throws IOException {
        File f = writeFile("agent.properties", "token_type=bearer\n#token=commented\ntoken=old\n");

        Config.updatePropertyInFile(f, "token", "new");

        assertThat(read(f)).isEqualTo("token_type=bearer\n#token=commented\ntoken=new\n");
    }

    @Test
    void removesContinuationLinesOfOldEntry() throws IOException {
        File f = writeFile("agent.properties", "a=1\ntoken=part1\\\n    part2\nb=2\n");

        Config.updatePropertyInFile(f, "token", "single");

        assertThat(read(f)).isEqualTo("a=1\ntoken=single\nb=2\n");
        assertThat(load(f).getProperty("token")).isEqualTo("single");
    }

    @Test
    void valueRoundTripsThroughPropertiesLoad() throws IOException {
        File f = writeFile("agent.properties", "token=old\n");
        String tricky = " lead\\slash=eq:colon#hash\ttab";

        Config.updatePropertyInFile(f, "token", tricky);

        assertThat(load(f).getProperty("token")).isEqualTo(tricky);
    }

    @Test
    void valueWithSeparatorsAndCommentCharsRoundTrips() throws IOException {
        File f = writeFile("agent.properties", "token=old\nnext=1\n");
        // '=' ':' '#' '!' inside a value must not split the key or start a comment
        String tricky = "a=b:c#d!e==f::g";

        Config.updatePropertyInFile(f, "token", tricky);

        assertThat(read(f)).isEqualTo("token=" + tricky + "\nnext=1\n");
        Properties p = load(f);
        assertThat(p.getProperty("token")).isEqualTo(tricky);
        assertThat(p.getProperty("next")).isEqualTo("1");
    }

    @Test
    void valueWithLineBreaksCannotInjectExtraProperties() throws IOException {
        File f = writeFile("agent.properties", "token=old\nnext=1\n");
        String malicious = "abc\nserver_url=http://evil/\r\n#x";

        Config.updatePropertyInFile(f, "token", malicious);

        Properties p = load(f);
        assertThat(p.size()).isEqualTo(2);
        assertThat(p.getProperty("server_url")).isNull();
        assertThat(p.getProperty("token")).isEqualTo(malicious);
    }

    @Test
    void trailingBackslashDoesNotSwallowNextLine() throws IOException {
        File f = writeFile("agent.properties", "token=old\nnext=1\n");

        Config.updatePropertyInFile(f, "token", "ends-with-backslash\\");

        Properties p = load(f);
        assertThat(p.getProperty("token")).isEqualTo("ends-with-backslash\\");
        assertThat(p.getProperty("next")).isEqualTo("1");
    }

    @Test
    void nonAsciiValueIsWrittenAsAsciiEscapesAndOtherLinesKeepTheirBytes() throws IOException {
        File f = tempDir.resolve("agent.properties").toFile();
        // UTF-8 Korean comment: must survive byte-for-byte no matter the platform charset
        byte[] original = ("# \uD55C\uAE00 \uC8FC\uC11D\ntoken=old\nlog_dir=/var/log/\uC5D0\uC774\uC804\uD2B8\n")
                .getBytes(StandardCharsets.UTF_8);
        Files.write(f.toPath(), original);

        Config.updatePropertyInFile(f, "token", "\uD1A0\uD070-\u00E9-x");

        byte[] after = Files.readAllBytes(f.toPath());
        String afterStr = new String(after, StandardCharsets.UTF_8);
        assertThat(afterStr).isEqualTo(
                "# \uD55C\uAE00 \uC8FC\uC11D\ntoken=\\uD1A0\\uD070-\\u00E9-x\nlog_dir=/var/log/\uC5D0\uC774\uC804\uD2B8\n");
        // every byte of the token line is ASCII
        for (byte b : "token=\\uD1A0\\uD070-\\u00E9-x".getBytes(StandardCharsets.US_ASCII)) {
            assertThat(b).isGreaterThanOrEqualTo((byte) 0);
        }
        assertThat(load(f).getProperty("token")).isEqualTo("\uD1A0\uD070-\u00E9-x");
    }

    @Test
    void literalUnicodeEscapeSequenceInValueRoundTrips() throws IOException {
        File f = writeFile("agent.properties", "token=old\n");
        String literal = "abc\\u0041def"; // six literal chars backslash-u-0-0-4-1, not the letter A

        Config.updatePropertyInFile(f, "token", literal);

        assertThat(load(f).getProperty("token")).isEqualTo(literal);
    }

    @Test
    void leavesNoTempFilesBehind() throws IOException {
        File f = writeFile("agent.properties", ORIGINAL);

        Config.updatePropertyInFile(f, "token", "x");

        assertThat(tempDir.toFile().list()).containsExactly("agent.properties");
    }

    @Test
    void concurrentUpdatesNeverLoseOtherKeys() throws Exception {
        final File f = writeFile("agent.properties", ORIGINAL);
        final List<Throwable> errors = new ArrayList<Throwable>();

        Runnable r = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 300; i++) {
                    try {
                        Config.updatePropertyInFile(f, "token", "tok-" + Thread.currentThread().getName() + "-" + i);
                    } catch (Throwable t) {
                        synchronized (errors) { errors.add(t); }
                    }
                }
            }
        };
        Thread t1 = new Thread(r, "A");
        Thread t2 = new Thread(r, "B");
        Thread t3 = new Thread(r, "C");
        t1.start(); t2.start(); t3.start();
        t1.join(); t2.join(); t3.join();

        assertThat(errors).isEmpty();
        Properties p = load(f);
        assertThat(p.size()).as("all 8 keys must survive concurrent token updates").isEqualTo(8);
        assertThat(p.getProperty("server_url")).isEqualTo("https://app.mwm.local:20443/");
        assertThat(p.getProperty("token")).startsWith("tok-");
        assertThat(read(f)).startsWith("# MwManger agent configuration\n");
    }

    @Test
    void updatePropertyLegacyRejectsNullValueWithoutTouchingFile() {
        Config config = Config.getConfig();
        if (config.getLogger() == null) {
            config.setLogger(Logger.getLogger("ConfigUpdatePropertyTest"));
        }

        assertThat(config.updatePropertyLegacy("token", null)).isEqualTo(-2);
        assertThat(config.updatePropertyLegacy(null, "x")).isEqualTo(-2);
        assertThat(config.updatePropertyLegacy("  ", "x")).isEqualTo(-2);
    }
}
