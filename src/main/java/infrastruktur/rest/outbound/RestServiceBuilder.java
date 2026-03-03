package infrastruktur.rest.outbound;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.ClientRequestFilter;
import com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider;

import infrastruktur.rest.outbound.error.DefaultErrorHandler;
import infrastruktur.rest.outbound.error.ErrorHandler;

/**
 * Builder för RestService.
 * 
 * Användning:
 * <pre>
 * // Med standard timeouts
 * RestService service = RestServiceBuilder.create()
 *     .connectTimeout(20000)
 *     .readTimeout(20000)
 *     .build();
 * 
 * // Med anpassad felhantering
 * RestService service = RestServiceBuilder.create()
 *     .errorHandler((status, body, uri) -> {
 *         if (status == 404) throw new NotFoundException(uri);
 *         if (status >= 500) throw new ServerException();
 *     })
 *     .build();
 * 
 * // Med filter och felhantering
 * RestService service = RestServiceBuilder.create()
 *     .registerFilter(new LoggingFilter())
 *     .registerFilter(new StandardHeadersFilter(...))
 *     .errorHandler(new CustomErrorHandler())
 *     .connectTimeout(Duration.ofSeconds(10))
 *     .build();
 * </pre>
 */
public class RestServiceBuilder
{
   private ClientBuilder clientBuilder;
   private final List<ClientRequestFilter> filters = new ArrayList<>();
   private ErrorHandler errorHandler;

   private RestServiceBuilder()
   {
      this.clientBuilder = null;
      this.errorHandler = new DefaultErrorHandler();
   }

   /**
    * Skapar en ny RestServiceBuilder.
    *
    * @return En ny builder-instans
    */
   public static RestServiceBuilder create()
   {
      return new RestServiceBuilder();
   }

   /**
    * Sätter connect timeout via underliggande ClientBuilder.
    *
    * @param millis Connect timeout i ms
    * @return Denna builder (för method chaining)
    */
   public RestServiceBuilder connectTimeout(long millis)
   {
      if (clientBuilder == null)
      {
         clientBuilder = ClientBuilder.newBuilder();
      }
      clientBuilder.connectTimeout(millis, TimeUnit.MILLISECONDS);
      return this;
   }

   /**
    * Sätter connect timeout via underliggande ClientBuilder.
    *
    * @param duration Connect timeout
    * @return Denna builder (för method chaining)
    */
   public RestServiceBuilder connectTimeout(Duration duration)
   {
      if (duration == null)
      {
         throw new IllegalArgumentException("duration must not be null");
      }
      return connectTimeout(duration.toMillis());
   }

   /**
    * Sätter read timeout via underliggande ClientBuilder.
    *
    * @param millis Read timeout i ms
    * @return Denna builder (för method chaining)
    */
   public RestServiceBuilder readTimeout(long millis)
   {
      if (clientBuilder == null)
      {
         clientBuilder = ClientBuilder.newBuilder();
      }
      clientBuilder.readTimeout(millis, TimeUnit.MILLISECONDS);
      return this;
   }

   /**
    * Sätter read timeout via underliggande ClientBuilder.
    *
    * @param duration Read timeout
    * @return Denna builder (för method chaining)
    */
   public RestServiceBuilder readTimeout(Duration duration)
   {
      if (duration == null)
      {
         throw new IllegalArgumentException("duration must not be null");
      }
      return readTimeout(duration.toMillis());
   }

   /**
    * Sätter en property på den underliggande ClientBuilder.
    * Egenskapen appliceras enligt JAX-RS-implementeringens specifikation.
    *
    * @param name Egenskapsnamn
    * @param value Egenskapsvärde
    * @return Denna builder (för method chaining)
    */
   public RestServiceBuilder property(String name, Object value)
   {
      if (clientBuilder == null)
      {
         clientBuilder = ClientBuilder.newBuilder();
      }
      clientBuilder.property(name, value);
      return this;
   }

   /**
    * Registrerar ett ClientRequestFilter som kommer att appliceras på alla requests.
    * Filtret kan användas för att modifiera requests före de skickas, till exempel
    * för att lägga till standard-headrar, autentisering, eller loggning.
    *
    * @param filter Filtret som ska registreras
    * @return Denna builder (för method chaining)
    */
   public RestServiceBuilder registerFilter(ClientRequestFilter filter)
   {
      filters.add(filter);
      return this;
   }

   /**
    * Registrerar en ErrorHandler för anpassad felhantering.
    * 
    * ErrorHandlern anropas när en HTTP-response har en statuskod utanför 200-299.
    * Om ingen ErrorHandler registreras används DefaultErrorHandler som kastar RuntimeException.
    * 
    * @param handler ErrorHandlern som ska användas
    * @return Denna builder (för method chaining)
    */
   public RestServiceBuilder errorHandler(ErrorHandler handler)
   {
      if (handler == null)
      {
         throw new IllegalArgumentException("errorHandler must not be null");
      }
      this.errorHandler = handler;
      return this;
   }

   /**
    * Bygger och returnerar en ny RestService-instans baserat på konfigurationen.
    * Alla registrerade filter kommer att appliceras på denna Client.
    *
    * @return En ny RestService-instans
    */
   public RestService build()
   {
      Client client;
      if (clientBuilder == null)
      {
         client = ClientBuilder.newClient();
      }
      else
      {
         client = clientBuilder.build();
      }

      // Registrera Jackson JSON-provider för automatisk JSON-serialisering/deserialisering
      client.register(JacksonJsonProvider.class);

      // Registrera alla filters på Client-instansen
      for (ClientRequestFilter filter : filters)
      {
         client.register(filter);
      }

      return new RestService(client, errorHandler);
   }
}
