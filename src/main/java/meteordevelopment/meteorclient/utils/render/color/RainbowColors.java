package meteordevelopment.meteorclient.utils.render.color;

import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.waypoints.Waypoint;
import meteordevelopment.meteorclient.systems.waypoints.Waypoints;
import meteordevelopment.meteorclient.utils.PostInit;
import meteordevelopment.meteorclient.utils.misc.UnorderedArrayList;
import meteordevelopment.orbit.EventHandler;

public class RainbowColors {
   private static final List<Setting<SettingColor>> colorSettings = new UnorderedArrayList<>();
   private static final List<Setting<List<SettingColor>>> colorListSettings = new UnorderedArrayList<>();
   private static final List<SettingColor> colors = new UnorderedArrayList<>();
   private static final List<Runnable> listeners = new UnorderedArrayList<>();
   public static final RainbowColor GLOBAL = new RainbowColor();

   private RainbowColors() {
   }

   @PostInit
   public static void init() {
      MeteorClient.EVENT_BUS.subscribe(RainbowColors.class);
   }

   public static void addSetting(Setting<SettingColor> setting) {
      colorSettings.add(setting);
   }

   public static void addSettingList(Setting<List<SettingColor>> setting) {
      colorListSettings.add(setting);
   }

   public static void removeSetting(Setting<SettingColor> setting) {
      colorSettings.remove(setting);
   }

   public static void removeSettingList(Setting<List<SettingColor>> setting) {
      colorListSettings.remove(setting);
   }

   public static void add(SettingColor color) {
      colors.add(color);
   }

   public static void register(Runnable runnable) {
      listeners.add(runnable);
   }

   @EventHandler
   private static void onTick(TickEvent.Post event) {
      GLOBAL.setSpeed(Config.get().rainbowSpeed.get() / 100.0);
      GLOBAL.getNext();

      for (int i = 0; i < colorSettings.size(); i++) {
         Setting<SettingColor> setting = colorSettings.get(i);
         if (setting.module == null || setting.module.isActive()) {
            SettingColor sc = setting.get();
            if (sc != null && sc.rainbow) {
               sc.update();
            }
         }
      }

      for (int i = 0; i < colorListSettings.size(); i++) {
         Setting<List<SettingColor>> settingx = colorListSettings.get(i);
         if (settingx.module == null || settingx.module.isActive()) {
            List<SettingColor> list = settingx.get();
            if (list != null) {
               for (int j = 0; j < list.size(); j++) {
                  SettingColor color = list.get(j);
                  if (color != null && color.rainbow) {
                     color.update();
                  }
               }
            }
         }
      }

      for (int i = 0; i < colors.size(); i++) {
         SettingColor color = colors.get(i);
         if (color != null && color.rainbow) {
            color.update();
         }
      }

      for (Waypoint waypoint : Waypoints.get()) {
         SettingColor wc = waypoint.color.get();
         if (wc != null && wc.rainbow) {
            wc.update();
         }
      }

      if (MeteorClient.mc.screen instanceof WidgetScreen) {
         for (SettingGroup group : GuiThemes.get().settings) {
            for (Setting<?> settingxx : group) {
               if (settingxx instanceof ColorSetting) {
                  SettingColor sc = (SettingColor)settingxx.get();
                  if (sc != null && sc.rainbow) {
                     sc.update();
                  }
               }
            }
         }
      }

      for (int i = 0; i < listeners.size(); i++) {
         listeners.get(i).run();
      }
   }
}
