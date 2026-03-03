package infrastruktur.rest.outbound.error;

/**
 * Hanterar HTTP-fel och exceptions i RestService.
 * 
 * Implementera detta interface för att anpassa felhanteringen baserat på HTTP-statuskoder
 * och responsinnehåll. Den registrerade ErrorHandlern anropas när RestService tar emot
 * ett svar med statuskod utanför 200-299 intervallet alternativt om ett Exception kastas.
 *  
 * <b>Användning:</b>
 * <pre>
 * RestService service = RestServiceBuilder.create()
 *     .errorHandler(new CustomErrorHandler())
 *     .build();
 * </pre>
 */
public interface ErrorHandler
{
   /**
    * Hanterar ett HTTP-fel.
    * 
    * Denna metod anropas när en HTTP-response har en statuskod utanför 200-299.
    * Implementationen förväntas kasta en exception för att indikera ett fel.
    * Om ingen exception kastas anses felets vara hanterat och ingen vidare bearbetning sker.
    * 
    * @param statusCode HTTP-statuskoden (t.ex. 400, 401, 404, 500)
    * @param responseBody Responsens body som text
    * @param uri Request-URIn
    * @throws RuntimeException eller annan exception för att indikera ett fel
    */
   void handleError(int statusCode, String responseBody, String uri) throws RuntimeException;

   /**
    * Hanterar exceptions som uppstår under request/response-hantering.
    *
    * Denna metod anropas för tekniska fel såsom serialisering, deserialisering,
    * anslutningsproblem eller andra oväntade exceptions.
    *
    * @param exception Exception som uppstod
    * @param uri Request-URIn om tillgänglig, annars null
    */
   void handleException(Exception exception, String uri);
}
