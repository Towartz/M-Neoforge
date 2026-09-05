package meteordevelopment.meteorclient.utils.misc;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.PreInit;
import meteordevelopment.orbit.EventHandler;

public class CPSUtils {
   private static int clicks;
   private static int cps;
   private static int secondsClicking;
   private static int tickCounter;

   private CPSUtils() {
   }

   @PreInit
   public static void init() {
      MeteorClient.EVENT_BUS.subscribe(CPSUtils.class);
   }

   @EventHandler
   private static void onTick(TickEvent.Pre event) {
      if (++tickCounter >= 20) {
         tickCounter = 0;
         if (cps == 0) {
            clicks = 0;
            secondsClicking = 0;
         } else {
            secondsClicking++;
            cps = 0;
         }
      }
   }

   public static void onAttack() {
      clicks++;
      cps++;
   }

   public static int getCpsAverage() {
      return clicks / (secondsClicking == 0 ? 1 : secondsClicking);
   }
}
