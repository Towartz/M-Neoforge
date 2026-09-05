package de.florianmichael.waybackauthlib;

public class InvalidCredentialsException extends InvalidRequestException {
   public InvalidCredentialsException(String message) {
      super(message);
   }
}
