package mwagent.common;

import mwagent.vo.CommandVO;
import mwagent.vo.ResultVO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.*;

class CommonTest {

    @Test
    void testEscape() {
        // Test backslash escape
        String result = Common.escape("path\\to\\file");
        assertThat(result).isEqualTo("path\\\\to\\\\file");

        // Test quote escape
        result = Common.escape("say \"hello\"");
        assertThat(result).isEqualTo("say \\\"hello\\\"");

        // Test newline escape
        result = Common.escape("line1\nline2");
        assertThat(result).isEqualTo("line1\\nline2");

        // Test tab escape
        result = Common.escape("col1\tcol2");
        assertThat(result).isEqualTo("col1\\tcol2");

        // Test carriage return escape
        result = Common.escape("text\rmore");
        assertThat(result).isEqualTo("text\\rmore");
    }

    @Test
    void testEscapeSpecialCharacters() {
        // Test backspace
        String result = Common.escape("before\bafter");
        assertThat(result).isEqualTo("before\\bafter");

        // Test form feed
        result = Common.escape("page1\fpage2");
        assertThat(result).isEqualTo("page1\\fpage2");
    }

    @Test
    void testEscapeEmptyString() {
        String result = Common.escape("");
        assertThat(result).isEqualTo("");
    }

    @Test
    void testEscapeNull() {
        String result = Common.escape(null);
        assertThat(result).isNull();
    }

    @Test
    void testEscapeMixedSpecialCharacters() {
        String input = "path\\to\\file\nwith \"quotes\"\tand tabs";
        String result = Common.escape(input);
        assertThat(result).isEqualTo("path\\\\to\\\\file\\nwith \\\"quotes\\\"\\tand tabs");
    }

    @Test
    void testFillResult() {
        // Given
        ResultVO resultVO = new ResultVO();
        resultVO.setResult("test result");

        CommandVO commandVO = new CommandVO();
        commandVO.setHostName("server01");
        commandVO.setTargetFileName("test.txt");
        commandVO.setTargetFilePath("/data/");

        // When
        ResultVO filled = Common.fillResult(resultVO, commandVO);

        // Then
        assertThat(filled.getHostName()).isEqualTo("server01");
        assertThat(filled.getTargetFileName()).isEqualTo("test.txt");
        assertThat(filled.getTargetFilePath()).isEqualTo("/data/");
        assertThat(filled.getResult()).isEqualTo("test result");
    }

    @Test
    void testMakeOneResultArray() {
        // Given
        ResultVO resultVO = new ResultVO();
        resultVO.setResult("test result");

        CommandVO commandVO = new CommandVO();
        commandVO.setHostName("server01");
        commandVO.setTargetFileName("test.txt");

        // When
        ArrayList<ResultVO> results = Common.makeOneResultArray(resultVO, commandVO);

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getHostName()).isEqualTo("server01");
        assertThat(results.get(0).getTargetFileName()).isEqualTo("test.txt");
        assertThat(results.get(0).getResult()).isEqualTo("test result");
    }

    @Test
    void testFillResultPreservesExistingData() {
        // Given
        ResultVO resultVO = new ResultVO();
        resultVO.setResult("important data");
        resultVO.setResultHash("hash123");
        resultVO.setOk(true);

        CommandVO commandVO = new CommandVO();
        commandVO.setHostName("server02");

        // When
        ResultVO filled = Common.fillResult(resultVO, commandVO);

        // Then
        assertThat(filled.getResult()).isEqualTo("important data");
        assertThat(filled.getResultHash()).isEqualTo("hash123");
        assertThat(filled.isOk()).isTrue();
        assertThat(filled.getHostName()).isEqualTo("server02");
    }
}
