package mwagent.vo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ResultVOTest {

    @Test
    void testResultVOGettersAndSetters() {
        // Given
        ResultVO resultVO = new ResultVO();

        // When
        resultVO.setTargetFileName("test.txt");
        resultVO.setHostName("server01");
        resultVO.setTargetFilePath("/data/");
        resultVO.setResult("Success");
        resultVO.setObjectAggregationKey("key123");
        resultVO.setResultHash("hash456");
        resultVO.setOk(true);

        // Then
        assertThat(resultVO.getTargetFileName()).isEqualTo("test.txt");
        assertThat(resultVO.getHostName()).isEqualTo("server01");
        assertThat(resultVO.getTargetFilePath()).isEqualTo("/data/");
        assertThat(resultVO.getResult()).isEqualTo("Success");
        assertThat(resultVO.getObjectAggregationKey()).isEqualTo("key123");
        assertThat(resultVO.getResultHash()).isEqualTo("hash456");
        assertThat(resultVO.isOk()).isTrue();
    }

    @Test
    void testResultVOToString() {
        // Given
        ResultVO resultVO = new ResultVO();
        resultVO.setTargetFileName("test.txt");
        resultVO.setHostName("server01");
        resultVO.setResult("Success");

        // When
        String result = resultVO.toString();

        // Then
        assertThat(result).contains("test.txt");
        assertThat(result).contains("server01");
        assertThat(result).contains("Success");
        assertThat(result).contains("ResultVO");
    }

    @Test
    void testResultVODefaultValues() {
        // Given & When
        ResultVO resultVO = new ResultVO();

        // Then
        assertThat(resultVO.getTargetFileName()).isEqualTo("");
        assertThat(resultVO.getHostName()).isEqualTo("");
        assertThat(resultVO.getTargetFilePath()).isEqualTo("");
        assertThat(resultVO.getResult()).isEqualTo("");
        assertThat(resultVO.getObjectAggregationKey()).isEqualTo("");
        assertThat(resultVO.getResultHash()).isEqualTo("");
        assertThat(resultVO.isOk()).isFalse();
    }

    @Test
    void testResultVOBooleanFlag() {
        // Given
        ResultVO resultVO = new ResultVO();

        // When & Then - Initially false
        assertThat(resultVO.isOk()).isFalse();

        // When - Set to true
        resultVO.setOk(true);

        // Then
        assertThat(resultVO.isOk()).isTrue();
    }
}
