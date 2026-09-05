package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import joptsimple.internal.Strings;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.ClientPlayNetworkHandlerAccessor;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent.Action;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import org.apache.commons.lang3.StringUtils;

public class ServerCommand extends Command {
   private static final Set<String> ANTICHEAT_LIST = Set.of(
      "nocheatplus",
      "negativity",
      "warden",
      "horizon",
      "illegalstack",
      "coreprotect",
      "exploitsx",
      "vulcan",
      "abc",
      "spartan",
      "kauri",
      "anticheatreloaded",
      "witherac",
      "godseye",
      "matrix",
      "wraith",
      "antixrayheuristics",
      "grimac"
   );
   private static final Set<String> VERSION_ALIASES = Set.of("version", "ver", "about", "bukkit:version", "bukkit:ver", "bukkit:about");
   private String alias;
   private int ticks = 0;
   private boolean tick = false;
   private final List<String> plugins = new ArrayList<>();
   private final List<String> commandTreePlugins = new ArrayList<>();
   private static final Random RANDOM = new Random();

   public ServerCommand() {
      super("server", "Prints server information");
      MeteorClient.EVENT_BUS.subscribe(this);
   }

   @Override
   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      builder.executes(context -> {
         this.basicInfo();
         return 1;
      });
      builder.then(literal("info").executes(ctx -> {
         this.basicInfo();
         return 1;
      }));
      builder.then(literal("plugins").executes(ctx -> {
         this.plugins.addAll(this.commandTreePlugins);
         if (this.alias != null) {
            mc.getConnection().send(new ServerboundCommandSuggestionPacket(RANDOM.nextInt(200), this.alias + " "));
            this.tick = true;
         } else {
            this.printPlugins();
         }

         return 1;
      }));
      builder.then(literal("tps").executes(ctx -> {
         float tps = TickRate.INSTANCE.getTickRate();
         ChatFormatting color;
         if (tps > 17.0F) {
            color = ChatFormatting.GREEN;
         } else if (tps > 12.0F) {
            color = ChatFormatting.YELLOW;
         } else {
            color = ChatFormatting.RED;
         }

         this.info("Current TPS: %s%.2f(default).", new Object[]{color, tps});
         return 1;
      }));
   }

   private void basicInfo() {
      if (mc.hasSingleplayerServer()) {
         IntegratedServer server = mc.getSingleplayerServer();
         this.info("Singleplayer", new Object[0]);
         if (server != null) {
            this.info("Version: %s", new Object[]{server.getServerVersion()});
         }
      } else {
         ServerData server = mc.getCurrentServer();
         if (server == null) {
            this.info("Couldn't obtain any server information.", new Object[0]);
         } else {
            String ipv4 = "";

            try {
               ipv4 = InetAddress.getByName(server.ip).getHostAddress();
            } catch (UnknownHostException var5) {
            }

            MutableComponent ipText;
            if (ipv4.isEmpty()) {
               ipText = Component.literal(ChatFormatting.GRAY + server.ip);
               ipText.setStyle(
                  ipText.getStyle()
                     .withClickEvent(new ClickEvent(Action.COPY_TO_CLIPBOARD, server.ip))
                     .withHoverEvent(new HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal("Copy to clipboard")))
               );
            } else {
               ipText = Component.literal(ChatFormatting.GRAY + server.ip);
               ipText.setStyle(
                  ipText.getStyle()
                     .withClickEvent(new ClickEvent(Action.COPY_TO_CLIPBOARD, server.ip))
                     .withHoverEvent(new HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal("Copy to clipboard")))
               );
               MutableComponent ipv4Text = Component.literal(String.format("%s (%s)", ChatFormatting.GRAY, ipv4));
               ipv4Text.setStyle(
                  ipText.getStyle()
                     .withClickEvent(new ClickEvent(Action.COPY_TO_CLIPBOARD, ipv4))
                     .withHoverEvent(new HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal("Copy to clipboard")))
               );
               ipText.append(ipv4Text);
            }

            this.info(Component.literal(String.format("%sIP: ", ChatFormatting.GRAY)).append(ipText));
            this.info("Port: %d", new Object[]{ServerAddress.parseString(server.ip).getPort()});
            this.info("Type: %s", new Object[]{mc.getConnection().serverBrand() != null ? mc.getConnection().serverBrand() : "unknown"});
            this.info("Motd: %s", new Object[]{server.motd != null ? server.motd.getString() : "unknown"});
            this.info("Version: %s", new Object[]{server.version.getString()});
            this.info("Protocol version: %d", new Object[]{server.protocol});
            this.info(
               "Difficulty: %s (Local: %.2f)",
               new Object[]{
                  mc.level.getDifficulty().getDisplayName().getString(), mc.level.getCurrentDifficultyAt(mc.player.blockPosition()).getEffectiveDifficulty()
               }
            );
            this.info("Day: %d", new Object[]{mc.level.getDayTime() / 24000L});
            this.info("Permission level: %s", new Object[]{this.formatPerms()});
         }
      }
   }

   public String formatPerms() {
      int p = 5;

      while (!mc.player.hasPermissions(p) && p > 0) {
         p--;
      }
      return switch (p) {
         case 0 -> "0 (No Perms)";
         case 1 -> "1 (No Perms)";
         case 2 -> "2 (Player Command Access)";
         case 3 -> "3 (Server Command Access)";
         case 4 -> "4 (Operator)";
         default -> p + " (Unknown)";
      };
   }

   private void printPlugins() {
      this.plugins.sort(String.CASE_INSENSITIVE_ORDER);
      this.plugins.replaceAll(this::formatName);
      if (!this.plugins.isEmpty()) {
         this.info("Plugins (%d): %s ", new Object[]{this.plugins.size(), Strings.join(this.plugins.toArray(new String[0]), ", ")});
      } else {
         this.error("No plugins found.", new Object[0]);
      }

      this.tick = false;
      this.ticks = 0;
      this.plugins.clear();
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.tick) {
         this.ticks++;
         if (this.ticks >= 100) {
            this.printPlugins();
         }
      }
   }

   @EventHandler
   private void onSendPacket(PacketEvent.Send event) {
      if (this.tick && event.packet instanceof ServerboundCommandSuggestionPacket) {
         event.cancel();
      }
   }

   @EventHandler
   private void onReadPacket(PacketEvent.Receive event) {
      if (event.packet instanceof ClientboundCommandsPacket packet) {
         ClientPlayNetworkHandlerAccessor handler = (ClientPlayNetworkHandlerAccessor)event.connection.getPacketListener();
         this.commandTreePlugins.clear();
         this.alias = null;
         packet.getRoot(CommandBuildContext.simple(handler.getCombinedDynamicRegistries(), handler.getEnabledFeatures())).getChildren().forEach(node -> {
            String[] split = node.getName().split(":");
            if (split.length > 1 && !this.commandTreePlugins.contains(split[0])) {
               this.commandTreePlugins.add(split[0]);
            }

            if (this.alias == null && VERSION_ALIASES.contains(node.getName())) {
               this.alias = node.getName();
            }
         });
      }

      if (this.tick) {
         try {
            if (event.packet instanceof ClientboundCommandSuggestionsPacket packet) {
               Suggestions matches = packet.toSuggestions();
               if (matches.isEmpty()) {
                  this.error("An error occurred while trying to find plugins.", new Object[0]);
                  return;
               }

               for (Suggestion suggestion : matches.getList()) {
                  String pluginName = suggestion.getText();
                  if (!this.plugins.contains(pluginName.toLowerCase())) {
                     this.plugins.add(pluginName);
                  }
               }

               this.printPlugins();
            }
         } catch (Exception var7) {
            this.error("An error occurred while trying to find plugins.", new Object[0]);
         }
      }
   }

   private String formatName(String name) {
      if (ANTICHEAT_LIST.contains(name.toLowerCase())) {
         return String.format("%s%s(default)", ChatFormatting.RED, name);
      } else {
         return !StringUtils.containsIgnoreCase(name, "exploit")
               && !StringUtils.containsIgnoreCase(name, "cheat")
               && !StringUtils.containsIgnoreCase(name, "illegal")
            ? String.format("(highlight)%s(default)", name)
            : String.format("%s%s(default)", ChatFormatting.RED, name);
      }
   }
}
