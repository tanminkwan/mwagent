package mwagent.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for the batch delete/upsert used by the set_properties agent function:
 * untouched lines must survive verbatim, the whole batch must land in one atomic write,
 * and an empty batch must not write at all.
 */
class ConfigApplyPropertiesTest {

    private static final String ORIGINAL =
            "# MwManger agent configuration\n" +
            "server_url=https://app.mwm.local:20443/\n" +
            "\n" +
            "token=secret-refresh-token\n" +
            "get_command_uri=/api/v1/command\n" +
            "log_level=FINE\n" +
            "command_check_cycle=60\n";

    @TempDir
    Path tempDir;

    private File writeFile(String content) throws IOException {
        File f = tempDir.resolve("agent.properties").toFile();
        Files.write(f.toPath(), content.getBytes(StandardCharsets.ISO_8859_1));
        return f;
    }

    private String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.ISO_8859_1);
    }

    private Properties load(File f) throws IOException {
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(f)) {
            p.load(in);
        }
        return p;
    }

    private Map<String, String> pairs(String... keyValues) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    @Test
    void upsertUpdatesExistingItemInPlace() throws IOException {
        File f = writeFile(ORIGINAL);

        Config.applyPropertiesInFile(f, Collections.<String>emptyList(), pairs("log_level", "INFO"));

        assertThat(load(f).getProperty("log_level")).isEqualTo("INFO");
        assertThat(read(f)).isEqualTo(ORIGINAL.replace("log_level=FINE", "log_level=INFO"));
    }

    @Test
    void upsertInsertsMissingItemAtEnd() throws IOException {
        File f = writeFile(ORIGINAL);

        Config.applyPropertiesInFile(f, Collections.<String>emptyList(), pairs("mqtt_enabled", "true"));

        assertThat(read(f)).isEqualTo(ORIGINAL + "mqtt_enabled=true\n");
    }

    @Test
    void deleteRemovesOnlyTheTargetLine() throws IOException {
        File f = writeFile(ORIGINAL);

        Config.applyPropertiesInFile(f, Arrays.asList("log_level"), Collections.<String, String>emptyMap());

        assertThat(read(f)).isEqualTo(ORIGINAL.replace("log_level=FINE\n", ""));
        assertThat(load(f)).doesNotContainKey("log_level");
    }

    @Test
    void deleteOfAbsentItemIsIgnored() throws IOException {
        File f = writeFile(ORIGINAL);

        Config.applyPropertiesInFile(f, Arrays.asList("no_such_item"), Collections.<String, String>emptyMap());

        assertThat(read(f)).isEqualTo(ORIGINAL);
    }

    @Test
    void deleteAndUpsertApplyInASinglePass() throws IOException {
        File f = writeFile(ORIGINAL);

        Config.applyPropertiesInFile(f,
                Arrays.asList("command_check_cycle"),
                pairs("log_level", "INFO", "mqtt_enabled", "true"));

        Properties p = load(f);
        assertThat(p).doesNotContainKey("command_check_cycle");
        assertThat(p.getProperty("log_level")).isEqualTo("INFO");
        assertThat(p.getProperty("mqtt_enabled")).isEqualTo("true");
        // comments, blank lines and untouched entries keep their exact position
        assertThat(read(f)).startsWith("# MwManger agent configuration\n"
                + "server_url=https://app.mwm.local:20443/\n"
                + "\n"
                + "token=secret-refresh-token\n");
    }

    @Test
    void commentsAndBlankLinesArePreserved() throws IOException {
        String content = "# head\n\n! bang comment\nlog_level=FINE\n\n# tail\n";
        File f = writeFile(content);

        Config.applyPropertiesInFile(f, Collections.<String>emptyList(), pairs("log_level", "INFO"));

        assertThat(read(f)).isEqualTo("# head\n\n! bang comment\nlog_level=INFO\n\n# tail\n");
    }

    @Test
    void commentedOutItemIsNotTreatedAsExisting() throws IOException {
        File f = writeFile("# log_level=OFF\nserver_url=x\n");

        Config.applyPropertiesInFile(f, Collections.<String>emptyList(), pairs("log_level", "INFO"));

        assertThat(read(f)).isEqualTo("# log_level=OFF\nserver_url=x\nlog_level=INFO\n");
    }

    @Test
    void deleteRemovesContinuationLines() throws IOException {
        File f = writeFile("a=one\\\n  two\nb=2\n");

        Config.applyPropertiesInFile(f, Arrays.asList("a"), Collections.<String, String>emptyMap());

        assertThat(read(f)).isEqualTo("b=2\n");
    }

    @Test
    void upsertReplacesContinuationLines() throws IOException {
        File f = writeFile("a=one\\\n  two\nb=2\n");

        Config.applyPropertiesInFile(f, Collections.<String>emptyList(), pairs("a", "single"));

        assertThat(read(f)).isEqualTo("a=single\nb=2\n");
    }

    @Test
    void upsertLeavesNoStaleDuplicateBehind() throws IOException {
        // Properties.load() lets the last entry win, so a duplicate further down would
        // silently override the value we just wrote.
        File f = writeFile("log_level=FINE\nserver_url=x\nlog_level=OFF\n");

        Config.applyPropertiesInFile(f, Collections.<String>emptyList(), pairs("log_level", "INFO"));

        assertThat(read(f)).isEqualTo("log_level=INFO\nserver_url=x\n");
        assertThat(load(f).getProperty("log_level")).isEqualTo("INFO");
    }

    @Test
    void deleteRemovesEveryOccurrence() throws IOException {
        File f = writeFile("log_level=FINE\nserver_url=x\nlog_level=OFF\n");

        Config.applyPropertiesInFile(f, Arrays.asList("log_level"), Collections.<String, String>emptyMap());

        assertThat(read(f)).isEqualTo("server_url=x\n");
    }

    @Test
    void valuesAreEscapedAndReadBackVerbatim() throws IOException {
        File f = writeFile(ORIGINAL);
        String tricky = "\uAC12=1\n2\ttab\\end";

        Config.applyPropertiesInFile(f, Collections.<String>emptyList(), pairs("tricky", tricky));

        assertThat(load(f).getProperty("tricky")).isEqualTo(tricky);
        // written as pure ASCII, so the file stays charset independent
        assertThat(read(f)).contains("tricky=\\uAC12=1\\n2\\ttab\\\\end");
    }

    @Test
    void crlfLineEndingsArePreserved() throws IOException {
        File f = writeFile("# head\r\nlog_level=FINE\r\nserver_url=x\r\n");

        Config.applyPropertiesInFile(f, Collections.<String>emptyList(),
                pairs("log_level", "INFO", "added", "1"));

        assertThat(read(f)).isEqualTo("# head\r\nlog_level=INFO\r\nserver_url=x\r\nadded=1\r\n");
    }

    @Test
    void fileWithoutTrailingNewlineGetsOneBeforeAppending() throws IOException {
        File f = writeFile("a=1");

        Config.applyPropertiesInFile(f, Collections.<String>emptyList(), pairs("b", "2"));

        assertThat(read(f)).isEqualTo("a=1\nb=2\n");
    }

    @Test
    void readAllPropertiesKeepsFileOrderAndDecodesValues() throws IOException {
        File f = writeFile(ORIGINAL + "unicode=\\uAC12\n");

        Map<String, String> all = Config.readAllProperties(f);

        assertThat(all.keySet()).containsExactly(
                "server_url", "token", "get_command_uri", "log_level", "command_check_cycle", "unicode");
        assertThat(all.get("unicode")).isEqualTo("\uAC12");
    }

    @Test
    void applyAndReadReturnsUpdatedContent() throws IOException {
        File f = writeFile(ORIGINAL);

        Map<String, String> all = Config.applyAndReadProperties(f,
                Arrays.asList("command_check_cycle"), pairs("log_level", "INFO"));

        assertThat(all.get("log_level")).isEqualTo("INFO");
        assertThat(all).doesNotContainKey("command_check_cycle");
        // the caller, not Config, is responsible for hiding the token
        assertThat(all).containsKey("token");
    }

    @Test
    void emptyBatchDoesNotWriteTheFile() throws IOException {
        File f = writeFile(ORIGINAL);
        long before = f.lastModified();
        f.setLastModified(before - 10000L);
        long stamp = f.lastModified();

        Map<String, String> all = Config.applyAndReadProperties(f,
                new ArrayList<String>(), new LinkedHashMap<String, String>());

        assertThat(f.lastModified()).isEqualTo(stamp);
        assertThat(read(f)).isEqualTo(ORIGINAL);
        assertThat(all.get("log_level")).isEqualTo("FINE");
    }

    @Test
    void noTempFileIsLeftBehind() throws IOException {
        File f = writeFile(ORIGINAL);

        Config.applyPropertiesInFile(f, Arrays.asList("log_level"), pairs("added", "1"));

        List<String> names = Arrays.asList(tempDir.toFile().list());
        assertThat(names).containsExactly("agent.properties");
    }

    @Test
    void tokenUpdateStillWorksAlongsideBatchUpdates() throws IOException {
        // the token path must keep behaving as before (it shares the same lock and file)
        File f = writeFile(ORIGINAL);

        Config.updatePropertyInFile(f, "token", "new-token");
        Config.applyPropertiesInFile(f, Collections.<String>emptyList(), pairs("log_level", "INFO"));

        Properties p = load(f);
        assertThat(p.getProperty("token")).isEqualTo("new-token");
        assertThat(p.getProperty("log_level")).isEqualTo("INFO");
    }
}
