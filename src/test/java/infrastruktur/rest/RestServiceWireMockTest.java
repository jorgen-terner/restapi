package infrastruktur.rest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.tomakehurst.wiremock.WireMockServer;

import infrastruktur.rest.outbound.RestService;
import infrastruktur.rest.outbound.RestServiceBuilder;
import infrastruktur.rest.outbound.error.ErrorHandler;

/**
 * Integration tests for RestService using WireMock mock server.
 * 
 * These tests start a real WireMock HTTP server locally and make actual HTTP
 * requests through RestService to verify functionality.
 */
public class RestServiceWireMockTest
{

   private WireMockServer wireMockServer;
   private RestService restService;
   private String baseUrl;
   private ObjectMapper objectMapper;

   @BeforeEach
   void setUp()
   {
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
   void tearDown()
   {
      if (restService != null)
      {
         restService.close();
      }
      if (wireMockServer != null)
      {
         wireMockServer.stop();
      }
   }

   @Test
   void testGetRequestWithUrlEncodingSpecialCharacters()
   {
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
            UserVO.class);

      // Verify response
      assertNotNull(result);
      assertEquals("John Doe & Co.", result.getName());
      assertEquals("john@example.com", result.getEmail());
   }

   @Test
   void testGetRequestWithSwedishCharactersInQueryParam()
   {
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
            UserVO.class);

      assertNotNull(result);
      assertEquals("Åsa Öberg", result.getName());
   }

   @Test
   void testGetRequestWithEmailAddressSpecialCharacters()
   {
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
            UserVO.class);

      assertNotNull(result);
      assertEquals("jane+test@example.com", result.getEmail());
   }

   @Test
   void testGetRequestWithMultipleQueryParameters()
   {
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
            java.util.Map.class);

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
   void testPostJsonRequest()
   {
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
            UserVO.class);

      assertNotNull(result);
      assertEquals(5L, result.getId());
      assertEquals("New User", result.getName());
   }

   @Test
   void testPutJsonRequest()
   {
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
            UserVO.class);

      assertNotNull(result);
      assertEquals("Updated User", result.getName());
      assertEquals("updated@example.com", result.getEmail());
   }

   @Test
   void testGetRequestWithCustomHeaders()
   {
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
                  "X-Custom-Header", "custom-value"),
            null,
            UserVO.class);

      assertNotNull(result);
      assertEquals("Test User", result.getName());

      verify(getRequestedFor(urlPathEqualTo("/api/users/4"))
            .withHeader("Authorization", equalTo("Bearer token123"))
            .withHeader("X-Custom-Header", equalTo("custom-value")));
   }

   @Test
   void testPostBytesWithBase64Encoding()
   {
      String mockResponse = "{\"ok\":true}";

      stubFor(post(urlMatching(".*data.*"))
            .willReturn(aResponse()
                  .withStatus(201)
                  .withHeader("Content-Type", "application/json")
                  .withBody(mockResponse)));

      // Deserialize to a simple Map instead of String to avoid JSON parsing
      // issues
      java.util.Map<String, Object> result = restService.postBytes(
            baseUrl + "/api/data",
            "test data".getBytes(),
            RestService.ByteEncoding.BASE64,
            java.util.Map.class);

      assertNotNull(result);
      assertTrue((Boolean) result.get("ok"));

      verify(postRequestedFor(urlPathEqualTo("/api/data"))
            .withRequestBody(equalTo("dGVzdCBkYXRh")));
   }

   @Test
   void testPostBytesWithRawEncoding()
   {
      String mockResponse = "{\"ok\":true}";
      String rawPayload = "test data";

      stubFor(post(urlMatching(".*binary.*"))
            .willReturn(aResponse()
                  .withStatus(201)
                  .withHeader("Content-Type", "application/json")
                  .withBody(mockResponse)));

      java.util.Map<String, Object> result = restService.postBytes(
            baseUrl + "/api/binary",
            rawPayload.getBytes(),
            RestService.ByteEncoding.RAW,
            java.util.Map.class);

      assertNotNull(result);
      assertTrue((Boolean) result.get("ok"));

      verify(postRequestedFor(urlPathEqualTo("/api/binary"))
            .withRequestBody(equalTo(rawPayload)));
   }

   @Test
   void testPostBytesWithHexEncoding()
   {
      String mockResponse = "{\"ok\":true}";

      stubFor(post(urlMatching(".*hex.*"))
            .willReturn(aResponse()
                  .withStatus(201)
                  .withHeader("Content-Type", "application/json")
                  .withBody(mockResponse)));

      java.util.Map<String, Object> result = restService.postBytes(
            baseUrl + "/api/hex",
            new byte[]
            {
                  1, 2, 3, 4, 5
            },
            RestService.ByteEncoding.HEX,
            java.util.Map.class);

      assertNotNull(result);
      assertTrue((Boolean) result.get("ok"));

      verify(postRequestedFor(urlPathEqualTo("/api/hex"))
            .withRequestBody(equalTo("0102030405")));
   }

   @Test
   void testGetRequestWithDateDeserialization()
   {
      DataWithDateVO requestData = new DataWithDateVO(
            LocalDate.of(2024, 1, 15),
            LocalDateTime.of(2024, 1, 15, 10, 30, 0),
            OffsetDateTime.parse("2024-01-15T10:30:00+01:00"),
            ZonedDateTime.parse("2024-01-15T10:30:00+01:00[Europe/Stockholm]"));

      String responseJson = "{" +
            "\"localDate\":\"2024-01-15\"," +
            "\"localDateTime\":\"2024-01-15T10:30:00\"," +
            "\"offsetDateTime\":\"2024-01-15T10:30:00+01:00\"," +
            "\"zonedDateTime\":\"2024-01-15T10:30:00+01:00[Europe/Stockholm]\"" +
            "}";

      stubFor(post(urlMatching(".*data/1.*"))
            .willReturn(aResponse()
                  .withStatus(200)
                  .withHeader("Content-Type", "application/json")
                  .withBody(responseJson)));

      DataWithDateVO result = restService.post(
            baseUrl + "/api/data/1",
            requestData,
            DataWithDateVO.class);

      assertNotNull(result);
      assertEquals(LocalDate.of(2024, 1, 15), result.getLocalDate());
      assertEquals(LocalDateTime.of(2024, 1, 15, 10, 30, 0), result.getLocalDateTime());
      assertEquals(OffsetDateTime.parse("2024-01-15T10:30:00+01:00"), result.getOffsetDateTime());
      assertEquals(ZonedDateTime.parse("2024-01-15T10:30:00+01:00[Europe/Stockholm]"), result.getZonedDateTime());

      verify(postRequestedFor(urlPathEqualTo("/api/data/1"))
            .withRequestBody(matchingJsonPath("$.localDate", equalTo("2024-01-15")))
            .withRequestBody(matchingJsonPath("$.localDateTime", equalTo("2024-01-15T10:30:00")))
            .withRequestBody(matchingJsonPath("$.offsetDateTime", equalTo("2024-01-15T10:30:00+01:00")))
            .withRequestBody(matchingJsonPath("$.zonedDateTime", equalTo("2024-01-15T10:30:00+01:00[Europe/Stockholm]"))));
   }

   @Test
   void testGetRequestIgnoresUnknownJsonProperties()
   {
      String responseWithUnknownFields = "{" +
            "\"id\":7," +
            "\"name\":\"Mapper Test\"," +
            "\"email\":\"mapper@test.example\"," +
            "\"unknownRoot\":\"ignored\"," +
            "\"extra\":{\"nested\":true}" +
            "}";

      stubFor(get(urlMatching(".*users/7.*"))
            .willReturn(aResponse()
                  .withStatus(200)
                  .withHeader("Content-Type", "application/json")
                  .withBody(responseWithUnknownFields)));

      UserVO result = restService.get(
            baseUrl + "/api/users/7",
            null,
            null,
            UserVO.class);

      assertNotNull(result);
      assertEquals(7L, result.getId());
      assertEquals("Mapper Test", result.getName());
      assertEquals("mapper@test.example", result.getEmail());
   }

   @Test
   void testHttp404ErrorThrowsException()
   {
      stubFor(get(urlMatching(".*nonexistent.*"))
            .willReturn(aResponse()
                  .withStatus(404)
                  .withBody("{\"error\":\"Not found\"}")));

      assertThrows(RuntimeException.class, () -> restService.get(baseUrl + "/api/nonexistent", null, null, UserVO.class));
   }

   @Test
   void testHttp500ErrorThrowsException()
   {
      stubFor(get(urlMatching(".*error.*"))
            .willReturn(aResponse()
                  .withStatus(500)
                  .withBody("{\"error\":\"Internal server error\"}")));

      assertThrows(RuntimeException.class, () -> restService.get(baseUrl + "/api/error", null, null, UserVO.class));
   }

   @Test
   void testDefaultErrorHandlerIsUsedWhenNotConfigured()
   {
      stubFor(get(urlMatching(".*teapot.*"))
            .willReturn(aResponse()
                  .withStatus(418)
                  .withBody("{\"error\":\"I'm a teapot\"}")));

      RestService defaultService = RestServiceBuilder.create().build();

      try
      {
         String url = baseUrl + "/api/teapot";
         RuntimeException ex = assertThrows(RuntimeException.class, () -> defaultService.get(url, null, null, UserVO.class));
         assertTrue(ex.getMessage().contains("Fel vid anrop till " + url + ": 418"));
      }
      finally
      {
         defaultService.close();
      }
   }

   @Test
   void testErrorHandlerExceptionTypeIsPropagated()
   {
      stubFor(get(urlMatching(".*custom-ex.*"))
            .willReturn(aResponse()
                  .withStatus(400)
                  .withBody("{\"error\":\"Bad request\"}")));

      KnownException known = new KnownException("Custom error");
      RestService customService = RestServiceBuilder.create()
            .errorHandler(new FixedExceptionErrorHandler(known))
            .build();

      try
      {
         KnownException thrown = assertThrows(KnownException.class,
               () -> customService.get(baseUrl + "/api/custom-ex", null, null, UserVO.class));

         assertSame(known, thrown);
      }
      finally
      {
         customService.close();
      }
   }

   @Test
   void testCustomErrorHandlerMapsErrors()
   {
      stubFor(get(urlMatching(".*notfound.*"))
            .willReturn(aResponse()
                  .withStatus(404)
                  .withBody("{\"error\":\"Not found\"}")));

      RestService customService = RestServiceBuilder.create()
            .errorHandler(new CapturingErrorHandler())
            .build();

      try
      {
         TestErrorException ex = assertThrows(TestErrorException.class,
               () -> customService.get(baseUrl + "/api/notfound", null, null, UserVO.class));

         assertEquals(404, ex.getStatusCode());
         assertEquals("{\"error\":\"Not found\"}", ex.getResponseBody());
         assertTrue(ex.getUri().endsWith("/api/notfound"));
      }
      finally
      {
         customService.close();
      }
   }

   @Test
   void testErrorHandlerCanSuppressErrors()
   {
      stubFor(get(urlMatching(".*suppressed.*"))
            .willReturn(aResponse()
                  .withStatus(503)
                  .withBody("{\"error\":\"Service unavailable\"}")));

      SwallowingErrorHandler handler = new SwallowingErrorHandler();
      RestService customService = RestServiceBuilder.create()
            .errorHandler(handler)
            .build();

      try
      {
         UserVO result = customService.get(baseUrl + "/api/suppressed", null, null, UserVO.class);
         assertNull(result);
         assertEquals(1, handler.getCount());
      }
      finally
      {
         customService.close();
      }
   }

   @Test
   void testSerializationExceptionIsDelegatedToErrorHandler()
   {
      String url = baseUrl + "/api/serialize-error";

      java.util.Map<String, Object> cyclic = new java.util.HashMap<>();
      cyclic.put("self", cyclic);

      ExceptionDelegatingErrorHandler handler = new ExceptionDelegatingErrorHandler();
      RestService customService = RestServiceBuilder.create()
            .errorHandler(handler)
            .build();

      try
      {
         KnownException thrown = assertThrows(KnownException.class, () -> customService.post(url, cyclic, UserVO.class));

         assertEquals("Serialization delegated", thrown.getMessage());
         assertEquals(1, handler.getCount());
         assertNotNull(handler.getLastException());
         assertTrue(handler.getLastException() instanceof Exception);
         assertEquals(url, handler.getLastUri());
      }
      finally
      {
         customService.close();
      }
   }

   /**
    * Helper method to convert objects to JSON
    */
   private String toJson(Object obj)
   {
      try
      {
         return objectMapper.writeValueAsString(obj);
      }
      catch (Exception e)
      {
         throw new RuntimeException("Failed to serialize to JSON", e);
      }
   }

   private static class CapturingErrorHandler implements ErrorHandler
   {
      @Override
      public void handleError(int statusCode, String responseBody, String uri)
      {
         throw new TestErrorException(statusCode, responseBody, uri);
      }

      @Override
      public void handleException(Exception exception, String uri)
      {
         if (exception instanceof RuntimeException runtimeException)
         {
            throw runtimeException;
         }
         throw new RuntimeException(exception);
      }
   }

   private static class SwallowingErrorHandler implements ErrorHandler
   {
      private int count;

      @Override
      public void handleError(int statusCode, String responseBody, String uri)
      {
         count++;
      }

      @Override
      public void handleException(Exception exception, String uri)
      {
         if (exception instanceof RuntimeException runtimeException)
         {
            throw runtimeException;
         }
         throw new RuntimeException(exception);
      }

      int getCount()
      {
         return count;
      }
   }

   private static class FixedExceptionErrorHandler implements ErrorHandler
   {
      private final RuntimeException exception;

      FixedExceptionErrorHandler(RuntimeException exception)
      {
         this.exception = exception;
      }

      @Override
      public void handleError(int statusCode, String responseBody, String uri)
      {
         throw exception;
      }

      @Override
      public void handleException(Exception exception, String uri)
      {
         if (exception instanceof RuntimeException runtimeException)
         {
            throw runtimeException;
         }
         throw new RuntimeException(exception);
      }
   }

   private static class ExceptionDelegatingErrorHandler implements ErrorHandler
   {
      private int count;
      private Exception lastException;
      private String lastUri;

      @Override
      public void handleError(int statusCode, String responseBody, String uri)
      {
         throw new AssertionError("handleError should not be called for serialization failures");
      }

      @Override
      public void handleException(Exception exception, String uri)
      {
         count++;
         lastException = exception;
         lastUri = uri;
         throw new KnownException("Serialization delegated");
      }

      int getCount()
      {
         return count;
      }

      Exception getLastException()
      {
         return lastException;
      }

      String getLastUri()
      {
         return lastUri;
      }
   }

   private static class TestErrorException extends RuntimeException
   {
      private final int statusCode;
      private final String responseBody;
      private final String uri;

      TestErrorException(int statusCode, String responseBody, String uri)
      {
         super("HTTP " + statusCode + " for " + uri);
         this.statusCode = statusCode;
         this.responseBody = responseBody;
         this.uri = uri;
      }

      int getStatusCode()
      {
         return statusCode;
      }

      String getResponseBody()
      {
         return responseBody;
      }

      String getUri()
      {
         return uri;
      }
   }

   private static class KnownException extends RuntimeException
   {
      KnownException(String message)
      {
         super(message);
      }
   }
}
