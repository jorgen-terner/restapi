# RestAPI – Java HTTP Client with JAX-RS Filter Support

A lightweight REST client library built on the Jakarta JAX-RS Client API with support for `jakarta.ws.rs.client.ClientRequestFilter` for header manipulation, logging, and request interception.

## Features

- **JAX-RS Client API**: Implementation-agnostic client built with `jakarta.ws.rs.client.ClientBuilder`.
- **JAX-RS Filter Support**: Register `ClientRequestFilter` instances to intercept and modify requests before sending.
- **Easy Configuration**: Builder methods for connect/read timeouts (millis or `Duration`).
- **Automatic Serialization**: JSON request/response serialization via Jackson `ObjectMapper`.
- **Built-in Support**: GET, POST, PUT with JSON or byte payloads.
- **No Built-in Logging**: RestService has no logging dependencies - add `LoggingFilter` if you need logging.

## Build & Run

### With Gradle

```bash
./gradlew build
./gradlew test
```

### Compile and Run

```bash
java -jar build/libs/restapi-0.1.0-all.jar
```

## Usage Examples

### Basic REST Client

```java
import com.example.restapi.client.RestService;
import com.example.restapi.client.RestServiceBuilder;
import java.time.Duration;

// Create with default settings
RestService client = RestServiceBuilder.create().build();

// Or with custom timeouts (millis)
RestService client = RestServiceBuilder.create()
    .connectTimeout(5000)
    .readTimeout(10000)
    .build();

// Or with custom timeouts (Duration)
RestService client = RestServiceBuilder.create()
    .connectTimeout(Duration.ofSeconds(5))
    .readTimeout(Duration.ofSeconds(10))
    .build();

// GET request
MyDto result = client.get("http://api.example.com/items/1", MyDto.class);

// GET with query parameters
Map<String, String> params = Map.of("page", "1", "size", "10");
MyDto result = client.get("http://api.example.com/items", null, params, MyDto.class);

// POST with object (converted to JSON automatically)
MyDto requestData = new MyDto("John", 30);
MyDto created = client.post("http://api.example.com/items", requestData, MyDto.class);

// PUT with object (converted to JSON automatically)
MyDto updateData = new MyDto("Jane", 31);
MyDto updated = client.put("http://api.example.com/items/1", updateData, MyDto.class);

// POST binary data (base64 encoded)
byte[] data = "...".getBytes();
MyDto result = client.postBytes("http://api.example.com/files", data, 
    RestService.ByteEncoding.BASE64, MyDto.class);

// Always close when done
client.close();
```

### Dependency Injection with Custom Timeouts

```java
import jakarta.inject.Inject;
import com.example.restapi.client.RestService;
import com.example.restapi.client.RestServiceQualifier;

@Component
public class MyService {
    @Inject
    @RestServiceQualifier
    private RestService restClient;

    public void fetchData() {
        MyDto data = restClient.get("http://api.example.com/data", MyDto.class);
        // ...
    }
}
```

Configure timeouts via system properties:

```
-Drestclient.connect.timeout=5000
-Drestclient.read.timeout=15000
```

### Custom Error Handling

**RestService** uses an `ErrorHandler` to map HTTP errors to custom exceptions. By default, it throws `RuntimeException` for all errors. Register your own handler to customize error mapping:

```java
import com.example.restapi.client.RestService;
import com.example.restapi.client.RestServiceBuilder;
import com.example.restapi.client.ErrorHandler;

// Simple lambda-based error handler
RestService client = RestServiceBuilder.create()
    .errorHandler((statusCode, responseBody, uri) -> {
        switch (statusCode) {
            case 401:
                throw new UnauthorizedException("Not authenticated");
            case 403:
                throw new ForbiddenException("Access denied");
            case 404:
                throw new NotFoundException("Resource not found at " + uri);
            case 500:
            case 502:
            case 503:
                throw new ServerException("Server error: " + statusCode);
            default:
                throw new RestException("HTTP " + statusCode + ": " + responseBody);
        }
    })
    .build();

// Or implement ErrorHandler interface
public class CustomErrorHandler implements ErrorHandler {
    @Override
    public void handleError(int statusCode, String responseBody, String uri) {
        // Parse error response, log details, or customize behavior
        if (statusCode >= 500) {
            // Retry logic or circuit breaker here
            throw new RetryableException("Server error, retry later");
        }
        throw new RestException("HTTP " + statusCode);
    }
}

// Usage
RestService client = RestServiceBuilder.create()
    .errorHandler(new CustomErrorHandler())
    .connectTimeout(Duration.ofSeconds(5))
    .build();
```

### Adding Request Headers via Filter

```java
import jakarta.ws.rs.client.ClientRequestFilter;
import java.util.UUID;
import com.example.restapi.client.RestService;
import com.example.restapi.client.RestServiceBuilder;

// Simple lambda filter - add trace ID and authorization
ClientRequestFilter authFilter = ctx -> {
    ctx.getHeaders().add("X-Trace-Id", UUID.randomUUID().toString());
    ctx.getHeaders().add("Authorization", "Bearer your-token-here");
};

RestService client = RestServiceBuilder.create()
    .registerFilter(authFilter)
    .build();

// All subsequent requests will include these headers
MyDto result = client.get("http://api.example.com/items", MyDto.class);
```

### Comprehensive Logging Filter with SLF4J

**Note**: `RestService` has no built-in logging. Register `LoggingFilter` to enable request/response logging.

```java
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.ClientRequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;

public class LoggingFilter implements ClientRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public void filter(ClientRequestContext ctx) throws IOException {
        // Log request line
        LOG.info("→ {} {}", ctx.getMethod(), ctx.getUri());
        
        // Log request headers
        ctx.getHeaders().forEach((name, values) -> {
            values.forEach(value -> LOG.debug("  {}: {}", name, value));
        });

        // Log entity if present
        if (ctx.hasEntity()) {
            Object entity = ctx.getEntity();
            LOG.debug("  Body: {}", entity);
        }
    }
}

// Usage
RestService client = RestServiceBuilder.create()
    .registerFilter(new LoggingFilter())
    .build();
```

### Chaining Multiple Filters

Filters execute in registration order—combine logging, auth, tracing:

```java
RestService client = RestServiceBuilder.create()
    // 1. Add tracing
    .registerFilter(ctx -> 
        ctx.getHeaders().add("X-Request-Id", UUID.randomUUID().toString())
    )

    // 2. Add comprehensive logging
    .registerFilter(new LoggingFilter())

    // 3. Add authorization token
    .registerFilter(ctx -> 
        ctx.getHeaders().add("Authorization", "Bearer " + getAuthToken())
    )
    .build();

// All filters applied automatically
MyDto result = client.get("http://api.example.com/items", MyDto.class);
```

### Early Response (Abort) in Filters

Short-circuit a request and return a cached response:

```java
ClientRequestFilter cacheFilter = ctx -> {
    String url = ctx.getUri().toString();
    Object cached = getFromCache(url);
    if (cached != null) {
        LOG.debug("Cache hit for {}", url);
        // Abort request and return cached response
        Response cachedResponse = Response.ok(cached).build();
        ctx.abortWith(cachedResponse);
    }
};

client.registerClientRequestFilter(cacheFilter);
```

## Error Handling

```java
try {
    MyDto result = client.get("http://api.example.com/items/999", MyDto.class);
} catch (RuntimeException e) {
    // Wraps IO errors, InterruptedException, and HTTP errors (4xx, 5xx)
    LOG.error("REST request failed", e);
}
```

### Testing with WireMock

See the integration tests for examples using WireMock:

- [src/test/java/com/example/restapi/client/RestServiceWireMockTest.java](src/test/java/com/example/restapi/client/RestServiceWireMockTest.java)

## Architecture & Design Notes

- **Implementation-Agnostic**: Uses `jakarta.ws.rs.client.ClientBuilder` under the hood.
- **Filter Support**: `ClientRequestFilter` instances registered on the client.
- **Timeout Control**: Connect/read timeouts via builder methods or implementation-specific properties.

## Dependencies

Core:
- `jakarta.ws.rs:jakarta.ws.rs-api` – JAX-RS API (interfaces only)
- `com.fasterxml.jackson.core:jackson-databind` – JSON serialization

Logging:
- `org.slf4j:slf4j-api` – SLF4J interface
- `org.slf4j:slf4j-simple` (or your implementation) – SLF4J backing

Test:
- `org.junit.jupiter:junit-jupiter` – JUnit 5
- `org.mockito:mockito-core` – Mocking

## Project Structure

```
src/main/java/com/example/restapi/client/
├── RestService.java              # Main REST client
└── ... (other classes)

src/test/java/com/example/restapi/client/
└── RestServiceWireMockTest.java  # Integration tests
build.gradle                        # Gradle build config
```

## Notes

- Timeout properties can be configured via `RestServiceBuilder` methods or implementation-specific properties.
- Response status codes outside 200–299 range throw `RuntimeException` with HTTP status and body details.
- Entity serialization follows Jackson defaults; customize by configuring the `ObjectMapper` within `RestService` if needed.
