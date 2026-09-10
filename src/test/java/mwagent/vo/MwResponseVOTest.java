package mwagent.vo;

import static org.assertj.core.api.Assertions.*;

import org.json.simple.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * MwResponseVO 테스트
 */
class MwResponseVOTest {

    @Test
    void constructor_ShouldCreateEmptyObject() {
        // When
        MwResponseVO vo = new MwResponseVO();

        // Then
        assertThat(vo).isNotNull();
    }

    @Test
    void setAndGetResponse_ShouldWork() {
        // Given
        MwResponseVO vo = new MwResponseVO();
        JSONObject response = new JSONObject();
        response.put("status", "success");

        // When
        vo.setResponse(response);

        // Then
        assertThat(vo.getResponse()).isEqualTo(response);
        assertThat(vo.getResponse().get("status")).isEqualTo("success");
    }

    @Test
    void setAndGetStatusCode_ShouldWork() {
        // Given
        MwResponseVO vo = new MwResponseVO();

        // When
        vo.setStatusCode(200);

        // Then
        assertThat(vo.getStatusCode()).isEqualTo(200);
    }

    @Test
    void setAndGetFileName_ShouldWork() {
        // Given
        MwResponseVO vo = new MwResponseVO();

        // When
        vo.setFileName("test.txt");

        // Then
        assertThat(vo.getFileName()).isEqualTo("test.txt");
    }

    @Test
    void setAndGetFileLocation_ShouldWork() {
        // Given
        MwResponseVO vo = new MwResponseVO();

        // When
        vo.setFileLocation("/tmp/test.txt");

        // Then
        assertThat(vo.getFileLocation()).isEqualTo("/tmp/test.txt");
    }

    @Test
    void setMultipleValues_ShouldRetainAll() {
        // Given
        MwResponseVO vo = new MwResponseVO();
        JSONObject response = new JSONObject();
        response.put("key", "value");

        // When
        vo.setResponse(response);
        vo.setStatusCode(201);
        vo.setFileName("data.json");
        vo.setFileLocation("/opt/data.json");

        // Then
        assertThat(vo.getResponse()).isEqualTo(response);
        assertThat(vo.getStatusCode()).isEqualTo(201);
        assertThat(vo.getFileName()).isEqualTo("data.json");
        assertThat(vo.getFileLocation()).isEqualTo("/opt/data.json");
    }

    @Test
    void toString_ShouldContainAllFields() {
        // Given
        MwResponseVO vo = new MwResponseVO();
        JSONObject response = new JSONObject();
        response.put("status", "ok");
        vo.setResponse(response);
        vo.setStatusCode(200);
        vo.setFileName("test.txt");
        vo.setFileLocation("/tmp/test.txt");

        // When
        String result = vo.toString();

        // Then
        assertThat(result)
            .contains("response=")
            .contains("statusCode=200")
            .contains("fileName=test.txt")
            .contains("fullFileName=/tmp/test.txt");
    }

    @Test
    void setStatusCode_WithErrorCode_ShouldWork() {
        // Given
        MwResponseVO vo = new MwResponseVO();

        // When
        vo.setStatusCode(500);

        // Then
        assertThat(vo.getStatusCode()).isEqualTo(500);
    }

    @Test
    void fileName_DefaultValue_ShouldBeEmpty() {
        // Given
        MwResponseVO vo = new MwResponseVO();

        // Then
        assertThat(vo.getFileName()).isEqualTo("");
    }

    @Test
    void fileLocation_DefaultValue_ShouldBeEmpty() {
        // Given
        MwResponseVO vo = new MwResponseVO();

        // Then
        assertThat(vo.getFileLocation()).isEqualTo("");
    }
}
