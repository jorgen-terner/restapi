# RestAPI – Java HTTP Client with JAX-RS Filter Support

A lightweight REST client library built on `java.net.http.HttpClient` with support for `jakarta.ws.rs.client.ClientRequestFilter` for header manipulation, logging, and request interception.

## Features

- **Pure Java HTTP Client**: Uses `java.net.http.HttpClient` without heavyweight JAX-RS implementations.
- **JAX-RS Filter Support**: Register `ClientRequestFilter` instances to intercept and modify requests before sending.
- **Easy Configuration**: Construct with custom connect/read timeouts.
- **Automatic Serialization**: JSON request/response serialization via Jackson `ObjectMapper`.
- **Built-in Support**: GET, POST, PUT with JSON or byte payloads.

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
import infrastruktur.rest.RestService;

// Create with default timeouts (20s connect, 20s read)
RestService client = new RestService();

// Or with custom timeouts
RestService client = new RestService(5000, 10000);  // 5s connect, 10s read

// GET request
MyDto result = client.get("http://api.example.com/items/1", MyDto.class);

// GET with query parameters
Map<String, String> params = Map.of("page", "1", "size", "10");
MyDto result = client.get("http://api.example.com/items", null, params, MyDto.class);

// POST JSON
String json = "{\"name\":\"John\",\"age\":30}";
MyDto created = client.postJson("http://api.example.com/items", json, MyDto.class);

// PUT JSON
MyDto updated = client.putJson("http://api.example.com/items/1", json, MyDto.class);

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

@Component
public class MyService {
    private final RestService restClient;

    @Inject
    public MyService() {
        // Create with 5 second connect timeout, 15 second read timeout
        this.restClient = new RestService(5000, 15000);
    }

    public void fetchData() {
        MyDto data = restClient.get("http://api.example.com/data", MyDto.class);
        // ...
    }
}
```

### Adding Request Headers via Filter

```java
import jakarta.ws.rs.client.ClientRequestFilter;
import java.util.UUID;

RestService client = new RestService();

// Simple lambda filter - add trace ID and authorization
ClientRequestFilter authFilter = ctx -> {
    ctx.getHeaders().add("X-Trace-Id", UUID.randomUUID().toString());
    ctx.getHeaders().add("Authorization", "Bearer your-token-here");
};

client.registerClientRequestFilter(authFilter);

// All subsequent requests will include these headers
MyDto result = client.get("http://api.example.com/items", MyDto.class);
```

### Comprehensive Logging Filter with SLF4J

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
RestService client = new RestService();
client.registerClientRequestFilter(new LoggingFilter());
```

### Chaining Multiple Filters

Filters execute in registration order—combine logging, auth, tracing:

```java
RestService client = new RestService();

// 1. Add tracing
client.registerClientRequestFilter(ctx -> 
    ctx.getHeaders().add("X-Request-Id", UUID.randomUUID().toString())
);

// 2. Add comprehensive logging
client.registerClientRequestFilter(new LoggingFilter());

// 3. Add authorization token
client.registerClientRequestFilter(ctx -> 
    ctx.getHeaders().add("Authorization", "Bearer " + getAuthToken())
);

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

## Testing with Mock HttpClient

Inject a mock `HttpClient` for unit testing:

```java
import org.mockito.Mockito;
import java.net.http.HttpClient;
import java.time.Duration;

@Test
void testRestServiceWithMock() {
    HttpClient mockClient = Mockito.mock(HttpClient.class);
    Duration timeout = Duration.ofSeconds(10);
    
    RestService service = new RestService(mockClient, timeout);
    // Test using the mock client
}
```

## Architecture & Design Notes

- **No Heavy Dependencies**: Uses only `java.net.http.HttpClient` and `jakarta.ws.rs-api` (interface only).
- **Dynamic Proxy Pattern**: `ClientRequestFilter` compatibility via Java reflection proxy—no manual interface boilerplate.
- **Thread-Safe Registry**: Filters stored in `CopyOnWriteArrayList` for safe concurrent registration.
- **Timeout Control**: Both connect and read timeouts handled by `java.net.http.HttpClient`; implementation-specific timeouts can be set via filter properties.

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
src/main/java/infrastruktur/rest/
├── RestService.java              # Main REST client
└── ... (other classes)

src/test/java/infrastruktur/rest/
└── RestServiceTest.java          # Unit tests
build.gradle                        # Gradle build config
```

## Notes

- Timeout properties are stored internally; custom implementation-specific timeouts must be configured via registered filters or when constructing the underlying `HttpClient`.
- Response status codes outside 200–299 range throw `RuntimeException` with HTTP status and body details.
- Entity serialization follows Jackson defaults; customize by configuring the `ObjectMapper` within `RestService` if needed.
