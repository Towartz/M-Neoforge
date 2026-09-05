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
      synchronized (colorSettings) {
         colorSettings.add(setting);
      }
   }

   public static void addSettingList(Setting<List<SettingColor>> setting) {
      synchronized (colorListSettings) {
         colorListSettings.add(setting);
      }
   }

   public static void removeSetting(Setting<SettingColor> setting) {
      synchronized (colorSettings) {
         colorSettings.remove(setting);
      }
   }

   public static void removeSettingList(Setting<List<SettingColor>> setting) {
      synchronized (colorListSettings) {
         colorListSettings.remove(setting);
      }
   }

   public static void add(SettingColor color) {
      synchronized (colors) {
         colors.add(color);
      }
   }

   public static void register(Runnable runnable) {
      synchronized (listeners) {
         listeners.add(runnable);
      }
   }

   @EventHandler
   private static void onTick(TickEvent.Post event) {
      GLOBAL.setSpeed(Config.get().rainbowSpeed.get() / 100.0);
      GLOBAL.getNext();

      synchronized (colorSettings) {
         for (int i = 0; i < colorSettings.size(); i++) {
            Setting<SettingColor> setting = colorSettings.get(i);
            if (setting != null && (setting.module == null || setting.module.isActive())) {
               SettingColor sc = setting.get();
               if (sc != null && sc.rainbow) {
                  sc.update();
               }
            }
         }
      }

      synchronized (colorListSettings) {
         for (int i = 0; i < colorListSettings.size(); i++) {
            Setting<List<SettingColor>> settingx = colorListSettings.get(i);
            if (settingx != null && (settingx.module == null || settingx.module.isActive())) {
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
      }

      synchronized (colors) {
         for (int i = 0; i < colors.size(); i++) {
            SettingColor color = colors.get(i);
            if (color != null && color.rainbow) {
               color.update();
            }
         }
      }

      if (Waypoints.get() != null) {
         for (Waypoint waypoint : Waypoints.get()) {
            if (waypoint != null && waypoint.color != null) {
               SettingColor wc = waypoint.color.get();
               if (wc != null && wc.rainbow) {
                  wc.update();
               }
            }
         }
      }

      if (MeteorClient.mc.screen instanceof WidgetScreen && GuiThemes.get() != null && GuiThemes.get().settings != null) {
         for (SettingGroup group : GuiThemes.get().settings) {
            if (group == null) continue;
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

      synchronized (listeners) {
         for (int i = 0; i < listeners.size(); i++) {
            try {
               listeners.get(i).run();
            } catch (Throwable ignored) {
            }
         }
      }
   }
}
