package com.example.restapi.client;

import com.example.restapi.dto.UserDto;
import com.example.restapi.dto.DataWithDateDto;
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
            .property("jersey.config.client.connectTimeout", 5000)
            .property("jersey.config.client.readTimeout", 5000)
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
        UserDto mockUser = new UserDto(1L, "John Doe & Co.", "john@example.com");
        
        // Setup mock server stub
        stubFor(get(urlMatching(".*search.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(toJson(mockUser))));

        // Make actual HTTP request through RestService
        UserDto result = restService.get(
            baseUrl + "/api/users/search",
            null,
            Map.of("name", "John Doe & Co."),
            UserDto.class
        );

        // Verify response
        assertNotNull(result);
        assertEquals("John Doe & Co.", result.getName());
        assertEquals("john@example.com", result.getEmail());
    }

    @Test
    void testGetRequestWithSwedishCharactersInQueryParam() {
        UserDto mockUser = new UserDto(2L, "Åsa Öberg", "asa@example.com");
        
        stubFor(get(urlMatching(".*search.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(toJson(mockUser))));

        UserDto result = restService.get(
            baseUrl + "/api/users/search",
            null,
            Map.of("name", "Åsa Öberg"),
            UserDto.class
        );

        assertNotNull(result);
        assertEquals("Åsa Öberg", result.getName());
    }

    @Test
    void testGetRequestWithEmailAddressSpecialCharacters() {
        UserDto mockUser = new UserDto(3L, "Jane Doe", "jane+test@example.com");
        
        stubFor(get(urlMatching(".*search.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(toJson(mockUser))));

        UserDto result = restService.get(
            baseUrl + "/api/users/search",
            null,
            Map.of("email", "jane+test@example.com"),
            UserDto.class
        );

        assertNotNull(result);
        assertEquals("jane+test@example.com", result.getEmail());
    }

    @Test
    void testGetRequestWithMultipleQueryParameters() {
        String mockResponse = "{\"page\":1,\"limit\":10,\"total\":100}";
        
        stubFor(get(urlMatching(".*users.*"))
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
    }

    @Test
    void testPostJsonRequest() {
        UserDto requestData = new UserDto(null, "New User", "new@example.com");
        UserDto mockResponse = new UserDto(5L, "New User", "new@example.com");
        
        stubFor(post(urlMatching(".*users.*"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody(toJson(mockResponse))));

        UserDto result = restService.postJson(
            baseUrl + "/api/users",
            toJson(requestData),
            UserDto.class
        );

        assertNotNull(result);
        assertEquals(5L, result.getId());
        assertEquals("New User", result.getName());
    }

    @Test
    void testPutJsonRequest() {
        UserDto updateData = new UserDto(6L, "Updated User", "updated@example.com");
        UserDto mockResponse = new UserDto(6L, "Updated User", "updated@example.com");
        
        stubFor(put(urlMatching(".*users/6.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(toJson(mockResponse))));

        UserDto result = restService.putJson(
            baseUrl + "/api/users/6",
            toJson(updateData),
            UserDto.class
        );

        assertNotNull(result);
        assertEquals("Updated User", result.getName());
        assertEquals("updated@example.com", result.getEmail());
    }

    @Test
    void testGetRequestWithCustomHeaders() {
        UserDto mockUser = new UserDto(4L, "Test User", "test@example.com");
        
        stubFor(get(urlMatching(".*users/4.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(toJson(mockUser))));

        UserDto result = restService.get(
            baseUrl + "/api/users/4",
            Map.of(
                "Authorization", "Bearer token123",
                "X-Custom-Header", "custom-value"
            ),
            null,
            UserDto.class
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
        DataWithDateDto mockData = new DataWithDateDto(
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

        DataWithDateDto result = restService.get(
            baseUrl + "/api/data/1",
            null,
            null,
            DataWithDateDto.class
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
            restService.get(baseUrl + "/api/nonexistent", null, null, UserDto.class)
        );
    }

    @Test
    void testHttp500ErrorThrowsException() {
        stubFor(get(urlMatching(".*error.*"))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("{\"error\":\"Internal server error\"}")));

        assertThrows(RuntimeException.class, () ->
            restService.get(baseUrl + "/api/error", null, null, UserDto.class)
        );
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
        UserDto updateRequest = new UserDto(1L, "Updated User1", "updated1@example.com");
        UserDto updateResponse = new UserDto(1L, "Updated User1", "updated1@example.com");
        
        stubFor(put(urlMatching(".*users/1.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(toJson(updateResponse))));

        UserDto updated = restService.putJson(
            baseUrl + "/api/users/1",
            toJson(updateRequest),
            UserDto.class
        );

        assertNotNull(updated);
        assertEquals("Updated User1", updated.getName());

        // Step 3: GET updated user to verify
        stubFor(get(urlMatching(".*users/1.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(toJson(updateResponse))));

        UserDto verified = restService.get(
            baseUrl + "/api/users/1",
            null,
            null,
            UserDto.class
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
}
