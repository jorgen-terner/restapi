package infrastruktur.rest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

public class DataWithDateVO
{
   private LocalDate localDate;
   private LocalDateTime localDateTime;
   private OffsetDateTime offsetDateTime;
   private ZonedDateTime zonedDateTime;

   public DataWithDateVO()
   {
   }

   DataWithDateVO(LocalDate localDate, LocalDateTime localDateTime, OffsetDateTime offsetDateTime, ZonedDateTime zonedDateTime)
   {
      this.localDate = localDate;
      this.localDateTime = localDateTime;
      this.offsetDateTime = offsetDateTime;
      this.zonedDateTime = zonedDateTime;
   }

   public LocalDate getLocalDate()
   {
      return localDate;
   }

   public void setLocalDate(LocalDate localDate)
   {
      this.localDate = localDate;
   }

   public LocalDateTime getLocalDateTime()
   {
      return localDateTime;
   }

   public void setLocalDateTime(LocalDateTime localDateTime)
   {
      this.localDateTime = localDateTime;
   }

   public OffsetDateTime getOffsetDateTime()
   {
      return offsetDateTime;
   }

   public void setOffsetDateTime(OffsetDateTime offsetDateTime)
   {
      this.offsetDateTime = offsetDateTime;
   }

   public ZonedDateTime getZonedDateTime()
   {
      return zonedDateTime;
   }

   public void setZonedDateTime(ZonedDateTime zonedDateTime)
   {
      this.zonedDateTime = zonedDateTime;
   }
}
