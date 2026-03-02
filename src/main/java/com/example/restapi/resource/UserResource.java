package com.example.restapi.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.restapi.vo.UserVO;

/**
 * REST-resurs för användarhantering.
 * 
 * Denna resurs demonstrerar hur man kan bygga enkla REST-endpoints
 * och testa dem med REST Assured och WireMock.
 */
@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource
{
   private static final Logger LOG = LoggerFactory.getLogger(UserResource.class);

   /**
    * Hämtar en användare efter ID.
    * 
    * @param userId Användar-ID
   * @return UserVO-objekt eller 404 om användaren inte finns
    */
   @GET
   @Path("/{id}")
   public Response getUser(@PathParam("id") Long userId)
   {
      LOG.debug("Hämtar användare med ID: {}", userId);
      
      // I en verklig applikation skulle detta hämtas från en databas
      if (userId == null || userId < 1)
      {
         return Response.status(Response.Status.BAD_REQUEST)
            .entity("Ogiltigt användar-ID").build();
      }
      
      UserVO user = new UserVO(userId, "Test User " + userId, 
                                 "user" + userId + "@example.com");
      return Response.ok(user).build();
   }

   /**
    * Skapar en ny användare.
    * 
   * @param user UserVO-objekt med användardata
   * @return Skapad UserVO-objekt med genererat ID eller 400 vid validationsfel
    */
   @POST
   public Response createUser(UserVO user)
   {
      LOG.debug("Skapar ny användare: {}", user);
      
      // Validering
      if (user.getName() == null || user.getName().isEmpty())
      {
         return Response.status(Response.Status.BAD_REQUEST)
            .entity("Namn är obligatoriskt").build();
      }
      
      if (user.getEmail() == null || user.getEmail().isEmpty())
      {
         return Response.status(Response.Status.BAD_REQUEST)
            .entity("E-post är obligatorisk").build();
      }
      
      // Generera ett ID (i verklig app skulle detta komma från databas)
      user.setId(System.currentTimeMillis() % 100000);
      
      LOG.info("Användare skapad med ID: {}", user.getId());
      return Response.status(Response.Status.CREATED)
         .entity(user).build();
   }
}
