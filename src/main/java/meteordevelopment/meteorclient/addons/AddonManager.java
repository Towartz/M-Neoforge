package meteordevelopment.meteorclient.addons;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import meteordevelopment.meteorclient.MeteorClient;

public class AddonManager {
   public static final List<MeteorAddon> ADDONS = new ArrayList<>();

   public static void init() {
      MeteorClient.ADDON = new MeteorAddon() {
         @Override
         public void onInitialize() {
         }

         @Override
         public String getPackage() {
            return "meteordevelopment.meteorclient";
         }

         @Override
         public String getWebsite() {
            return "https://meteorclient.com";
         }

         @Override
         public GithubRepo getRepo() {
            return new GithubRepo("MeteorDevelopment", "meteor-client");
         }

         @Override
         public String getCommit() {
            return null;
         }
      };
      MeteorClient.ADDON.name = "Meteor Client";
      MeteorClient.ADDON.authors = new String[]{"MineGame159", "squidoodly", "seasnail"};
      MeteorClient.ADDON.color.parse("145,61,226");

      ADDONS.add(MeteorClient.ADDON);

      for (MeteorAddon addon : ServiceLoader.load(MeteorAddon.class)) {
         if (addon.name == null) {
            addon.name = addon.getClass().getSimpleName();
         }
         if (addon.authors == null) {
            addon.authors = new String[]{"Unknown"};
         }
         ADDONS.add(addon);
      }
   }
}
