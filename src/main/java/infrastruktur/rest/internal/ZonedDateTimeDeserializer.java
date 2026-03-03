package infrastruktur.rest.internal;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class ZonedDateTimeDeserializer extends JsonDeserializer<ZonedDateTime>
{
   @Override
   public ZonedDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException
   {
      return ZonedDateTime.parse(p.getValueAsString(), DateTimeFormatter.ISO_ZONED_DATE_TIME);
   }
}
