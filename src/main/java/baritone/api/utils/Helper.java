package baritone.api.utils;

import baritone.api.BaritoneAPI;
import java.util.Arrays;
import java.util.Calendar;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public interface Helper {
   Helper HELPER = new Helper() {
   };
   @Deprecated
   Minecraft mc = Minecraft.getInstance();
   GuiMessageTag MESSAGE_TAG = new GuiMessageTag(16733695, null, Component.literal("Baritone message."), "Baritone");

   static Component getPrefix() {
      Calendar now = Calendar.getInstance();
      boolean xd = now.get(2) == 3 && now.get(5) <= 3;
      MutableComponent baritone = Component.literal(xd ? "Baritoe" : (BaritoneAPI.getSettings().shortBaritonePrefix.value ? "B" : "Baritone"));
      baritone.setStyle(baritone.getStyle().withColor(ChatFormatting.LIGHT_PURPLE));
      MutableComponent prefix = Component.literal("");
      prefix.setStyle(baritone.getStyle().withColor(ChatFormatting.DARK_PURPLE));
      prefix.append("[");
      prefix.append(baritone);
      prefix.append("]");
      return prefix;
   }

   default void logToast(Component title, Component message) {
      Minecraft.getInstance().execute(() -> BaritoneAPI.getSettings().toaster.value.accept(title, message));
   }

   default void logToast(String title, String message) {
      this.logToast(Component.literal(title), Component.literal(message));
   }

   default void logToast(String message) {
      this.logToast(getPrefix(), Component.literal(message));
   }

   default void logNotification(String message) {
      this.logNotification(message, false);
   }

   default void logNotification(String message, boolean error) {
      if (BaritoneAPI.getSettings().desktopNotifications.value) {
         this.logNotificationDirect(message, error);
      }
   }

   default void logNotificationDirect(String message) {
      this.logNotificationDirect(message, false);
   }

   default void logNotificationDirect(String message, boolean error) {
      Minecraft.getInstance().execute(() -> BaritoneAPI.getSettings().notifier.value.accept(message, error));
   }

   default void logDebug(String message) {
      if (BaritoneAPI.getSettings().chatDebug.value) {
         this.logDirect(message, false);
      }
   }

   default void logDirect(boolean logAsToast, Component... components) {
      MutableComponent component = Component.literal("");
      if (!logAsToast && !BaritoneAPI.getSettings().useMessageTag.value) {
         component.append(getPrefix());
         component.append(Component.literal(" "));
      }

      Arrays.asList(components).forEach(component::append);
      if (logAsToast) {
         this.logToast(getPrefix(), component);
      } else {
         Minecraft.getInstance().execute(() -> BaritoneAPI.getSettings().logger.value.accept(component));
      }
   }

   default void logDirect(Component... components) {
      this.logDirect(BaritoneAPI.getSettings().logAsToast.value, components);
   }

   default void logDirect(String message, ChatFormatting color, boolean logAsToast) {
      Stream.of(message.split("\n")).forEach(line -> {
         MutableComponent component = Component.literal(line.replace("\t", "    "));
         component.setStyle(component.getStyle().withColor(color));
         this.logDirect(logAsToast, component);
      });
   }

   default void logDirect(String message, ChatFormatting color) {
      this.logDirect(message, color, BaritoneAPI.getSettings().logAsToast.value);
   }

   default void logDirect(String message, boolean logAsToast) {
      this.logDirect(message, ChatFormatting.GRAY, logAsToast);
   }

   default void logDirect(String message) {
      this.logDirect(message, BaritoneAPI.getSettings().logAsToast.value);
   }

   default void logUnhandledException(Throwable exception) {
      HELPER.logDirect(
         "An unhandled exception occurred. The error is in your game's log, please report this at https://github.com/cabaletta/baritone/issues",
         ChatFormatting.RED
      );
      exception.printStackTrace();
   }
}
