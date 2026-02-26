package com.example.restapi.client;

import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.ClientRequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;

/**
 * A JAX-RS ClientRequestFilter that logs HTTP request details using SLF4J.
 * 
 * Usage:
 *   RestService client = new RestService();
 *   client.registerClientRequestFilter(new LoggingFilter());
 */
public class LoggingFilter implements ClientRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(LoggingFilter.class);

    /**
     * Logs HTTP request method, URI, and all headers.
     * If the request contains an entity (body), it will also be logged at DEBUG level.
     *
     * @param ctx the ClientRequestContext containing request information
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void filter(ClientRequestContext ctx) throws IOException {
        // Log request line: method and URI
        LOG.info("→ {} {}", ctx.getMethod(), ctx.getUri());
        
        // Log all request headers
        ctx.getHeaders().forEach((name, values) -> {
            values.forEach(value -> {
                // Mask sensitive headers (Authorization, API keys, etc.)
                String displayValue = isSensitiveHeader(name) ? "***" : String.valueOf(value);
                LOG.debug("  {}: {}", name, displayValue);
            });
        });

        // Log request body if present
        if (ctx.hasEntity()) {
            Object entity = ctx.getEntity();
            if (entity != null) {
                String entityStr = entity.toString();
                if (entityStr.length() > 500) {
                    LOG.debug("  Body: {} (truncated, length: {})", 
                        entityStr.substring(0, 500) + "...", 
                        entityStr.length());
                } else {
                    LOG.debug("  Body: {}", entityStr);
                }
            }
        }
    }

    /**
     * Check if a header name is sensitive and should be masked in logs.
     */
    private boolean isSensitiveHeader(String name) {
        String lower = name.toLowerCase();
        return lower.contains("authorization") || 
               lower.contains("token") || 
               lower.contains("api-key") || 
               lower.contains("api_key") ||
               lower.contains("secret") ||
               lower.contains("password");
    }
}
