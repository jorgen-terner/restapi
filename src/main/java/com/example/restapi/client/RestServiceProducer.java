package com.example.restapi.client;

import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;

/**
 * CDI Producer för RestService.
 * 
 * Denna producer tillhandahåller RestService-instanser via dependency injection.
 * Den använder RestServiceBuilder för att konfigurera client-egenskaper,
 * och garanterar att close() anropas när bönnen destrueras via @Disposes.
 * 
 * <h3>Användning med Injection (CDI):</h3>
 * 
 * <b>1. Enkel injection:</b>
 * <pre>
 * {@literal @}Inject
 * {@literal @}RestServiceQualifier
 * private RestService restService;
 * </pre>
 * 
 * <b>2. Med injection point:</b>
 * <pre>
 * {@literal @}Inject
 * {@literal @}RestServiceQualifier
 * RestService restService;
 * 
 * public void doRequest() {
 *     String url = "https://api.example.com/users";
 *     String response = restService.get(url, String.class);
 * }
 * </pre>
 * 
 * <b>3. I en REST-resurs:</b>
 * <pre>
 * {@literal @}Path("/proxy")
 * public class ProxyResource {
 *     {@literal @}Inject
 *     {@literal @}RestServiceQualifier
 *     private RestService restService;
 *     
 *     {@literal @}GET
 *     {@literal @}Path("/users/{id}")
 *     public Response getUser({@literal @}PathParam("id") String userId) {
 *         try {
 *             String url = "https://api.example.com/users/" + userId;
 *             String user = restService.get(url, String.class);
 *             return Response.ok(user).build();
 *         } catch (Exception e) {
 *             return Response.serverError().entity(e.getMessage()).build();
 *         }
 *     }
 * }
 * </pre>
 * 
 * <h3>Timeout-konfigurering:</h3>
 * 
 * Timeout-värdena kan konfigureras genom att modifiera createClient()-metoden.
 * Värdena läses från system properties för flexibilitet:
 * 
 * <pre>
 * // Systemegenskaper
 * -Drestclient.connect.timeout=20000
 * -Drestclient.read.timeout=30000
 * 
 * // Eller environment-variabler (om stöd läggs till)
 * RESTCLIENT_CONNECT_TIMEOUT=20000
 * RESTCLIENT_READ_TIMEOUT=30000
 * </pre>
 * 
 * <h3>Filter och interceptorer:</h3>
 * 
 * {@code RestServiceBuilder} stöder registrering av {@code ClientRequestFilter}
 * för att modifiera requests före de skickas. Exempel:
 * 
 * <pre>
 * // Filter för standard-headrar
 * Map&lt;String, String&gt; headers = Map.of(
 *     "X-API-Key", "secret-key",
 *     "User-Agent", "MyApp/1.0"
 * );
 * 
 * RestService service = RestServiceBuilder.create()
 *     .registerFilter(new StandardHeadersFilter(headers))
 *     .property("jersey.config.client.connectTimeout", 20000)
 *     .build();
 * </pre>
 * 
 * <h3>Livscykel:</h3>
 * 
 * Containern hanterar automatiskt:
 * <ul>
 *   <li>Skapande av RestService-instans vid första injection-förfrågan</li>
 *   <li>Cachning för samma injection point under bönens livscykel</li>
 *   <li>Anropning av {@link #disposeClient} när bönnen förstörs</li>
 *   <li>Anropning av {@link RestService#close()} för proper cleanup</li>
 * </ul>
 */
public class RestServiceProducer
{
   /**
    * Connect timeout i millisekunder (default: 20000 ms = 20 sekunder).
    */
   private static final int DEFAULT_CONNECT_TIMEOUT = 20000;

   /**
    * Read timeout i millisekunder (default: 30000 ms = 30 sekunder).
    */
   private static final int DEFAULT_READ_TIMEOUT = 30000;

   /**
    * Skapar och producerar en RestService-instans med konfigurerade timeouts.
    * 
    * Timeouts kan överskrivas via system properties:
    * <ul>
    *   <li>{@code restclient.connect.timeout} - Connect timeout i ms</li>
    *   <li>{@code restclient.read.timeout} - Read timeout i ms</li>
    * </ul>
    * 
    * Exempel:
    * <pre>
    * // Standard (20s connect, 30s read)
    * RestService service = producer.createClient();
    * 
    * // Med anpassade timeouts via JVM-flaggor:
    * // java -Drestclient.connect.timeout=10000 -Drestclient.read.timeout=15000 App
    * </pre>
    * 
    * @return En ny RestService-instans konfigurerad enligt system properties
    */
   @Produces
   @RestServiceQualifier
   public RestService createClient()
   {
      int connectTimeout = getIntProperty("restclient.connect.timeout", DEFAULT_CONNECT_TIMEOUT);
      int readTimeout = getIntProperty("restclient.read.timeout", DEFAULT_READ_TIMEOUT);

      return RestServiceBuilder.create()
         .property("jersey.config.client.connectTimeout", connectTimeout)
         .property("jersey.config.client.readTimeout", readTimeout)
         .build();
   }

   /**
    * Stänger RestService-instansen när CDI-behållaren destruerar bönnen.
    * 
    * Denna metod anropas automatiskt av CDI-behållaren när den förstör den
    * producerade RestService-instansen. Den garanterar att alla underliggande
    * resurser (connection pools, etc.) frigörs korrekt.
    * 
    * @param restService RestService-instansen som ska stängas
    */
   public void disposeClient(@Disposes @RestServiceQualifier RestService restService)
   {
      restService.close();
   }

   /**
    * Läser en integer-property från system properties med fallback till default.
    * 
    * @param propertyName Namn på system property
    * @param defaultValue Default-värde om property inte är satt eller ogiltig
    * @return Property-värdet eller defaultValue
    */
   private static int getIntProperty(String propertyName, int defaultValue)
   {
      String value = System.getProperty(propertyName);
      if (value == null || value.isEmpty())
      {
         return defaultValue;
      }
      try
      {
         return Integer.parseInt(value);
      }
      catch (NumberFormatException e)
      {
         return defaultValue;
      }
   }
}

