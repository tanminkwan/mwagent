package mwagent.vo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CommandVOTest {

    @Test
    void testCommandVOGettersAndSetters() {
        // Given
        CommandVO commandVO = new CommandVO();

        // When
        commandVO.setCommandId("CMD-123");
        commandVO.setRepetitionSeq(5L);
        commandVO.setHostName("server01");
        commandVO.setTargetFileName("test.sh");
        commandVO.setTargetFilePath("/scripts/");
        commandVO.setResultHash("abc123");
        commandVO.setAdditionalParams("-v --force");
        commandVO.setResultReceiver("SERVER");
        commandVO.setTargetObject("t_results");

        // Then
        assertThat(commandVO.getCommandId()).isEqualTo("CMD-123");
        assertThat(commandVO.getRepetitionSeq()).isEqualTo(5L);
        assertThat(commandVO.getHostName()).isEqualTo("server01");
        assertThat(commandVO.getTargetFileName()).isEqualTo("test.sh");
        assertThat(commandVO.getTargetFilePath()).isEqualTo("/scripts/");
        assertThat(commandVO.getResultHash()).isEqualTo("abc123");
        assertThat(commandVO.getAdditionalParams()).isEqualTo("-v --force");
        assertThat(commandVO.getResultReceiver()).isEqualTo("SERVER");
        assertThat(commandVO.getTargetObject()).isEqualTo("t_results");
    }

    @Test
    void testCommandVOToString() {
        // Given
        CommandVO commandVO = new CommandVO();
        commandVO.setCommandId("CMD-123");
        commandVO.setHostName("server01");

        // When
        String result = commandVO.toString();

        // Then
        assertThat(result).contains("CMD-123");
        assertThat(result).contains("server01");
        assertThat(result).contains("CommandVO");
    }

    @Test
    void testCommandVODefaultValues() {
        // Given & When
        CommandVO commandVO = new CommandVO();

        // Then
        assertThat(commandVO.getCommandId()).isEqualTo("");
        assertThat(commandVO.getRepetitionSeq()).isEqualTo(0L);
        assertThat(commandVO.getHostName()).isNull();
        assertThat(commandVO.getTargetFileName()).isNull();
        assertThat(commandVO.getTargetFilePath()).isNull();
    }
}
