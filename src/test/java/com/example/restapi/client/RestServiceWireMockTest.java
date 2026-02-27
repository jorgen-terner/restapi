package com.example.restapi.client;

import com.example.restapi.dto.UserVO;
import com.example.restapi.dto.DataWithDateVO;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for RestService using WireMock mock server.
 * 
 * These tests start a real WireMock HTTP server locally and make
 * actual HTTP requests through RestService to verify functionality.
 */
public class RestServiceWireMockTest {

    private WireMockServer wireMockServer;
    private RestService restService;
    private String baseUrl;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Start WireMock server on dynamic port
        wireMockServer = new WireMockServer();
        wireMockServer.start();
        
        // Configure WireMock to listen on the allocated port
        configureFor(wireMockServer.port());
        baseUrl = "http://localhost:" + wireMockServer.port();

        // Create RestService pointing to mock server
        restService = RestServiceBuilder.create()
            .connectTimeout(5000)
            .readTimeout(5000)
            .build();

        // Setup ObjectMapper for JSON serialization
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @AfterEach
    void tearDown() {
        if (restService != null) {
            restService.close();
        }
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void testGetRequestWithUrlEncodingSpecialCharacters() {
        UserVO mockUser = new UserVO(1L, "John Doe & Co.", "john@example.com");
        
        // Setup mock server stub
        stubFor(get(urlMatching(".*search.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(toJson(mockUser))));

        // Make actual HTTP request through RestService
        UserVO result = restService.get(
            baseUrl + "/api/users/search",
            null,
            Map.of("name", "John Doe & Co."),
            UserVO.class
        );

        // Verify response
        assertNotNull(result);
        assertEquals("John Doe & Co.", result.getName());
        assertEquals("john@example.com", result.getEmail());
    }

    @Test
    void testGetRequestWithSwedishCharactersInQueryParam() {
        UserVO mockUser = new UserVO(2L, "Åsa Öberg", "asa@example.com");
        
        stubFor(get(urlMatching(".*search.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(toJson(mockUser))));

        UserVO result = restService.get(
            baseUrl + "/api/users/search",
            null,
            Map.of("name", "Åsa Öberg"),
            UserVO.class
        );

        assertNotNull(result);
        assertEquals("Åsa Öberg", result.getName());
    }

    @Test
    void testGetRequestWithEmailAddressSpecialCharacters() {
        UserVO mockUser = new UserVO(3L, "Jane Doe", "jane+test@example.com");
        
        stubFor(get(urlMatching(".*search.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(toJson(mockUser))));

        UserVO result = restService.get(
            baseUrl + "/api/users/search",
            null,
            Map.of("email", "jane+test@example.com"),
            UserVO.class
        );

        assertNotNull(result);
        assertEquals("jane+test@example.com", result.getEmail());
    }

    @Test
    void testGetRequestWithMultipleQueryParameters() {
        String mockResponse = "{\"page\":1,\"limit\":10,\"total\":100}";
        
        // Stub only matches if query parameters are present and correct
        stubFor(get(urlPathEqualTo("/api/users"))
            .withQueryParam("page", equalTo("1"))
            .withQueryParam("limit", equalTo("10"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(mockResponse)));

        java.util.Map<String, Object> result = restService.get(
            baseUrl + "/api/users",
            null,
            Map.of("page", "1", "limit", "10"),
            java.util.Map.class
        );

        assertNotNull(result);
        assertEquals(1, result.get("page"));
        assertEquals(10, result.get("limit"));
        assertEquals(100, result.get("total"));
        
        // Verify the request was made with correct query parameters
        verify(getRequestedFor(urlPathEqualTo("/api/users"))
            .withQueryParam("page", equalTo("1"))
            .withQueryParam("limit", equalTo("10")));
    }

    @Test
    void testPostJsonRequest() {
        UserVO requestData = new UserVO(null, "New User", "new@example.com");
        UserVO mockResponse = new UserVO(5L, "New User", "new@example.com");
        
        stubFor(post(urlMatching(".*users.*"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody(toJson(mockResponse))));

        UserVO result = restService.post(
            baseUrl + "/api/users",
            requestData,
            UserVO.class
        );

        assertNotNull(result);
        assertEquals(5L, result.getId());
        assertEquals("New User", result.getName());
    }

    @Test
    void testPutJsonRequest() {
        UserVO updateData = new UserVO(6L, "Updated User", "updated@example.com");
        UserVO mockResponse = new UserVO(6L, "Updated User", "updated@example.com");
        
        stubFor(put(urlMatching(".*users/6.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(toJson(mockResponse))));

        UserVO result = restService.put(
            baseUrl + "/api/users/6",
            updateData,
            UserVO.class
        );

        assertNotNull(result);
        assertEquals("Updated User", result.getName());
        assertEquals("updated@example.com", result.getEmail());
    }

    @Test
    void testGetRequestWithCustomHeaders() {
        UserVO mockUser = new UserVO(4L, "Test User", "test@example.com");
        
        stubFor(get(urlMatching(".*users/4.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(toJson(mockUser))));

        UserVO result = restService.get(
            baseUrl + "/api/users/4",
            Map.of(
                "Authorization", "Bearer token123",
                "X-Custom-Header", "custom-value"
            ),
            null,
            UserVO.class
        );

        assertNotNull(result);
        assertEquals("Test User", result.getName());
    }

    @Test
    void testPostBytesWithBase64Encoding() {
        // Create a simple response DTO for byte encoding tests
        String mockResponse = "{\"encoded\":\"dGVzdCBkYXRh\"}";
        
        stubFor(post(urlMatching(".*data.*"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody(mockResponse)));

        // Deserialize to a simple Map instead of String to avoid JSON parsing issues
        java.util.Map<String, Object> result = restService.postBytes(
            baseUrl + "/api/data",
            "test data".getBytes(),
            RestService.ByteEncoding.BASE64,
            java.util.Map.class
        );

        assertNotNull(result);
        assertEquals("dGVzdCBkYXRh", result.get("encoded"));
    }

    @Test
    void testPostBytesWithRawEncoding() {
        String mockResponse = "{\"received\":true}";
        
        stubFor(post(urlMatching(".*binary.*"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody(mockResponse)));

        java.util.Map<String, Object> result = restService.postBytes(
            baseUrl + "/api/binary",
            new byte[]{1, 2, 3, 4, 5},
            RestService.ByteEncoding.RAW,
            java.util.Map.class
        );

        assertNotNull(result);
        assertTrue((Boolean) result.get("received"));
    }

    @Test
    void testPostBytesWithHexEncoding() {
        String mockResponse = "{\"hexEncoded\":true}";
        
        stubFor(post(urlMatching(".*hex.*"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody(mockResponse)));

        java.util.Map<String, Object> result = restService.postBytes(
            baseUrl + "/api/hex",
            new byte[]{1, 2, 3, 4, 5},
            RestService.ByteEncoding.HEX,
            java.util.Map.class
        );

        assertNotNull(result);
        assertTrue((Boolean) result.get("hexEncoded"));
    }

    @Test
    void testGetRequestWithDateDeserialization() {
        DataWithDateVO mockData = new DataWithDateVO(
            1L,
            "Test Data",
            LocalDate.of(2024, 1, 15),
            LocalDateTime.of(2024, 1, 15, 10, 30, 0)
        );
        
        stubFor(get(urlMatching(".*data/1.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(toJson(mockData))));

        DataWithDateVO result = restService.get(
            baseUrl + "/api/data/1",
            null,
            null,
            DataWithDateVO.class
        );

        assertNotNull(result);
        assertEquals(LocalDate.of(2024, 1, 15), result.getCreatedDate());
        assertEquals(LocalDateTime.of(2024, 1, 15, 10, 30, 0), result.getModifiedDate());
    }

    @Test
    void testHttp404ErrorThrowsException() {
        stubFor(get(urlMatching(".*nonexistent.*"))
            .willReturn(aResponse()
                .withStatus(404)
                .withBody("{\"error\":\"Not found\"}")));

        assertThrows(RuntimeException.class, () ->
            restService.get(baseUrl + "/api/nonexistent", null, null, UserVO.class)
        );
    }

    @Test
    void testHttp500ErrorThrowsException() {
        stubFor(get(urlMatching(".*error.*"))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("{\"error\":\"Internal server error\"}")));

        assertThrows(RuntimeException.class, () ->
            restService.get(baseUrl + "/api/error", null, null, UserVO.class)
        );
    }

    @Test
    void testDefaultErrorHandlerIsUsedWhenNotConfigured() {
        stubFor(get(urlMatching(".*teapot.*"))
            .willReturn(aResponse()
                .withStatus(418)
                .withBody("{\"error\":\"I'm a teapot\"}")));

        RestService defaultService = RestServiceBuilder.create().build();

        try {
            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                defaultService.get(baseUrl + "/api/teapot", null, null, UserVO.class)
            );

            assertTrue(ex.getMessage().contains("Upstream error: 418"));
        } finally {
            defaultService.close();
        }
    }

    @Test
    void testErrorHandlerExceptionTypeIsPropagated() {
        stubFor(get(urlMatching(".*custom-ex.*"))
            .willReturn(aResponse()
                .withStatus(400)
                .withBody("{\"error\":\"Bad request\"}")));

        KnownException known = new KnownException("Custom error");
        RestService customService = RestServiceBuilder.create()
            .errorHandler(new FixedExceptionErrorHandler(known))
            .build();

        try {
            KnownException thrown = assertThrows(KnownException.class, () ->
                customService.get(baseUrl + "/api/custom-ex", null, null, UserVO.class)
            );

            assertSame(known, thrown);
        } finally {
            customService.close();
        }
    }

    @Test
    void testCustomErrorHandlerMapsErrors() {
        stubFor(get(urlMatching(".*notfound.*"))
            .willReturn(aResponse()
                .withStatus(404)
                .withBody("{\"error\":\"Not found\"}")));

        RestService customService = RestServiceBuilder.create()
            .errorHandler(new CapturingErrorHandler())
            .build();

        try {
            TestErrorException ex = assertThrows(TestErrorException.class, () ->
                customService.get(baseUrl + "/api/notfound", null, null, UserVO.class)
            );

            assertEquals(404, ex.getStatusCode());
            assertEquals("{\"error\":\"Not found\"}", ex.getResponseBody());
            assertTrue(ex.getUri().endsWith("/api/notfound"));
        } finally {
            customService.close();
        }
    }

    @Test
    void testErrorHandlerCanSuppressErrors() {
        stubFor(get(urlMatching(".*suppressed.*"))
            .willReturn(aResponse()
                .withStatus(503)
                .withBody("{\"error\":\"Service unavailable\"}")));

        SwallowingErrorHandler handler = new SwallowingErrorHandler();
        RestService customService = RestServiceBuilder.create()
            .errorHandler(handler)
            .build();

        try {
            UserVO result = customService.get(baseUrl + "/api/suppressed", null, null, UserVO.class);
            assertNull(result);
            assertEquals(1, handler.getCount());
        } finally {
            customService.close();
        }
    }

    @Test
    void testCompleteWorkflowGetUpdateGet() {
        // Step 1: GET user list as JSON array
        String listBody = "[{\"id\":1,\"name\":\"User1\",\"email\":\"user1@example.com\"}," +
                         "{\"id\":2,\"name\":\"User2\",\"email\":\"user2@example.com\"}]";
        
        stubFor(get(urlMatching(".*users$"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(listBody)));

        // Get as list of maps
        java.util.List<java.util.Map<String, Object>> userList = restService.get(
            baseUrl + "/api/users",
            null,
            null,
            java.util.List.class
        );
        
        assertNotNull(userList);
        assertEquals(2, userList.size());
        assertEquals("User1", userList.get(0).get("name"));

        // Step 2: UPDATE a user
        UserVO updateRequest = new UserVO(1L, "Updated User1", "updated1@example.com");
        UserVO updateResponse = new UserVO(1L, "Updated User1", "updated1@example.com");
        
        stubFor(put(urlMatching(".*users/1.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(toJson(updateResponse))));

        UserVO updated = restService.put(
            baseUrl + "/api/users/1",
            updateRequest,
            UserVO.class
        );

        assertNotNull(updated);
        assertEquals("Updated User1", updated.getName());

        // Step 3: GET updated user to verify
        stubFor(get(urlMatching(".*users/1.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(toJson(updateResponse))));

        UserVO verified = restService.get(
            baseUrl + "/api/users/1",
            null,
            null,
            UserVO.class
        );

        assertNotNull(verified);
        assertEquals("Updated User1", verified.getName());
        assertEquals("updated1@example.com", verified.getEmail());
    }

    /**
     * Helper method to convert objects to JSON
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }

    private static class CapturingErrorHandler implements ErrorHandler {
        @Override
        public void handleError(int statusCode, String responseBody, String uri) {
            throw new TestErrorException(statusCode, responseBody, uri);
        }
    }

    private static class SwallowingErrorHandler implements ErrorHandler {
        private int count;

        @Override
        public void handleError(int statusCode, String responseBody, String uri) {
            count++;
        }

        int getCount() {
            return count;
        }
    }

    private static class FixedExceptionErrorHandler implements ErrorHandler {
        private final RuntimeException exception;

        FixedExceptionErrorHandler(RuntimeException exception) {
            this.exception = exception;
        }

        @Override
        public void handleError(int statusCode, String responseBody, String uri) {
            throw exception;
        }
    }

    private static class TestErrorException extends RuntimeException {
        private final int statusCode;
        private final String responseBody;
        private final String uri;

        TestErrorException(int statusCode, String responseBody, String uri) {
            super("HTTP " + statusCode + " for " + uri);
            this.statusCode = statusCode;
            this.responseBody = responseBody;
            this.uri = uri;
        }

        int getStatusCode() {
            return statusCode;
        }

        String getResponseBody() {
            return responseBody;
        }

        String getUri() {
            return uri;
        }
    }

    private static class KnownException extends RuntimeException {
        KnownException(String message) {
            super(message);
        }
    }
}
