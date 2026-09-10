package mwagent.vo;

import static org.assertj.core.api.Assertions.*;

import org.json.simple.JSONArray;
import org.junit.jupiter.api.Test;

/**
 * RawCommandsVO 테스트
 */
class RawCommandsVOTest {

    @Test
    void constructor_ShouldCreateEmptyObject() {
        // When
        RawCommandsVO vo = new RawCommandsVO();

        // Then
        assertThat(vo).isNotNull();
    }

    @Test
    void setAndGetCommands_ShouldWork() {
        // Given
        RawCommandsVO vo = new RawCommandsVO();
        JSONArray commands = new JSONArray();
        commands.add("command1");
        commands.add("command2");

        // When
        vo.setCommands(commands);

        // Then
        assertThat(vo.getCommands()).isEqualTo(commands);
        assertThat(vo.getCommands()).hasSize(2);
    }

    @Test
    void setAndGetReturnCode_ShouldWork() {
        // Given
        RawCommandsVO vo = new RawCommandsVO();

        // When
        vo.setReturnCode(1);

        // Then
        assertThat(vo.getReturnCode()).isEqualTo(1);
    }

    @Test
    void setReturnCode_WithNegativeValue_ShouldWork() {
        // Given
        RawCommandsVO vo = new RawCommandsVO();

        // When
        vo.setReturnCode(-1);

        // Then
        assertThat(vo.getReturnCode()).isEqualTo(-1);
    }

    @Test
    void setCommands_WithEmptyArray_ShouldWork() {
        // Given
        RawCommandsVO vo = new RawCommandsVO();
        JSONArray emptyCommands = new JSONArray();

        // When
        vo.setCommands(emptyCommands);

        // Then
        assertThat(vo.getCommands()).isEmpty();
    }

    @Test
    void setMultipleValues_ShouldRetainBoth() {
        // Given
        RawCommandsVO vo = new RawCommandsVO();
        JSONArray commands = new JSONArray();
        commands.add("test");

        // When
        vo.setCommands(commands);
        vo.setReturnCode(100);

        // Then
        assertThat(vo.getCommands()).isEqualTo(commands);
        assertThat(vo.getReturnCode()).isEqualTo(100);
    }
}
