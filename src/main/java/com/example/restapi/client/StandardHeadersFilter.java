package com.example.restapi.client;

import java.io.IOException;
import java.util.Map;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;

/**
 * Filter som lägger till standard-headrar på alla outgoing requests.
 * 
 * Headrar som redan är satta på requesten kommer inte att skrivas över.
 * 
 * <b>Exempel:</b>
 * <pre>
 * Map&lt;String, String&gt; standardHeaders = Map.of(
 *     "X-API-Key", "secret-key-123",
 *     "User-Agent", "MyApp/1.0"
 * );
 * 
 * RestService service = RestServiceBuilder.create()
 *     .registerFilter(new StandardHeadersFilter(standardHeaders))
 *     .build();
 * 
 * // Alla requests kommer nu att inkludera dessa headrar automatiskt
 * service.get("https://api.example.com/users", String.class);
 * </pre>
 */
public class StandardHeadersFilter implements ClientRequestFilter
{
   private final Map<String, String> standardHeaders;

   /**
    * Skapar ett filter med angivna standard-headrar.
    *
    * @param standardHeaders En map med headrar som ska läggas till automatiskt
    */
   public StandardHeadersFilter(Map<String, String> standardHeaders)
   {
      this.standardHeaders = standardHeaders != null ? standardHeaders : Map.of();
   }

   /**
    * Filtrerar requesten genom att lägga till standard-headrar.
    * Headrar som redan finns på requesten kommer inte att skrivas över.
    *
    * @param requestContext Kontexten för outgoing request
    * @throws IOException Om ett I/O-fel uppstår
    */
   @Override
   public void filter(ClientRequestContext requestContext) throws IOException
   {
      // Lägg till standard-headrar som inte redan finns
      standardHeaders.forEach((name, value) -> {
         if (!requestContext.getHeaders().containsKey(name))
         {
            requestContext.getHeaders().add(name, value);
         }
      });
   }
}
