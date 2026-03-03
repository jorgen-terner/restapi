package infrastruktur.rest.inbound;

import com.fasterxml.jackson.databind.ObjectMapper;

import infrastruktur.rest.internal.RestClientMapperConfig;
import jakarta.ws.rs.ext.ContextResolver;

public class RestClientResolver implements ContextResolver<ObjectMapper>
{
   private final static RestClientMapperConfig MAPPER_CONFIG = new RestClientMapperConfig();
   private final ObjectMapper mapper;

   public RestClientResolver()
   {
      mapper = MAPPER_CONFIG.getAndConfigureObjectMapper();
   }

   @Override
   public ObjectMapper getContext(Class<?> type)
   {
      return mapper;
   }
}
