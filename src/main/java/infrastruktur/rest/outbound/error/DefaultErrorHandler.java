package infrastruktur.rest.outbound.error;

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
      throw new RuntimeException("Fel vid anrop till " + uri + ": " + statusCode);
   }
   
   @Override
   public void handleException(Exception exception, String uri)
   {
      if (exception instanceof RuntimeException)
      {
         throw (RuntimeException)exception;
      }
      throw new RuntimeException(exception);
   }
}