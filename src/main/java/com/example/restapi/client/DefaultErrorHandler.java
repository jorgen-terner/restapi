package com.example.restapi.client;

/**
 * Standard ErrorHandler som kastar RuntimeException för alla HTTP-fel.
 * 
 * Detta är default-implementationen som används om ingen ErrorHandler är registrerad.
 */
public class DefaultErrorHandler implements ErrorHandler
{
   @Override
   public void handleError(int statusCode, String responseBody, String uri) throws RuntimeException
   {
      throw new RuntimeException("Upstream error: " + statusCode);
   }
}
