package com.example.restapi.client;

import java.util.ArrayList;
import java.util.List;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.ClientRequestFilter;

/**
 * Builder för RestService som döljer JAX-RS Client och ClientBuilder.
 * 
 * Användning:
 * <pre>
 * // Med Jersey timeouts
 * RestService service = RestServiceBuilder.create()
 *     .property("jersey.config.client.connectTimeout", 20000)
 *     .property("jersey.config.client.readTimeout", 20000)
 *     .build();
 * 
 * // Med RESTEasy timeouts
 * RestService service = RestServiceBuilder.create()
 *     .property("resteasy.property.name", value)
 *     .build();
 * 
 * // Default (no configuration)
 * RestService service = RestServiceBuilder.create().build();
 * </pre>
 */
public class RestServiceBuilder
{
   private ClientBuilder clientBuilder;
   private final List<ClientRequestFilter> filters = new ArrayList<>();

   private RestServiceBuilder()
   {
      // Använd reflection eller håll en map av properties för att undvika att exponera ClientBuilder
      this.clientBuilder = null;
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
    * Sätter en property på den underliggande ClientBuilder.
    * Egenskapen appliceras enligt JAX-RS-implementeringens specifikation.
    *
    * <b>Jersey-egenskaper:</b>
    * <ul>
    *   <li>"jersey.config.client.connectTimeout" - Connect timeout i ms</li>
    *   <li>"jersey.config.client.readTimeout" - Read timeout i ms</li>
    * </ul>
    *
    * <b>RESTEasy-egenskaper:</b>
    * <ul>
    *   <li>Se RESTEasy-dokumentationen för giltiga egenskaper</li>
    * </ul>
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
    * <b>Exempel - Standard-headrar:</b>
    * <pre>
    * RestService service = RestServiceBuilder.create()
    *     .registerFilter(new StandardHeadersFilter(
    *         Map.of(
    *             "X-API-Key", "secret-key",
    *             "User-Agent", "MyApp/1.0"
    *         )
    *     ))
    *     .build();
    * </pre>
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

      // Registrera alla filters på Client-instansen
      for (ClientRequestFilter filter : filters)
      {
         client.register(filter);
      }

      return new RestService(client);
   }
}
