package meteordevelopment.meteorclient.utils.player;

import com.mojang.brigadier.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.mixininterface.IChatHud;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.utils.PostInit;
import meteordevelopment.meteorclient.utils.misc.text.MeteorClickEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.HoverEvent.Action;
import net.minecraft.util.Tuple;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class ChatUtils {
   private static final List<Tuple<String, Supplier<Component>>> customPrefixes = new ArrayList<>();
   private static String forcedPrefixClassName;
   private static Component PREFIX;

   private ChatUtils() {
   }

   @PostInit
   public static void init() {
      PREFIX = Component.empty()
         .setStyle(Style.EMPTY.applyFormat(ChatFormatting.GRAY))
         .append("[")
         .append(Component.literal("Meteor").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(MeteorClient.ADDON.color.getPacked()))))
         .append("] ");
   }

   public static Component getMeteorPrefix() {
      return PREFIX;
   }

   public static void registerCustomPrefix(String packageName, Supplier<Component> supplier) {
      for (Tuple<String, Supplier<Component>> pair : customPrefixes) {
         if (((String)pair.getA()).equals(packageName)) {
            pair.setB(supplier);
            return;
         }
      }

      customPrefixes.add(new Tuple(packageName, supplier));
   }

   public static void unregisterCustomPrefix(String packageName) {
      customPrefixes.removeIf(pair -> ((String)pair.getA()).equals(packageName));
   }

   public static void forceNextPrefixClass(Class<?> klass) {
      forcedPrefixClassName = klass.getName();
   }

   public static void sendPlayerMsg(String message) {
      MeteorClient.mc.gui.getChat().addRecentChat(message);
      if (message.startsWith("/")) {
         MeteorClient.mc.player.connection.sendCommand(message.substring(1));
      } else {
         MeteorClient.mc.player.connection.sendChat(message);
      }
   }

   public static void info(String message, Object... args) {
      sendMsg(ChatFormatting.GRAY, message, args);
   }

   public static void infoPrefix(String prefix, String message, Object... args) {
      sendMsg(0, prefix, ChatFormatting.LIGHT_PURPLE, ChatFormatting.GRAY, message, args);
   }

   public static void warning(String message, Object... args) {
      sendMsg(ChatFormatting.YELLOW, message, args);
   }

   public static void warningPrefix(String prefix, String message, Object... args) {
      sendMsg(0, prefix, ChatFormatting.LIGHT_PURPLE, ChatFormatting.YELLOW, message, args);
   }

   public static void error(String message, Object... args) {
      sendMsg(ChatFormatting.RED, message, args);
   }

   public static void errorPrefix(String prefix, String message, Object... args) {
      sendMsg(0, prefix, ChatFormatting.LIGHT_PURPLE, ChatFormatting.RED, message, args);
   }

   public static void sendMsg(Component message) {
      sendMsg(null, message);
   }

   public static void sendMsg(String prefix, Component message) {
      sendMsg(0, prefix, ChatFormatting.LIGHT_PURPLE, message);
   }

   public static void sendMsg(ChatFormatting color, String message, Object... args) {
      sendMsg(0, null, null, color, message, args);
   }

   public static void sendMsg(int id, ChatFormatting color, String message, Object... args) {
      sendMsg(id, null, null, color, message, args);
   }

   public static void sendMsg(
      int id, @Nullable String prefixTitle, @Nullable ChatFormatting prefixColor, ChatFormatting messageColor, String messageContent, Object... args
   ) {
      MutableComponent message = formatMsg(String.format(messageContent, args), messageColor);
      sendMsg(id, prefixTitle, prefixColor, message);
   }

   public static void sendMsg(int id, @Nullable String prefixTitle, @Nullable ChatFormatting prefixColor, String messageContent, ChatFormatting messageColor) {
      MutableComponent message = formatMsg(messageContent, messageColor);
      sendMsg(id, prefixTitle, prefixColor, message);
   }

   public static void sendMsg(int id, @Nullable String prefixTitle, @Nullable ChatFormatting prefixColor, Component msg) {
      if (MeteorClient.mc.level != null) {
         MutableComponent message = Component.empty();
         message.append(getPrefix());
         if (prefixTitle != null) {
            message.append(getCustomPrefix(prefixTitle, prefixColor));
         }

         message.append(msg);
         if (!Config.get().deleteChatFeedback.get()) {
            id = 0;
         }

         ((IChatHud)MeteorClient.mc.gui.getChat()).meteor$add(message, id);
      }
   }

   private static MutableComponent getCustomPrefix(String prefixTitle, ChatFormatting prefixColor) {
      MutableComponent prefix = Component.empty();
      prefix.setStyle(prefix.getStyle().applyFormat(ChatFormatting.GRAY));
      prefix.append("[");
      MutableComponent moduleTitle = Component.literal(prefixTitle);
      moduleTitle.setStyle(moduleTitle.getStyle().applyFormat(prefixColor));
      prefix.append(moduleTitle);
      prefix.append("] ");
      return prefix;
   }

   private static Component getPrefix() {
      if (customPrefixes.isEmpty()) {
         forcedPrefixClassName = null;
         return PREFIX;
      } else {
         boolean foundChatUtils = false;
         String className = null;
         if (forcedPrefixClassName != null) {
            className = forcedPrefixClassName;
            forcedPrefixClassName = null;
         } else {
            for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
               if (foundChatUtils) {
                  if (!element.getClassName().equals(ChatUtils.class.getName())) {
                     className = element.getClassName();
                     break;
                  }
               } else if (element.getClassName().equals(ChatUtils.class.getName())) {
                  foundChatUtils = true;
               }
            }
         }

         if (className == null) {
            return PREFIX;
         } else {
            for (Tuple<String, Supplier<Component>> pair : customPrefixes) {
               if (className.startsWith((String)pair.getA())) {
                  Component prefix = (Component)((Supplier)pair.getB()).get();
                  return prefix != null ? prefix : PREFIX;
               }
            }

            return PREFIX;
         }
      }
   }

   private static MutableComponent formatMsg(String message, ChatFormatting defaultColor) {
      StringReader reader = new StringReader(message);
      MutableComponent text = Component.empty();
      Style style = Style.EMPTY.applyFormat(defaultColor);
      StringBuilder result = new StringBuilder();
      boolean formatting = false;

      while (reader.canRead()) {
         char c = reader.read();
         if (c == '(') {
            text.append(Component.literal(result.toString()).setStyle(style));
            result.setLength(0);
            result.append(c);
            formatting = true;
         } else {
            result.append(c);
            if (formatting && c == ')') {
               String var8 = result.toString();
               switch (var8) {
                  case "(default)":
                     style = style.applyFormat(defaultColor);
                     result.setLength(0);
                     break;
                  case "(highlight)":
                     style = style.applyFormat(ChatFormatting.WHITE);
                     result.setLength(0);
                     break;
                  case "(underline)":
                     style = style.applyFormat(ChatFormatting.UNDERLINE);
                     result.setLength(0);
                     break;
                  case "(bold)":
                     style = style.applyFormat(ChatFormatting.BOLD);
                     result.setLength(0);
               }

               formatting = false;
            }
         }
      }

      if (!result.isEmpty()) {
         text.append(Component.literal(result.toString()).setStyle(style));
      }

      return text;
   }

   public static MutableComponent formatCoords(Vec3 pos) {
      String coordsString = String.format("(highlight)(underline)%.0f, %.0f, %.0f(default)", pos.x, pos.y, pos.z);
      MutableComponent coordsText = formatMsg(coordsString, ChatFormatting.GRAY);
      if (BaritoneUtils.IS_AVAILABLE) {
         Style style = coordsText.getStyle()
            .applyFormat(ChatFormatting.BOLD)
            .withHoverEvent(new HoverEvent(Action.SHOW_TEXT, Component.literal("Set as Baritone goal")))
            .withClickEvent(
               new MeteorClickEvent(
                  net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                  String.format("%sgoto %d %d %d", BaritoneUtils.getPrefix(), (int)pos.x, (int)pos.y, (int)pos.z)
               )
            );
         coordsText.setStyle(style);
      }

      return coordsText;
   }
}
