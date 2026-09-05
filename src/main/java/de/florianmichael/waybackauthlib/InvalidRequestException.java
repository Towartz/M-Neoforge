package de.florianmichael.waybackauthlib;

public class InvalidRequestException extends Exception {
   public final String error;
   public final String errorMessage;
   public final String cause;

   public InvalidRequestException(String error) {
      super(error);
      this.error = error;
      this.errorMessage = null;
      this.cause = null;
   }

   public InvalidRequestException(String error, String errorMessage, String cause) {
      super(error + ": " + errorMessage + " (" + cause + ")");
      this.error = error;
      this.errorMessage = errorMessage;
      this.cause = cause;
   }
}
