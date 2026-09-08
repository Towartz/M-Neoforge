package baritone.api;

import baritone.api.utils.SettingsUtil;

public final class BaritoneAPI {
   private static final IBaritoneProvider provider;
   private static final Settings settings = new Settings();

   public static IBaritoneProvider getProvider() {
      return provider;
   }

   public static Settings getSettings() {
      return settings;
   }

   static {
      SettingsUtil.readAndApply(settings, "settings.txt");

      try {
         provider = (IBaritoneProvider)Class.forName("baritone.BaritoneProvider").newInstance();
      } catch (ReflectiveOperationException var1) {
         throw new RuntimeException(var1);
      }
   }
}
