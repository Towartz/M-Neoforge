package meteordevelopment.meteorclient.addons;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import com.cope.meteormcp.MeteorMCPAddon;
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
      MeteorClient.ADDON.name = MeteorClient.NAME;
      MeteorClient.ADDON.authors = new String[]{"MineGame159", "squidoodly", "seasnail", "Towartz"};
      MeteorClient.ADDON.color.parse("255,42,85");

      ADDONS.add(MeteorClient.ADDON);

      MeteorMCPAddon mcpAddon = new MeteorMCPAddon();
      mcpAddon.name = "Meteor MCP";
      mcpAddon.authors = new String[]{"GhostTypes", "cope"};
      mcpAddon.color.parse("100,150,255");
      ADDONS.add(mcpAddon);

      for (MeteorAddon addon : ServiceLoader.load(MeteorAddon.class, Thread.currentThread().getContextClassLoader())) {
         registerAddon(addon);
      }
   }

   public static void registerAddon(MeteorAddon addon) {
      if (addon == null) return;
      for (MeteorAddon a : ADDONS) {
         if (a.getClass().equals(addon.getClass())) return;
      }
      if (addon.name == null) {
         addon.name = addon.getClass().getSimpleName();
      }
      if (addon.authors == null) {
         addon.authors = new String[]{"Unknown"};
      }
      ADDONS.add(addon);
   }
}
