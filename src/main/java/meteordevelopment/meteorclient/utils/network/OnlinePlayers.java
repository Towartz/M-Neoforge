package meteordevelopment.meteorclient.utils.network;

public class OnlinePlayers {
   private static int tickCounter = 0;

   private OnlinePlayers() {
   }

   public static void update() {
      if (++tickCounter >= 6000) {
         tickCounter = 0;
         MeteorExecutor.execute(() -> Http.post("https://meteorclient.com/api/online/ping").ignoreExceptions().send());
      }
   }

   public static void leave() {
      MeteorExecutor.execute(() -> Http.post("https://meteorclient.com/api/online/leave").ignoreExceptions().send());
   }
}
