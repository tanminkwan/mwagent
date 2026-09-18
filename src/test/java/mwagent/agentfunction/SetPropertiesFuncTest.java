package mwagent.agentfunction;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import mwagent.common.Config;
import mwagent.vo.CommandVO;
import mwagent.vo.ResultVO;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for the set_properties agent function: token protection, all-or-nothing validation,
 * query mode and the shape of the returned configuration.
 */
class SetPropertiesFuncTest {

    private static final String ORIGINAL =
            "# MwManger agent configuration\n" +
            "server_url=https://app.mwm.local:20443/\n" +
            "token=secret-refresh-token\n" +
            "log_level=FINE\n" +
            "command_check_cycle=60\n" +
            "mqtt_credential=BGL7DzZjt0GU1OcCMtZthtnY\n" +
            "client.keystore.password=keystorePass\n";

    @TempDir
    Path tempDir;

    private File propertiesFile;

    /** Redirects the file access to a temp copy so the real agent.properties is never touched. */
    private class TestableFunc extends SetPropertiesFunc {
        @Override
        Map<String, String> applyAndRead(List<String> deleteKeys, Map<String, String> upsertPairs)
                throws Exception {
            return Config.applyAndReadProperties(propertiesFile, deleteKeys, upsertPairs);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        Config.getConfig().setLogger(Logger.getLogger("SetPropertiesFuncTest"));
        propertiesFile = tempDir.resolve("agent.properties").toFile();
        Files.write(propertiesFile.toPath(), ORIGINAL.getBytes(StandardCharsets.ISO_8859_1));
    }

    private CommandVO command(String additionalParams) {
        CommandVO c = new CommandVO();
        c.setTargetFileName("set_properties");
        c.setHostName("test-host");
        c.setAdditionalParams(additionalParams);
        return c;
    }

    private ResultVO run(String additionalParams) {
        ArrayList<ResultVO> results = new TestableFunc().exeCommand(command(additionalParams));
        assertThat(results).hasSize(1);
        return results.get(0);
    }

    private JSONObject resultJson(ResultVO rv) throws Exception {
        return (JSONObject) new JSONParser().parse(rv.getResult());
    }

    private String fileContent() throws Exception {
        return new String(Files.readAllBytes(propertiesFile.toPath()), StandardCharsets.ISO_8859_1);
    }

    // ---------- happy paths ----------

    @Test
    void upsertUpdatesExistingItem() throws Exception {
        ResultVO rv = run("{\"upsert\":[{\"log_level\":\"INFO\"}]}");

        assertThat(rv.isOk()).isTrue();
        assertThat(resultJson(rv).get("log_level")).isEqualTo("INFO");
        assertThat(fileContent()).contains("log_level=INFO");
    }

    @Test
    void upsertInsertsNewItem() throws Exception {
        ResultVO rv = run("{\"upsert\":[{\"mqtt_enabled\":\"true\"}]}");

        assertThat(rv.isOk()).isTrue();
        assertThat(resultJson(rv).get("mqtt_enabled")).isEqualTo("true");
        assertThat(fileContent()).endsWith("mqtt_enabled=true\n");
    }

    @Test
    void deleteRemovesExistingItem() throws Exception {
        ResultVO rv = run("{\"delete\":[\"log_level\"]}");

        assertThat(rv.isOk()).isTrue();
        assertThat(resultJson(rv)).doesNotContainKey("log_level");
        assertThat(fileContent()).doesNotContain("log_level");
    }

    @Test
    void deleteOfAbsentItemSucceedsAndChangesNothing() throws Exception {
        ResultVO rv = run("{\"delete\":[\"no_such_item\"]}");

        assertThat(rv.isOk()).isTrue();
        assertThat(fileContent()).isEqualTo(ORIGINAL);
    }

    @Test
    void deleteAndUpsertCombined() throws Exception {
        ResultVO rv = run("{\"delete\":[\"command_check_cycle\"],\"upsert\":[{\"log_level\":\"INFO\"}]}");

        assertThat(rv.isOk()).isTrue();
        JSONObject json = resultJson(rv);
        assertThat(json).doesNotContainKey("command_check_cycle");
        assertThat(json.get("log_level")).isEqualTo("INFO");
    }

    @Test
    void nonStringValuesAreStoredAsStrings() throws Exception {
        ResultVO rv = run("{\"upsert\":[{\"command_check_cycle\":30},{\"mqtt_enabled\":true}]}");

        assertThat(rv.isOk()).isTrue();
        assertThat(resultJson(rv).get("command_check_cycle")).isEqualTo("30");
        assertThat(resultJson(rv).get("mqtt_enabled")).isEqualTo("true");
    }

    @Test
    void emptyStringValueIsAllowed() throws Exception {
        ResultVO rv = run("{\"upsert\":[{\"kafka_broker_address\":\"\"}]}");

        assertThat(rv.isOk()).isTrue();
        assertThat(resultJson(rv).get("kafka_broker_address")).isEqualTo("");
    }

    @Test
    void unknownFieldsAreIgnored() throws Exception {
        ResultVO rv = run("{\"upsert\":[{\"log_level\":\"INFO\"}],\"whatever\":123}");

        assertThat(rv.isOk()).isTrue();
        assertThat(resultJson(rv).get("log_level")).isEqualTo("INFO");
    }

    // ---------- query mode ----------

    @Test
    void emptyRequestIsAQueryAndLeavesTheFileUntouched() throws Exception {
        ResultVO rv = run("{}");

        assertThat(rv.isOk()).isTrue();
        assertThat(fileContent()).isEqualTo(ORIGINAL);
        assertThat(resultJson(rv).get("log_level")).isEqualTo("FINE");
    }

    @Test
    void explicitlyEmptyListsAreAQueryToo() throws Exception {
        ResultVO rv = run("{\"delete\":[],\"upsert\":[]}");

        assertThat(rv.isOk()).isTrue();
        assertThat(fileContent()).isEqualTo(ORIGINAL);
        assertThat(resultJson(rv)).doesNotContainKey("token");
    }

    // ---------- token protection ----------

    @Test
    void deletingTokenIsRejected() throws Exception {
        ResultVO rv = run("{\"delete\":[\"token\"]}");

        assertThat(rv.isOk()).isFalse();
        assertThat(rv.getResult()).isEqualTo("Error:TOKEN_NOT_ALLOWED - 'token' cannot be deleted or upserted.");
        assertThat(fileContent()).isEqualTo(ORIGINAL);
    }

    @Test
    void upsertingTokenIsRejected() throws Exception {
        ResultVO rv = run("{\"upsert\":[{\"token\":\"forged\"}]}");

        assertThat(rv.isOk()).isFalse();
        assertThat(rv.getResult()).contains("TOKEN_NOT_ALLOWED");
        assertThat(fileContent()).isEqualTo(ORIGINAL);
    }

    @Test
    void tokenIsMatchedCaseInsensitivelyAndAfterTrim() throws Exception {
        for (String variant : new String[] {"TOKEN", "Token", " token ", "\ttoken"}) {
            ResultVO rv = run("{\"delete\":[\"" + variant.replace("\t", "\\t") + "\"]}");
            assertThat(rv.isOk()).as(variant).isFalse();
            assertThat(rv.getResult()).as(variant).contains("TOKEN_NOT_ALLOWED");
        }
        assertThat(fileContent()).isEqualTo(ORIGINAL);
    }

    @Test
    void aTokenAnywhereInTheRequestRejectsTheWholeBatch() throws Exception {
        ResultVO rv = run("{\"upsert\":[{\"log_level\":\"INFO\"},{\"token\":\"forged\"}]}");

        assertThat(rv.isOk()).isFalse();
        // log_level must not have been applied either
        assertThat(fileContent()).isEqualTo(ORIGINAL);
    }

    @Test
    void tokenIsNeverIncludedInTheResult() throws Exception {
        assertThat(resultJson(run("{}"))).doesNotContainKey("token");
        assertThat(resultJson(run("{\"upsert\":[{\"log_level\":\"INFO\"}]}"))).doesNotContainKey("token");
        assertThat(run("{}").getResult()).doesNotContain("secret-refresh-token");
    }

    @Test
    void tokenStaysInTheFile() throws Exception {
        run("{\"upsert\":[{\"log_level\":\"INFO\"}]}");

        assertThat(fileContent()).contains("token=secret-refresh-token");
    }

    // ---------- other sensitive items are fair game ----------

    @Test
    void sensitiveItemsCanBeUpsertedAndAreReturned() throws Exception {
        ResultVO rv = run("{\"upsert\":[{\"mqtt_credential\":\"newSecret\"}]}");

        assertThat(rv.isOk()).isTrue();
        assertThat(resultJson(rv).get("mqtt_credential")).isEqualTo("newSecret");
    }

    @Test
    void sensitiveItemsCanBeDeleted() throws Exception {
        ResultVO rv = run("{\"delete\":[\"client.keystore.password\"]}");

        assertThat(rv.isOk()).isTrue();
        assertThat(fileContent()).doesNotContain("client.keystore.password");
    }

    @Test
    void queryReturnsSensitiveValuesUnmasked() throws Exception {
        JSONObject json = resultJson(run("{}"));

        assertThat(json.get("client.keystore.password")).isEqualTo("keystorePass");
        assertThat(json.get("mqtt_credential")).isEqualTo("BGL7DzZjt0GU1OcCMtZthtnY");
    }

    // ---------- validation failures ----------

    @Test
    void missingAdditionalParamsIsRejected() throws Exception {
        ResultVO rv = run(null);

        assertThat(rv.isOk()).isFalse();
        assertThat(rv.getResult()).contains("INVALID_PARAMS");
        assertThat(fileContent()).isEqualTo(ORIGINAL);
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        ResultVO rv = run("{invalid");

        assertThat(rv.isOk()).isFalse();
        assertThat(rv.getResult()).contains("INVALID_PARAMS");
    }

    @Test
    void deleteMustBeAnArrayOfStrings() throws Exception {
        assertThat(run("{\"delete\":\"log_level\"}").getResult()).contains("INVALID_PARAMS");
        assertThat(run("{\"delete\":[123]}").getResult()).contains("INVALID_PARAMS");
    }

    @Test
    void upsertElementMustHaveExactlyOneKey() throws Exception {
        assertThat(run("{\"upsert\":[{\"a\":\"1\",\"b\":\"2\"}]}").getResult()).contains("INVALID_PARAMS");
        assertThat(run("{\"upsert\":[{}]}").getResult()).contains("INVALID_PARAMS");
        assertThat(run("{\"upsert\":[\"a\"]}").getResult()).contains("INVALID_PARAMS");
    }

    @Test
    void nullValueIsRejected() throws Exception {
        ResultVO rv = run("{\"upsert\":[{\"log_level\":null}]}");

        assertThat(rv.isOk()).isFalse();
        assertThat(rv.getResult()).contains("INVALID_PARAMS");
    }

    @Test
    void emptyItemNameIsRejected() throws Exception {
        assertThat(run("{\"delete\":[\"\"]}").getResult()).contains("INVALID_PARAMS");
        assertThat(run("{\"delete\":[\"   \"]}").getResult()).contains("INVALID_PARAMS");
    }

    @Test
    void itemNameWithLineBreakIsRejected() throws Exception {
        ResultVO rv = run("{\"upsert\":[{\"a\\nb\":\"1\"}]}");

        assertThat(rv.isOk()).isFalse();
        assertThat(rv.getResult()).contains("line break");
        assertThat(fileContent()).isEqualTo(ORIGINAL);
    }

    @Test
    void itemNameWithSeparatorCharIsRejected() throws Exception {
        // these are written unescaped, so they would silently split the key from the value
        for (String key : new String[] {"a=b", "a:b", "a b", "#a", "a\\\\b"}) {
            ResultVO rv = run("{\"upsert\":[{\"" + key + "\":\"1\"}]}");
            assertThat(rv.isOk()).as(key).isFalse();
            assertThat(rv.getResult()).as(key).contains("illegal character");
        }
        assertThat(fileContent()).isEqualTo(ORIGINAL);
    }

    @Test
    void conflictingKeysAreRejected() throws Exception {
        ResultVO rv = run("{\"delete\":[\"log_level\"],\"upsert\":[{\"log_level\":\"INFO\"}]}");

        assertThat(rv.isOk()).isFalse();
        assertThat(rv.getResult())
                .isEqualTo("Error:CONFLICTING_KEYS - 'log_level' appears in both delete and upsert.");
        assertThat(fileContent()).isEqualTo(ORIGINAL);
    }

    @Test
    void duplicatedKeysAreRejected() throws Exception {
        assertThat(run("{\"delete\":[\"a\",\"a\"]}").getResult()).contains("DUPLICATED_KEYS");
        assertThat(run("{\"upsert\":[{\"a\":\"1\"},{\"a\":\"2\"}]}").getResult()).contains("DUPLICATED_KEYS");
        assertThat(fileContent()).isEqualTo(ORIGINAL);
    }

    @Test
    void fileErrorIsReportedAsAFailedResultInsteadOfAnException() throws Exception {
        SetPropertiesFunc failing = new SetPropertiesFunc() {
            @Override
            Map<String, String> applyAndRead(List<String> deleteKeys, Map<String, String> upsertPairs)
                    throws Exception {
                throw new java.io.IOException("disk on fire");
            }
        };

        ArrayList<ResultVO> results = failing.exeCommand(command("{\"upsert\":[{\"a\":\"1\"}]}"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isOk()).isFalse();
        assertThat(results.get(0).getResult())
                .isEqualTo("Error:FILE_WRITE_ERROR - Failed to update agent.properties: disk on fire");
    }

    @Test
    void resultCarriesTheCommandIdentity() throws Exception {
        ResultVO rv = run("{}");

        assertThat(rv.getTargetFileName()).isEqualTo("set_properties");
        assertThat(rv.getHostName()).isEqualTo("test-host");
    }

    @Test
    void factoryResolvesTheFunction() {
        assertThat(AgentFuncFactory.getAgentFunc("set_properties")).isInstanceOf(SetPropertiesFunc.class);
    }
}
