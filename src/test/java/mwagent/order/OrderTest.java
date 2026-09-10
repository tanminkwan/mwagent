package mwagent.order;

import mwagent.vo.CommandVO;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class OrderTest {

    private TestOrder testOrder;

    // Test implementation of abstract Order class
    static class TestOrder extends Order {
        public TestOrder(JSONObject command) {
            super(command);
        }

        @Override
        public int execute() {
            return 1;
        }

        // Expose protected methods for testing
        public String testReplaceParam(String text) {
            return replaceParam(text);
        }

        public String testGetHash(String content) throws NoSuchAlgorithmException {
            return getHash(content);
        }

        public CommandVO getCommandVo() {
            return commandVo;
        }
    }

    @BeforeEach
    void setUp() {
        JSONObject command = new JSONObject();
        command.put("command_id", "CMD-123");
        command.put("repetition_seq", 1L);
        command.put("target_file_name", "test.sh");
        command.put("target_file_path", "/scripts/");
        command.put("result_hash", "");
        command.put("additional_params", "");
        command.put("result_receiver", "SERVER");
        command.put("target_object", "t_results");

        testOrder = new TestOrder(command);
    }

    @Test
    void testConvertCommand() {
        // Given - setUp creates the order

        // When
        CommandVO commandVO = testOrder.getCommandVo();

        // Then
        assertThat(commandVO.getCommandId()).isEqualTo("CMD-123");
        assertThat(commandVO.getRepetitionSeq()).isEqualTo(1L);
        assertThat(commandVO.getTargetFileName()).isEqualTo("test.sh");
        assertThat(commandVO.getTargetFilePath()).isEqualTo("/scripts/");
        assertThat(commandVO.getResultReceiver()).isEqualTo("SERVER");
        assertThat(commandVO.getTargetObject()).isEqualTo("t_results");
    }

    @Test
    void testReplaceParamNoPlaceholder() {
        // Given
        String text = "simple text without placeholder";

        // When
        String result = testOrder.testReplaceParam(text);

        // Then
        assertThat(result).isEqualTo("simple text without placeholder");
    }

    @Test
    void testReplaceParamEmpty() {
        // Given
        String text = "";

        // When
        String result = testOrder.testReplaceParam(text);

        // Then
        assertThat(result).isEqualTo("");
    }

    @Test
    void testReplaceParamNull() {
        // Given
        String text = null;

        // When
        String result = testOrder.testReplaceParam(text);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void testGetHashSHA256() throws NoSuchAlgorithmException {
        // Given
        String content = "test content for hashing";

        // When
        String hash = testOrder.testGetHash(content);

        // Then
        assertThat(hash).isNotNull();
        assertThat(hash).hasSize(64); // SHA-256 produces 64 hex characters
        assertThat(hash).matches("[0-9A-F]{64}"); // All uppercase hex
    }

    @Test
    void testGetHashConsistency() throws NoSuchAlgorithmException {
        // Given
        String content = "same content";

        // When
        String hash1 = testOrder.testGetHash(content);
        String hash2 = testOrder.testGetHash(content);

        // Then
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void testGetHashDifferentContent() throws NoSuchAlgorithmException {
        // Given
        String content1 = "content 1";
        String content2 = "content 2";

        // When
        String hash1 = testOrder.testGetHash(content1);
        String hash2 = testOrder.testGetHash(content2);

        // Then
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void testGetHashEmptyString() throws NoSuchAlgorithmException {
        // Given
        String content = "";

        // When
        String hash = testOrder.testGetHash(content);

        // Then
        assertThat(hash).isNotNull();
        assertThat(hash).hasSize(64);
        // Known SHA-256 hash of empty string
        assertThat(hash).isEqualTo("E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855");
    }

    @Test
    void testStaticConstants() {
        // Test static constant values
        assertThat(Order.KAFKA).isEqualTo("KAFKA");
        assertThat(Order.SERVER).isEqualTo("SERVER");
        assertThat(Order.SERVER_N_KAFKA).isEqualTo("SERVER_N_KAFKA");
    }

    @Test
    void testConvertCommandWithNullValues() {
        // Given
        JSONObject command = new JSONObject();
        command.put("command_id", "CMD-456");
        command.put("repetition_seq", 0L);
        // Leave other fields null/missing

        // When
        TestOrder order = new TestOrder(command);
        CommandVO commandVO = order.getCommandVo();

        // Then
        assertThat(commandVO.getCommandId()).isEqualTo("CMD-456");
        assertThat(commandVO.getRepetitionSeq()).isEqualTo(0L);
        // These should be null since not provided
        assertThat(commandVO.getTargetFileName()).isNull();
        assertThat(commandVO.getTargetFilePath()).isNull();
    }
}
