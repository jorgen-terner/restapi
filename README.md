# RestAPI – Java HTTP-klient med stöd för JAX-RS-filter

Ett REST-klientbibliotek byggt på Jakarta JAX-RS Client API med stöd för `jakarta.ws.rs.client.ClientRequestFilter` för header-hantering, loggning och request-interception.

## Funktioner

- **JAX-RS Client API**: Implementationsagnostisk klient byggd med `jakarta.ws.rs.client.ClientBuilder`.
- **Stöd för JAX-RS-filter**: Registrera `ClientRequestFilter` för att fånga och modifiera requests innan de skickas.
- **Enkel konfigurering**: Builder-metoder för connect/read-timeout (millisekunder eller `Duration`).
- **Automatisk serialisering**: JSON-serialisering/deserialisering via Jackson `ObjectMapper`.
- **Inbyggt stöd**: GET, POST, PUT med JSON eller byte-payload.
- **Ingen inbyggd loggning**: `RestService` har inga loggningsberoenden – registrera `LoggingFilter` vid behov.

## Användningsexempel

### Grundläggande REST-klient

```java
import com.example.restapi.client.RestService;
import com.example.restapi.client.RestServiceBuilder;
import java.time.Duration;

// Skapa med standardinställningar
RestService client = RestServiceBuilder.create().build();

// Eller med anpassade timeouts (millisekunder)
RestService client = RestServiceBuilder.create()
    .connectTimeout(5000)
    .readTimeout(10000)
    .build();

// Eller med anpassade timeouts (Duration)
RestService client = RestServiceBuilder.create()
    .connectTimeout(Duration.ofSeconds(5))
    .readTimeout(Duration.ofSeconds(10))
    .build();

// GET-request
MyVO result = client.get("http://api.example.com/items/1", MyVO.class);

// GET med query-parametrar
Map<String, String> params = Map.of("page", "1", "size", "10");
MyVO result = client.get("http://api.example.com/items", null, params, MyVO.class);

// POST med objekt (konverteras automatiskt till JSON)
MyVO requestData = new MyVO("John", 30);
MyVO created = client.post("http://api.example.com/items", requestData, MyVO.class);

// PUT med objekt (konverteras automatiskt till JSON)
MyVO updateData = new MyVO("Jane", 31);
MyVO updated = client.put("http://api.example.com/items/1", updateData, MyVO.class);

// POST med binärdata (base64-kodad)
byte[] data = "...".getBytes();
MyVO result = client.postBytes("http://api.example.com/files", data,
    RestService.ByteEncoding.BASE64, MyVO.class);

// Stäng alltid när du är klar
client.close();
```

### Dependency Injection med anpassade timeouts

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

### Anpassad felhantering

`RestService` använder en `ErrorHandler` för att mappa HTTP-fel till egna exceptions. Som standard kastas `RuntimeException` för alla fel. Registrera en egen handler för att anpassa felmappning:

```java
// Användning
RestService client = RestServiceBuilder.create()
    .errorHandler(new CustomErrorHandler())
    .connectTimeout(Duration.ofSeconds(5))
    .build();
```

### Lägg till request-headrar via filter

```java
import jakarta.ws.rs.client.ClientRequestFilter;
import java.util.UUID;
import com.example.restapi.client.RestService;
import com.example.restapi.client.RestServiceBuilder;

// Enkelt lambda-filter - lägg till trace-id och auth-header
ClientRequestFilter authFilter = ctx -> {
    ctx.getHeaders().add("X-Trace-Id", UUID.randomUUID().toString());
    ctx.getHeaders().add("Authorization", "Bearer your-token-here");
};

RestService client = RestServiceBuilder.create()
    .registerFilter(authFilter)
    .build();

// Alla efterföljande requests innehåller dessa headrar

```

### Loggning

**Obs**: `RestService` har ingen inbyggd loggning. Registrera `LoggingFilter` för request/response-loggning.
Det finns ett färdigt filter som använder slf4j: SLF4JLoggingFilter.

```java
// Användning
RestService client = RestServiceBuilder.create()
    .registerFilter(new MyLoggingFilter())
    .build();
```
