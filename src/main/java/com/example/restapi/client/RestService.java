package com.example.restapi.client;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.Base64;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * <b>Med Jersey:</b>
 * <pre>
 * RestService service = RestServiceBuilder.create()
 *     .property("jersey.config.client.connectTimeout", 20000)
 *     .property("jersey.config.client.readTimeout", 20000)
 *     .build();
 * </pre>
 * 
 * <b>Med RESTEasy eller annat:</b>
 * <pre>
 * RestService service = RestServiceBuilder.create()
 *     // Sätt implementerings-specifika timeout properties
 *     .property("...", ...)
 *     .build();
 * </pre>
 * 
 * <b>Med default settings:</b>
 * <pre>
 * RestService service = RestServiceBuilder.create().build();
 * </pre>
 */

public class RestService implements AutoCloseable
{
   /**
    * Encoding-strategi för byte-arrays i HTTP-requests.
    */
   public enum ByteEncoding
   {
      BASE64, RAW, HEX
   }

   private static final Logger LOG = LoggerFactory.getLogger(RestService.class);

   private final static RestClientMapperConfig MAPPER_CONFIG = new RestClientMapperConfig();

   private final Client client;
   private final ObjectMapper mapper;

   /**
    * Skapar en RestService med en default JAX-RS Client.
    * För konfigurering av timeouts, använd RestServiceBuilder.create().
    */
   public RestService()
   {
      this(ClientBuilder.newClient());
   }
   
   /**
    * Paket-privat konstruktor för internt bruk.
    * Användare bör använda RestServiceBuilder istället.
    *
    * @param client En JAX-RS Client konfigurerad enligt din JAX-RS providers specifikation
    */
   RestService(Client client)
   {
      this.client = client;
      this.mapper = MAPPER_CONFIG.getAndConfigureObjectMapper();
   }

   // Finns standardfunktionalitet i apache httpclient men värt att dra in det beroendet?
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
      LOG.debug("Preparing request to: {}", fullUrl);
      return client.target(fullUrl);
   }

   private <T> T executeRequest(WebTarget target, String method, String contentType, 
                                String body, Map<String, String> headers, Class<T> clazz)
   {
      try
      {
         LOG.debug("{} request to: {}", method, target.getUri());
         
         var requestBuilder = target.request(MediaType.APPLICATION_JSON);
         
         // Lägg till custom headers
         if (headers != null)
         {
            for (Map.Entry<String, String> h : headers.entrySet())
            {
               if (h.getKey() != null && h.getValue() != null)
               {
                  requestBuilder = requestBuilder.header(h.getKey(), h.getValue());
                  LOG.trace("  Header: {} = {}", h.getKey(), h.getValue());
               }
            }
         }
         
         // Content-Type för requests med body
         if (contentType != null && body != null)
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
               response = requestBuilder.post(jakarta.ws.rs.client.Entity.text(body));
               break;
            case "PUT":
               response = requestBuilder.put(jakarta.ws.rs.client.Entity.text(body));
               break;
            default:
               throw new IllegalArgumentException("Unsupported method: " + method);
         }
         
         int status = response.getStatus();
         LOG.debug("{} response status: {}", method, status);
         
         if ((status >= 200) && (status < 300))
         {
            String responseBody = response.readEntity(String.class);
            LOG.trace("{} response body: {}", method, responseBody);
            return mapper.readValue(responseBody, clazz);
         }
         
         String errorBody = response.readEntity(String.class);
         LOG.warn("{} upstream error: {} - {}", method, status, errorBody);
         throw new RuntimeException("Upstream error: " + status);
      }
      catch (RuntimeException e)
      {
         throw e;
      }
      catch (Exception e)
      {
         LOG.error("{} request failed", method, e);
         throw new RuntimeException(e);
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

   public <T> T postJson(String url, String json, Map<String, String> headers, Map<String, String> queryParams, Class<T> clazz)
   {
      WebTarget target = prepareWebTarget(url, queryParams);
      LOG.trace("POST request body: {}", json);
      return executeRequest(target, "POST", MediaType.APPLICATION_JSON, json, headers, clazz);
   }

   public <T> T postJson(String url, String json, Class<T> clazz)
   {
      return postJson(url, json, null, null, clazz);
   }

   public <T> T putJson(String url, String json, Map<String, String> headers, Map<String, String> queryParams, Class<T> clazz)
   {
      WebTarget target = prepareWebTarget(url, queryParams);
      LOG.trace("PUT request body: {}", json);
      return executeRequest(target, "PUT", MediaType.APPLICATION_JSON, json, headers, clazz);
   }

   public <T> T putJson(String url, String json, Class<T> clazz)
   {
      return putJson(url, json, null, null, clazz);
   }

   public <T> T postBytes(String url, byte[] data, ByteEncoding encoding, Map<String, String> headers,
         Map<String, String> queryParams, Class<T> clazz)
   {
      String encodedData = encodeBytes(data, encoding);
      LOG.debug("POST bytes request to: {} (encoding: {})", url, encoding);
      LOG.trace("POST request body length: {}", data.length);
      
      String contentType = encoding == ByteEncoding.BASE64 ? MediaType.APPLICATION_OCTET_STREAM : MediaType.TEXT_PLAIN;
      WebTarget target = prepareWebTarget(url, queryParams);
      return executeRequest(target, "POST", contentType, encodedData, headers, clazz);
   }

   public <T> T postBytes(String url, byte[] data, ByteEncoding encoding, Class<T> clazz)
   {
      return postBytes(url, data, encoding, null, null, clazz);
   }

   public <T> T putBytes(String url, byte[] data, ByteEncoding encoding, Map<String, String> headers,
         Map<String, String> queryParams, Class<T> clazz)
   {
      String encodedData = encodeBytes(data, encoding);
      LOG.debug("PUT bytes request to: {} (encoding: {})", url, encoding);
      LOG.trace("PUT request body length: {}", data.length);
      
      String contentType = encoding == ByteEncoding.BASE64 ? MediaType.APPLICATION_OCTET_STREAM : MediaType.TEXT_PLAIN;
      WebTarget target = prepareWebTarget(url, queryParams);
      return executeRequest(target, "PUT", contentType, encodedData, headers, clazz);
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
            LOG.debug("JAX-RS Client closed");
         }
      }
      catch (Exception e)
      {
         LOG.warn("Failed to close JAX-RS Client", e);
      }
   }
}
