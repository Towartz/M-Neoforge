package meteordevelopment.meteorclient.pathing;

import java.lang.reflect.InvocationTargetException;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.PreInit;

public class PathManagers {
   private static IPathManager INSTANCE = new NopPathManager();

   public static IPathManager get() {
      if (INSTANCE instanceof NopPathManager && BaritoneUtils.IS_AVAILABLE) {
         try {
            if (baritone.api.BaritoneAPI.getProvider() != null && baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone() != null) {
               INSTANCE = new BaritonePathManager();
               MeteorClient.LOG.info("Path Manager (deferred init): {}", INSTANCE.getName());
            }
         } catch (Throwable ignored) {
         }
      }
      return INSTANCE;
   }

   @PreInit
   public static void init() {
      if (exists("meteordevelopment.voyager.PathManager")) {
         try {
            INSTANCE = (IPathManager)Class.forName("meteordevelopment.voyager.PathManager").getConstructor().newInstance();
         } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | ClassNotFoundException | InstantiationException var1) {
            throw new RuntimeException(var1);
         }
      }

      if (exists("baritone.api.BaritoneAPI")) {
         BaritoneUtils.IS_AVAILABLE = true;
         if (INSTANCE instanceof NopPathManager) {
            try {
               if (baritone.api.BaritoneAPI.getProvider() != null && baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone() != null) {
                  INSTANCE = new BaritonePathManager();
               }
            } catch (Throwable t) {
               MeteorClient.LOG.warn("Could not initialize BaritonePathManager during PreInit: {}", t.getMessage());
            }
         }
      }

      MeteorClient.LOG.info("Path Manager: {}", INSTANCE.getName());
   }

   private static boolean exists(String name) {
      try {
         Class.forName(name);
         return true;
      } catch (ClassNotFoundException var2) {
         return false;
      }
   }
}
