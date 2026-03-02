package com.example.restapi.client;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.Base64;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.example.restapi.client.internal.RestClientMapperConfig;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Tillhandahåller ett Rest-gränssnitt med JAX-RS API.
 * 
 * RestService är implementation-agnostisk och fungerar med vilken JAX-RS provider som helst
 * (Jersey, RESTEasy, Quarkus runtime, etc.) utan att koda ihop med någon specifik.
 * 
 * Timeout-konfigurering sker genom RestServiceBuilder:
 * 
 * <b>Med timeouts:</b>
 * <pre>
 * RestService service = RestServiceBuilder.create()
 *     .connectTimeout(20000)
 *     .readTimeout(20000)
 *     .build();
 * </pre>
 * 
 * <b>Med default settings:</b>
 * <pre>
 * RestService service = RestServiceBuilder.create().build();
 * </pre>
 * 
 * <b>Med LoggingFilter:</b>
 * <pre>
 * RestService service = RestServiceBuilder.create()
 *     .registerFilter(new LoggingFilter())
 *     .build();
 * </pre>
 * 
 * <b>Med anpassad felhantering:</b>
 * <pre>
 * RestService service = RestServiceBuilder.create()
 *     .errorHandler((status, body, uri) -> {
 *         if (status == 404) {
 *             throw new NotFoundException("Not found at " + uri);
 *         } else if (status >= 500) {
 *             throw new ServerException("Server error: " + status);
 *         }
 *     })
 *     .build();
 * </pre>
 * 
 * <b>Med POST/PUT:</b>
 * <pre>
 * MyDto request = new MyDto("data");
 * MyDto response = service.post("/api/items", request, MyDto.class);
 * MyDto updated = service.put("/api/items/1", request, MyDto.class);
 * </pre>
 */

public class RestService implements AutoCloseable
{
   /**
    * Encoding-strategi för byte-arrays i HTTP-requests.
    */
   public enum ByteEncoding
   {
      BASE64, RAW, HEX // TODO: HEX känns onödig
   }

   private final static RestClientMapperConfig MAPPER_CONFIG = new RestClientMapperConfig();

   private final Client client;
   private final ObjectMapper mapper;
   private final ErrorHandler errorHandler;

   /**
    * Skapar en RestService med en default JAX-RS Client.
    * För konfigurering av timeouts, använd RestServiceBuilder.create().
    */
   public RestService()
   {
      this(ClientBuilder.newClient(), new DefaultErrorHandler());
   }
   
   /**
    * Paket-privat konstruktor för internt bruk.
    * Användare bör använda RestServiceBuilder istället.
    *
    * @param client En JAX-RS Client konfigurerad enligt din JAX-RS providers specifikation
    */
   RestService(Client client)
   {
      this(client, new DefaultErrorHandler());
   }

   /**
    * Paket-privat konstruktor för internt bruk.
    * Användare bör använda RestServiceBuilder istället.
    *
    * @param client En JAX-RS Client konfigurerad enligt din JAX-RS providers specifikation
    * @param errorHandler En ErrorHandler för anpassad felhantering
    */
   RestService(Client client, ErrorHandler errorHandler)
   {
      this.client = client;
      this.mapper = MAPPER_CONFIG.getAndConfigureObjectMapper();
      this.errorHandler = errorHandler != null ? errorHandler : new DefaultErrorHandler();
   }

   // TODO: Finns standardfunktionalitet i apache httpclient men värt att dra in det beroendet?
   private String appendQueryParams(String url, Map<String, String> queryParams)
   {
      if ((queryParams == null) || queryParams.isEmpty())
      {
         return url;
      }
      StringBuilder sb = new StringBuilder(url);
      String sep = url.contains("?") ? "&" : "?";
      for (Map.Entry<String, String> e : queryParams.entrySet())
      {
         if (e.getKey() == null)
            continue;
         String k = URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8);
         String v = e.getValue() == null ? "" : URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8);
         sb.append(sep).append(k).append("=").append(v);
         sep = "&";
      }
      return sb.toString();
   }

   private String encodeBytes(byte[] data, ByteEncoding encoding)
   {
      if (data == null)
      {
         return "";
      }
      switch (encoding)
      {
         case BASE64:
            return Base64.getEncoder().encodeToString(data);
         case HEX:
            StringBuilder hex = new StringBuilder();
            for (byte b : data)
            {
               hex.append(String.format("%02x", b));
            }
            return hex.toString();
         case RAW:
         default:
            return new String(data, StandardCharsets.UTF_8);
      }
   }

   private WebTarget prepareWebTarget(String url, Map<String, String> queryParams)
   {
      String fullUrl = appendQueryParams(url, queryParams);
      return client.target(fullUrl);
   }

   private <T> T executeRequest(WebTarget target, String method, String contentType, 
                                Object body, Map<String, String> headers, Class<T> clazz)
   {
      try
      {
         var requestBuilder = target.request(MediaType.APPLICATION_JSON);
         
         // Lägg till custom headers
         if (headers != null)
         {
            for (Map.Entry<String, String> h : headers.entrySet())
            {
               if (h.getKey() != null && h.getValue() != null)
               {
                  requestBuilder = requestBuilder.header(h.getKey(), h.getValue());
               }
            }
         }
         
         // Content-Type för requests med body
         if ((contentType != null) && (body != null))
         {
            requestBuilder = requestBuilder.header(HttpHeaders.CONTENT_TYPE, contentType);
         }
         
         Response response;
         switch (method.toUpperCase())
         {
            case "GET":
               response = requestBuilder.get();
               break;
            case "POST":
               response = requestBuilder.post(jakarta.ws.rs.client.Entity.entity(body, contentType != null ? contentType : MediaType.APPLICATION_JSON));
               break;
            case "PUT":
               response = requestBuilder.put(jakarta.ws.rs.client.Entity.entity(body, contentType != null ? contentType : MediaType.APPLICATION_JSON));
               break;
            default:
               errorHandler.handleException(new IllegalArgumentException("Unsupported method: " + method),
                     target.getUri().toString());
               return null;
         }
         
         int status = response.getStatus();
         
         if ((status >= 200) && (status < 300))
         {
            String responseBody = response.readEntity(String.class);
            return mapper.readValue(responseBody, clazz);
         }
         
         String errorBody = response.readEntity(String.class);
         errorHandler.handleError(status, errorBody, target.getUri().toString());
         //  Om errorhandlern inte kastade exception antar vi att den hanterade felet
         return null;
      }
      catch (Exception e)
      {
         errorHandler.handleException(e, target != null ? target.getUri().toString() : null);
         return null;
      }
   }

   private <T> T executeStringRequest(WebTarget target, String method, String contentType, 
                                      String body, Map<String, String> headers, Class<T> clazz)
   {
      try
      {
         var requestBuilder = target.request(MediaType.APPLICATION_JSON);
         
         // Lägg till custom headers
         if (headers != null)
         {
            for (Map.Entry<String, String> h : headers.entrySet())
            {
               if (h.getKey() != null && h.getValue() != null)
               {
                  requestBuilder = requestBuilder.header(h.getKey(), h.getValue());
               }
            }
         }
         
         // Content-Type för requests med body
         if ((contentType != null) && (body != null))
         {
            requestBuilder = requestBuilder.header(HttpHeaders.CONTENT_TYPE, contentType);
         }
         
         Response response;
         switch (method.toUpperCase())
         {
            case "POST":
               response = requestBuilder.post(jakarta.ws.rs.client.Entity.text(body));
               break;
            case "PUT":
               response = requestBuilder.put(jakarta.ws.rs.client.Entity.text(body));
               break;
            default:
               errorHandler.handleException(new IllegalArgumentException("Unsupported method: " + method),
                     target.getUri().toString());
               return null;
         }
         
         int status = response.getStatus();
         
         if ((status >= 200) && (status < 300))
         {
            String responseBody = response.readEntity(String.class);
            return mapper.readValue(responseBody, clazz);
         }
         
         String errorBody = response.readEntity(String.class);
         errorHandler.handleError(status, errorBody, target.getUri().toString());
         //  Om errorhandlern inte kastade exception antar vi att den hanterade felet
         return null;
      }
      catch (Exception e)
      {
         errorHandler.handleException(e, target != null ? target.getUri().toString() : null);
         return null;
      }
   }

   public <T> T get(String url, Map<String, String> headers, Map<String, String> queryParams, Class<T> clazz)
   {
      WebTarget target = prepareWebTarget(url, queryParams);
      return executeRequest(target, "GET", null, null, headers, clazz);
   }

   public <T> T get(String url, Class<T> clazz)
   {
      return get(url, null, null, clazz);
   }

   /**
    * Skickar ett POST-request med ett objekt som JSON.
    * Objektet konverteras till JSON automatiskt med ObjectMapper.
    *
    * @param url Request-URIn
    * @param body Objektet som ska skickas som JSON
    * @param headers Custom headers eller null
    * @param queryParams Query-parametrar eller null
    * @param clazz Klassen för response-objektet
    * @return Det deserialiserade response-objektet
    */
   public <T> T post(String url, Object body, Map<String, String> headers, Map<String, String> queryParams, Class<T> clazz)
   {
      WebTarget target;
      try
      {
         target = prepareWebTarget(url, queryParams);
      }
      catch (Exception e)
      {
         errorHandler.handleException(e, url);
         return null;
      }
      return executeRequest(target, "POST", MediaType.APPLICATION_JSON, body, headers, clazz);
   }

   /**
    * Skickar ett POST-request med ett objekt som JSON.
    * Objektet konverteras till JSON automatiskt med ObjectMapper.
    *
    * @param url Request-URIn
    * @param body Objektet som ska skickas som JSON
    * @param clazz Klassen för response-objektet
    * @return Det deserialiserade response-objektet
    */
   public <T> T post(String url, Object body, Class<T> clazz)
   {
      return post(url, body, null, null, clazz);
   }

   /**
    * Skickar ett PUT-request med ett objekt som JSON.
    * Objektet konverteras till JSON automatiskt med ObjectMapper.
    *
    * @param url Request-URIn
    * @param body Objektet som ska skickas som JSON
    * @param headers Custom headers eller null
    * @param queryParams Query-parametrar eller null
    * @param clazz Klassen för response-objektet
    * @return Det deserialiserade response-objektet
    */
   public <T> T put(String url, Object body, Map<String, String> headers, Map<String, String> queryParams, Class<T> clazz)
   {
      WebTarget target;
      try
      {
         target = prepareWebTarget(url, queryParams);
      }
      catch (Exception e)
      {
         errorHandler.handleException(e, url);
         return null;
      }
      return executeRequest(target, "PUT", MediaType.APPLICATION_JSON, body, headers, clazz);
   }

   /**
    * Skickar ett PUT-request med ett objekt som JSON.
    * Objektet konverteras till JSON automatiskt med ObjectMapper.
    *
    * @param url Request-URIn
    * @param body Objektet som ska skickas som JSON
    * @param clazz Klassen för response-objektet
    * @return Det deserialiserade response-objektet
    */
   public <T> T put(String url, Object body, Class<T> clazz)
   {
      return put(url, body, null, null, clazz);
   }

   public <T> T postBytes(String url, byte[] data, ByteEncoding encoding, Map<String, String> headers,
         Map<String, String> queryParams, Class<T> clazz)
   {
      String encodedData = encodeBytes(data, encoding);
      String contentType = encoding == ByteEncoding.BASE64 ? MediaType.APPLICATION_OCTET_STREAM : MediaType.TEXT_PLAIN;
      WebTarget target = prepareWebTarget(url, queryParams);
      return executeStringRequest(target, "POST", contentType, encodedData, headers, clazz);
   }

   public <T> T postBytes(String url, byte[] data, ByteEncoding encoding, Class<T> clazz)
   {
      return postBytes(url, data, encoding, null, null, clazz);
   }

   public <T> T putBytes(String url, byte[] data, ByteEncoding encoding, Map<String, String> headers,
         Map<String, String> queryParams, Class<T> clazz)
   {
      String encodedData = encodeBytes(data, encoding);
      String contentType = encoding == ByteEncoding.BASE64 ? MediaType.APPLICATION_OCTET_STREAM : MediaType.TEXT_PLAIN;
      WebTarget target = prepareWebTarget(url, queryParams);
      return executeStringRequest(target, "PUT", contentType, encodedData, headers, clazz);
   }

   public <T> T putBytes(String url, byte[] data, ByteEncoding encoding, Class<T> clazz)
   {
      return putBytes(url, data, encoding, null, null, clazz);
   }

   @Override
   public void close()
   {
      try
      {
         if (client != null)
         {
            client.close();
         }
      }
      catch (Exception e)
      {
         // Silent cleanup - log via filter if needed
      }
   }
}
