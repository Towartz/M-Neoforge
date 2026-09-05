package meteordevelopment.meteorclient.events.entity.player;

public class SendMovementPacketsEvent {
   public static class Post {
      private static final SendMovementPacketsEvent.Post INSTANCE = new SendMovementPacketsEvent.Post();

      public static SendMovementPacketsEvent.Post get() {
         return INSTANCE;
      }
   }

   public static class Pre {
      private static final SendMovementPacketsEvent.Pre INSTANCE = new SendMovementPacketsEvent.Pre();

      public static SendMovementPacketsEvent.Pre get() {
         return INSTANCE;
      }
   }
}
