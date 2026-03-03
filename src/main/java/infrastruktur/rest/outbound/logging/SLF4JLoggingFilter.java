package infrastruktur.rest.outbound.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;

/**
 * Ett JAX-RS ClientRequestFilter som loggar HTTP-request med SLF4J.
 * 
 * RestService har ingen inbyggd loggning. Registrera detta filter för att få
 * loggning via SLF4J. Skapa ett eget loggfilter för att logga med annat
 * ramverk.
 * 
 * <b>Användning:</b>
 * 
 * <pre>
 * RestService client = RestServiceBuilder.create()
 *       .registerFilter(new SLF4JLoggingFilter())
 *       .connectTimeout(5000)
 *       .readTimeout(10000)
 *       .build();
 * </pre>
 * 
 * <b>Log levels:</b>
 * <ul>
 * <li>DEBUG - Request-metod och URI</li>
 * <li>DEBUG - Request headers</li>
 * <li>DEBUG - Request body (trunkerad om > 500 chars)</li>
 * </ul>
 */
public class SLF4JLoggingFilter implements ClientRequestFilter
{
   private static final Logger LOG = LoggerFactory.getLogger(SLF4JLoggingFilter.class);

   /**
    * Loggar HTTP request method, URI, och alla headrar. Om requesten 
    * innehåller en Entity (body), loggas även den på DEBUG-nivå.
    *
    * @param ctx ClientRequestContext med information om anropet
    */
   @Override
   public void filter(ClientRequestContext ctx)
   {
      if (!LOG.isDebugEnabled())
      {
         return;
      }
      
      LOG.debug("→ {} {}", ctx.getMethod(), ctx.getUri());

      ctx.getHeaders().forEach((name, values) -> {
         values.forEach(value -> {
            // Dölj header med känslig info
            String displayValue = isSensitiveHeader(name) ? "***" : String.valueOf(value);
            LOG.debug("  {}: {}", name, displayValue);
         });
      });

      // Logga request body om den finns
      if (ctx.hasEntity())
      {
         Object entity = ctx.getEntity();
         if (entity != null)
         {
            String entityStr = entity.toString();
            if (entityStr.length() > 500)
            {
               LOG.debug("  Body: {} (truncated, length: {})",
                     entityStr.substring(0, 500) + "...",
                     entityStr.length());
            }
            else
            {
               LOG.debug("  Body: {}", entityStr);
            }
         }
      }
   }

   /**
    * Kolla om en header innehåller känslig information och filtrera isåfall
    * bort den från loggning.
    */
   private boolean isSensitiveHeader(String name)
   {
      String lower = name.toLowerCase();
      return lower.contains("pnr");
   }
}
