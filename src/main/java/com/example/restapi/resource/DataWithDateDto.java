package com.example.restapi.resource;

import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * DTO för data med datum-fält för testning av datum-formatering.
 */
public class DataWithDateDto
{
   private Integer id;
   private String description;
   
   @JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
   private LocalDate createdDate;
   
   @JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
   private java.time.LocalDateTime modifiedDate;

   public DataWithDateDto()
   {
   }

   public DataWithDateDto(Integer id, String description, LocalDate createdDate, java.time.LocalDateTime modifiedDate)
   {
      this.id = id;
      this.description = description;
      this.createdDate = createdDate;
      this.modifiedDate = modifiedDate;
   }

   public Integer getId()
   {
      return id;
   }

   public void setId(Integer id)
   {
      this.id = id;
   }

   public String getDescription()
   {
      return description;
   }

   public void setDescription(String description)
   {
      this.description = description;
   }

   public LocalDate getCreatedDate()
   {
      return createdDate;
   }

   public void setCreatedDate(LocalDate createdDate)
   {
      this.createdDate = createdDate;
   }

   public java.time.LocalDateTime getModifiedDate()
   {
      return modifiedDate;
   }

   public void setModifiedDate(java.time.LocalDateTime modifiedDate)
   {
      this.modifiedDate = modifiedDate;
   }

   @Override
   public String toString()
   {
      return "DataWithDateDto{" +
         "id=" + id +
         ", description='" + description + '\'' +
         ", createdDate=" + createdDate +
         ", modifiedDate=" + modifiedDate +
         '}';
   }
}
