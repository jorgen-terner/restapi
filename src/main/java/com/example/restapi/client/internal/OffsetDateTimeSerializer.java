package com.example.restapi.client.internal;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class OffsetDateTimeSerializer extends JsonSerializer<OffsetDateTime>
{
   @Override
   public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException
   {
      gen.writeString(value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
   }
}
