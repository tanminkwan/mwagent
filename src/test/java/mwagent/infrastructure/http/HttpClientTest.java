package mwagent.infrastructure.http;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for HttpClient interface and HttpResponse.
 */
@DisplayName("HttpClient Interface Tests")
class HttpClientTest {

    @Test
    @DisplayName("HttpResponse isSuccess returns true for 2xx status codes")
    void httpResponse_IsSuccess_2xxCodes() {
        HttpClient.HttpResponse response200 = new HttpClient.HttpResponse(200, "OK", new HashMap<>());
        HttpClient.HttpResponse response201 = new HttpClient.HttpResponse(201, "Created", new HashMap<>());
        HttpClient.HttpResponse response299 = new HttpClient.HttpResponse(299, "OK", new HashMap<>());

        assertTrue(response200.isSuccess());
        assertTrue(response201.isSuccess());
        assertTrue(response299.isSuccess());
    }

    @Test
    @DisplayName("HttpResponse isSuccess returns false for non-2xx status codes")
    void httpResponse_IsSuccess_Non2xxCodes() {
        HttpClient.HttpResponse response400 = new HttpClient.HttpResponse(400, "Bad Request", new HashMap<>());
        HttpClient.HttpResponse response401 = new HttpClient.HttpResponse(401, "Unauthorized", new HashMap<>());
        HttpClient.HttpResponse response500 = new HttpClient.HttpResponse(500, "Server Error", new HashMap<>());
        HttpClient.HttpResponse response199 = new HttpClient.HttpResponse(199, "Info", new HashMap<>());

        assertFalse(response400.isSuccess());
        assertFalse(response401.isSuccess());
        assertFalse(response500.isSuccess());
        assertFalse(response199.isSuccess());
    }

    @Test
    @DisplayName("HttpResponse getters return correct values")
    void httpResponse_Getters() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        HttpClient.HttpResponse response = new HttpClient.HttpResponse(200, "{\"key\":\"value\"}", headers);

        assertEquals(200, response.getStatusCode());
        assertEquals("{\"key\":\"value\"}", response.getBody());
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
    }

    @Test
    @DisplayName("HttpClientException stores status code")
    void httpClientException_StatusCode() {
        HttpClientException ex = new HttpClientException("Test error", 404);

        assertEquals(404, ex.getStatusCode());
        assertTrue(ex.isHttpError());
        assertEquals("Test error", ex.getMessage());
    }

    @Test
    @DisplayName("HttpClientException without status code returns -1")
    void httpClientException_NoStatusCode() {
        HttpClientException ex = new HttpClientException("Test error");

        assertEquals(-1, ex.getStatusCode());
        assertFalse(ex.isHttpError());
    }

    @Test
    @DisplayName("HttpClientException with cause preserves cause")
    void httpClientException_WithCause() {
        RuntimeException cause = new RuntimeException("Root cause");
        HttpClientException ex = new HttpClientException("Wrapper error", cause);

        assertEquals("Wrapper error", ex.getMessage());
        assertEquals(cause, ex.getCause());
        assertEquals(-1, ex.getStatusCode());
    }

    @Test
    @DisplayName("HttpClientException with status code and cause")
    void httpClientException_WithStatusCodeAndCause() {
        RuntimeException cause = new RuntimeException("Root cause");
        HttpClientException ex = new HttpClientException("HTTP error", 500, cause);

        assertEquals("HTTP error", ex.getMessage());
        assertEquals(500, ex.getStatusCode());
        assertEquals(cause, ex.getCause());
        assertTrue(ex.isHttpError());
    }
}
