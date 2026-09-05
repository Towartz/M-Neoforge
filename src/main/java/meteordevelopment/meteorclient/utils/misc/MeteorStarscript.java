package meteordevelopment.meteorclient.utils.misc;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.Goal;
import baritone.api.process.IBaritoneProcess;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.mixin.ClientPlayerInteractionManagerAccessor;
import meteordevelopment.meteorclient.mixin.MinecraftClientAccessor;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.PreInit;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.world.Dimension;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.starscript.Script;
import meteordevelopment.starscript.Section;
import meteordevelopment.starscript.StandardLib;
import meteordevelopment.starscript.Starscript;
import meteordevelopment.starscript.compiler.Compiler;
import meteordevelopment.starscript.compiler.Parser;
import meteordevelopment.starscript.utils.Error;
import meteordevelopment.starscript.utils.StarscriptError;
import meteordevelopment.starscript.value.Value;
import meteordevelopment.starscript.value.ValueMap;
import net.minecraft.ResourceLocationException;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.apache.commons.lang3.StringUtils;

public class MeteorStarscript {
   public static Starscript ss = new Starscript();
   private static final MutableBlockPos BP = new MutableBlockPos();
   private static final StringBuilder SB = new StringBuilder();
   private static long lastRequestedStatsTime = 0L;

   @PreInit(
      dependencies = {PathManagers.class}
   )
   public static void init() {
      StandardLib.init(ss);
      ss.set("mc_version", SharedConstants.getCurrentVersion().getName());
      ss.set("fps", () -> Value.number((double)MinecraftClientAccessor.getFps()));
      ss.set("ping", MeteorStarscript::ping);
      ss.set("time", () -> Value.string(LocalTime.now().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))));
      ss.set("cps", () -> Value.number((double)CPSUtils.getCpsAverage()));
      ss.set(
         "meteor",
         new ValueMap()
            .set("name", MeteorClient.NAME)
            .set(
               "version",
               MeteorClient.VERSION != null
                  ? (MeteorClient.DEV_BUILD.isEmpty() ? MeteorClient.VERSION.toString() : MeteorClient.VERSION + " " + MeteorClient.DEV_BUILD)
                  : ""
            )
            .set("modules", () -> Value.number((double)Modules.get().getAll().size()))
            .set("active_modules", () -> Value.number((double)Modules.get().getActive().size()))
            .set("is_module_active", MeteorStarscript::isModuleActive)
            .set("get_module_info", MeteorStarscript::getModuleInfo)
            .set("get_module_setting", MeteorStarscript::getModuleSetting)
            .set("prefix", MeteorStarscript::getMeteorPrefix)
      );
      if (BaritoneUtils.IS_AVAILABLE) {
         ss.set(
            "baritone",
            new ValueMap()
               .set("is_pathing", () -> Value.bool(BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()))
               .set("distance_to_goal", MeteorStarscript::baritoneDistanceToGoal)
               .set("process", MeteorStarscript::baritoneProcess)
               .set("process_name", MeteorStarscript::baritoneProcessName)
               .set("eta", MeteorStarscript::baritoneETA)
         );
      }

      ss.set(
         "camera",
         new ValueMap()
            .set(
               "pos",
               new ValueMap()
                  .set("_toString", () -> posString(false, true))
                  .set("x", () -> Value.number(MeteorClient.mc.gameRenderer.getMainCamera().getPosition().x))
                  .set("y", () -> Value.number(MeteorClient.mc.gameRenderer.getMainCamera().getPosition().y))
                  .set("z", () -> Value.number(MeteorClient.mc.gameRenderer.getMainCamera().getPosition().z))
            )
            .set(
               "opposite_dim_pos",
               new ValueMap()
                  .set("_toString", () -> posString(true, true))
                  .set("x", () -> oppositeX(true))
                  .set("y", () -> Value.number(MeteorClient.mc.gameRenderer.getMainCamera().getPosition().y))
                  .set("z", () -> oppositeZ(true))
            )
            .set("yaw", () -> yaw(true))
            .set("pitch", () -> pitch(true))
            .set("direction", () -> direction(true))
      );
      ss.set(
         "player",
         new ValueMap()
            .set("_toString", () -> Value.string(MeteorClient.mc.getUser().getName()))
            .set("health", () -> Value.number(MeteorClient.mc.player != null ? (double)MeteorClient.mc.player.getHealth() : 0.0))
            .set("absorption", () -> Value.number(MeteorClient.mc.player != null ? (double)MeteorClient.mc.player.getAbsorptionAmount() : 0.0))
            .set("hunger", () -> Value.number(MeteorClient.mc.player != null ? (double)MeteorClient.mc.player.getFoodData().getFoodLevel() : 0.0))
            .set("speed", () -> Value.number(Utils.getPlayerSpeed().horizontalDistance()))
            .set(
               "speed_all",
               new ValueMap()
                  .set("_toString", () -> Value.string(MeteorClient.mc.player != null ? Utils.getPlayerSpeed().toString() : ""))
                  .set("x", () -> Value.number(MeteorClient.mc.player != null ? Utils.getPlayerSpeed().x : 0.0))
                  .set("y", () -> Value.number(MeteorClient.mc.player != null ? Utils.getPlayerSpeed().y : 0.0))
                  .set("z", () -> Value.number(MeteorClient.mc.player != null ? Utils.getPlayerSpeed().z : 0.0))
            )
            .set(
               "breaking_progress",
               () -> Value.number(
                     MeteorClient.mc.gameMode != null ? (double)((ClientPlayerInteractionManagerAccessor)MeteorClient.mc.gameMode).getBreakingProgress() : 0.0
                  )
            )
            .set("biome", MeteorStarscript::biome)
            .set("dimension", () -> Value.string(PlayerUtils.getDimension().name()))
            .set("opposite_dimension", () -> Value.string(PlayerUtils.getDimension().opposite().name()))
            .set(
               "gamemode", () -> PlayerUtils.getGameMode() != null ? Value.string(StringUtils.capitalize(PlayerUtils.getGameMode().getName())) : Value.null_()
            )
            .set(
               "pos",
               new ValueMap()
                  .set("_toString", () -> posString(false, false))
                  .set("x", () -> Value.number(MeteorClient.mc.player != null ? MeteorClient.mc.player.getX() : 0.0))
                  .set("y", () -> Value.number(MeteorClient.mc.player != null ? MeteorClient.mc.player.getY() : 0.0))
                  .set("z", () -> Value.number(MeteorClient.mc.player != null ? MeteorClient.mc.player.getZ() : 0.0))
            )
            .set(
               "opposite_dim_pos",
               new ValueMap()
                  .set("_toString", () -> posString(true, false))
                  .set("x", () -> oppositeX(false))
                  .set("y", () -> Value.number(MeteorClient.mc.player != null ? MeteorClient.mc.player.getY() : 0.0))
                  .set("z", () -> oppositeZ(false))
            )
            .set("yaw", () -> yaw(false))
            .set("pitch", () -> pitch(false))
            .set("direction", () -> direction(false))
            .set("hand", () -> MeteorClient.mc.player != null ? wrap(MeteorClient.mc.player.getMainHandItem()) : Value.null_())
            .set("offhand", () -> MeteorClient.mc.player != null ? wrap(MeteorClient.mc.player.getOffhandItem()) : Value.null_())
            .set("hand_or_offhand", MeteorStarscript::handOrOffhand)
            .set("get_item", MeteorStarscript::getItem)
            .set("count_items", MeteorStarscript::countItems)
            .set(
               "xp",
               new ValueMap()
                  .set("level", () -> Value.number(MeteorClient.mc.player != null ? (double)MeteorClient.mc.player.experienceLevel : 0.0))
                  .set("progress", () -> Value.number(MeteorClient.mc.player != null ? (double)MeteorClient.mc.player.experienceProgress : 0.0))
                  .set("total", () -> Value.number(MeteorClient.mc.player != null ? (double)MeteorClient.mc.player.totalExperience : 0.0))
            )
            .set("has_potion_effect", MeteorStarscript::hasPotionEffect)
            .set("get_potion_effect", MeteorStarscript::getPotionEffect)
            .set("get_stat", MeteorStarscript::getStat)
      );
      ss.set("crosshair_target", new ValueMap().set("type", MeteorStarscript::crosshairType).set("value", MeteorStarscript::crosshairValue));
      ss.set(
         "server",
         new ValueMap()
            .set("_toString", () -> Value.string(Utils.getWorldName()))
            .set("tps", () -> Value.number((double)TickRate.INSTANCE.getTickRate()))
            .set("time", () -> Value.string(Utils.getWorldTime()))
            .set(
               "player_count",
               () -> Value.number(MeteorClient.mc.getConnection() != null ? (double)MeteorClient.mc.getConnection().getOnlinePlayers().size() : 0.0)
            )
            .set("difficulty", () -> Value.string(MeteorClient.mc.level != null ? MeteorClient.mc.level.getDifficulty().getKey() : ""))
      );
   }

   public static Script compile(String source) {
      Parser.Result result = Parser.parse(source);
      if (!result.hasErrors()) {
         return Compiler.compile(result);
      } else {
         for (Error error : result.errors) {
            printChatError(error);
         }

         return null;
      }
   }

   public static Section runSection(Script script, StringBuilder sb) {
      try {
         return ss.run(script, sb);
      } catch (StarscriptError var3) {
         printChatError(var3);
         return null;
      }
   }

   public static String run(Script script, StringBuilder sb) {
      Section section = runSection(script, sb);
      return section != null ? section.toString() : null;
   }

   public static Section runSection(Script script) {
      return runSection(script, new StringBuilder());
   }

   public static String run(Script script) {
      return run(script, new StringBuilder());
   }

   public static void printChatError(int i, Error error) {
      String caller = getCallerName();
      if (caller != null) {
         if (i != -1) {
            ChatUtils.errorPrefix("Starscript", "%d, %d '%c': %s (from %s)", i, error.character, error.ch, error.message, caller);
         } else {
            ChatUtils.errorPrefix("Starscript", "%d '%c': %s (from %s)", error.character, error.ch, error.message, caller);
         }
      } else if (i != -1) {
         ChatUtils.errorPrefix("Starscript", "%d, %d '%c': %s", i, error.character, error.ch, error.message);
      } else {
         ChatUtils.errorPrefix("Starscript", "%d '%c': %s", error.character, error.ch, error.message);
      }
   }

   public static void printChatError(Error error) {
      printChatError(-1, error);
   }

   public static void printChatError(StarscriptError e) {
      String caller = getCallerName();
      if (caller != null) {
         ChatUtils.errorPrefix("Starscript", "%s (from %s)", e.getMessage(), caller);
      } else {
         ChatUtils.errorPrefix("Starscript", "%s", e.getMessage());
      }
   }

   private static String getCallerName() {
      StackTraceElement[] elements = Thread.currentThread().getStackTrace();
      if (elements.length == 0) {
         return null;
      } else {
         for (int i = 1; i < elements.length; i++) {
            String name = elements[i].getClassName();
            if (!name.startsWith(Starscript.class.getPackageName()) && !name.equals(MeteorStarscript.class.getName())) {
               return name.substring(name.lastIndexOf(46) + 1);
            }
         }

         return null;
      }
   }

   private static Value hasPotionEffect(Starscript ss, int argCount) {
      if (argCount < 1) {
         ss.error("player.has_potion_effect() requires 1 argument, got %d.", argCount);
      }

      if (MeteorClient.mc.player == null) {
         return Value.bool(false);
      } else {
         ResourceLocation name = popIdentifier(ss, "First argument to player.has_potion_effect() needs to a string.");
         Optional<Reference<MobEffect>> effect = BuiltInRegistries.MOB_EFFECT.getHolder(name);
         if (effect.isEmpty()) {
            return Value.bool(false);
         } else {
            MobEffectInstance effectInstance = MeteorClient.mc.player.getEffect((Holder)effect.get());
            return Value.bool(effectInstance != null);
         }
      }
   }

   private static Value getPotionEffect(Starscript ss, int argCount) {
      if (argCount < 1) {
         ss.error("player.get_potion_effect() requires 1 argument, got %d.", argCount);
      }

      if (MeteorClient.mc.player == null) {
         return Value.null_();
      } else {
         ResourceLocation name = popIdentifier(ss, "First argument to player.get_potion_effect() needs to a string.");
         Optional<Reference<MobEffect>> effect = BuiltInRegistries.MOB_EFFECT.getHolder(name);
         if (effect.isEmpty()) {
            return Value.null_();
         } else {
            MobEffectInstance effectInstance = MeteorClient.mc.player.getEffect((Holder)effect.get());
            return effectInstance == null ? Value.null_() : wrap(effectInstance);
         }
      }
   }

   private static Value getStat(Starscript ss, int argCount) {
      if (argCount < 1) {
         ss.error("player.get_stat() requires 1 argument, got %d.", argCount);
      }

      if (MeteorClient.mc.player == null) {
         return Value.number(0.0);
      } else {
         long time = System.currentTimeMillis();
         if ((double)(time - lastRequestedStatsTime) / 1000.0 >= 1.0 && MeteorClient.mc.getConnection() != null) {
            MeteorClient.mc.getConnection().send(new ServerboundClientCommandPacket(Action.REQUEST_STATS));
            lastRequestedStatsTime = time;
         }

         String type = argCount > 1 ? ss.popString("First argument to player.get_stat() needs to be a string.") : "custom";
         ResourceLocation name = popIdentifier(ss, (argCount > 1 ? "Second" : "First") + " argument to player.get_stat() needs to be a string.");

         Stat<?> stat = switch (type) {
            case "mined" -> Stats.BLOCK_MINED.get((Block)BuiltInRegistries.BLOCK.get(name));
            case "crafted" -> Stats.ITEM_CRAFTED.get((Item)BuiltInRegistries.ITEM.get(name));
            case "used" -> Stats.ITEM_USED.get((Item)BuiltInRegistries.ITEM.get(name));
            case "broken" -> Stats.ITEM_BROKEN.get((Item)BuiltInRegistries.ITEM.get(name));
            case "picked_up" -> Stats.ITEM_PICKED_UP.get((Item)BuiltInRegistries.ITEM.get(name));
            case "dropped" -> Stats.ITEM_DROPPED.get((Item)BuiltInRegistries.ITEM.get(name));
            case "killed" -> Stats.ENTITY_KILLED.get((EntityType)BuiltInRegistries.ENTITY_TYPE.get(name));
            case "killed_by" -> Stats.ENTITY_KILLED_BY.get((EntityType)BuiltInRegistries.ENTITY_TYPE.get(name));
            case "custom" -> {
               name = (ResourceLocation)BuiltInRegistries.CUSTOM_STAT.get(name);
               yield name != null ? Stats.CUSTOM.get(name) : null;
            }
            default -> null;
         };
         return Value.number(stat != null ? (double)MeteorClient.mc.player.getStats().getValue(stat) : 0.0);
      }
   }

   private static Value getModuleInfo(Starscript ss, int argCount) {
      if (argCount != 1) {
         ss.error("meteor.get_module_info() requires 1 argument, got %d.", argCount);
      }

      Module module = Modules.get().get(ss.popString("First argument to meteor.get_module_info() needs to be a string."));
      if (module != null && module.isActive()) {
         String info = module.getInfoString();
         return Value.string(info == null ? "" : info);
      } else {
         return Value.string("");
      }
   }

   private static Value getModuleSetting(Starscript ss, int argCount) {
      if (argCount != 2) {
         ss.error("meteor.get_module_setting() requires 2 arguments, got %d.", argCount);
      }

      String settingName = ss.popString("Second argument to meteor.get_module_setting() needs to be a string.");
      String moduleName = ss.popString("First argument to meteor.get_module_setting() needs to be a string.");
      Module module = Modules.get().get(moduleName);
      if (module == null) {
         ss.error("Unable to get module %s for meteor.get_module_setting()", moduleName);
      }

      Setting<?> setting = module.settings.get(settingName);
      if (setting == null) {
         ss.error("Unable to get setting %s for module %s for meteor.get_module_setting()", settingName, moduleName);
      }

      Object value = setting.get();

      if (value == null) return Value.null_();
      return switch (value) {
         case Double d -> Value.number(d);
         case Integer i -> Value.number((double)i.intValue());
         case Boolean b -> Value.bool(b);
         case List list -> Value.number((double)list.size());
         default -> Value.string(value.toString());
      };
   }

   private static Value isModuleActive(Starscript ss, int argCount) {
      if (argCount != 1) {
         ss.error("meteor.is_module_active() requires 1 argument, got %d.", argCount);
      }

      Module module = Modules.get().get(ss.popString("First argument to meteor.is_module_active() needs to be a string."));
      return Value.bool(module != null && module.isActive());
   }

   private static Value getItem(Starscript ss, int argCount) {
      if (argCount != 1) {
         ss.error("player.get_item() requires 1 argument, got %d.", argCount);
      }

      int i = (int)ss.popNumber("First argument to player.get_item() needs to be a number.");
      if (i < 0) {
         ss.error("First argument to player.get_item() needs to be a non-negative integer.", i);
      }

      return MeteorClient.mc.player != null ? wrap(MeteorClient.mc.player.getInventory().getItem(i)) : Value.null_();
   }

   private static Value countItems(Starscript ss, int argCount) {
      if (argCount != 1) {
         ss.error("player.count_items() requires 1 argument, got %d.", argCount);
      }

      String idRaw = ss.popString("First argument to player.count_items() needs to be a string.");
      ResourceLocation id = ResourceLocation.tryParse(idRaw);
      if (id == null) {
         return Value.number(0.0);
      } else {
         Item item = (Item)BuiltInRegistries.ITEM.get(id);
         if (item != Items.AIR && MeteorClient.mc.player != null) {
            int count = 0;

            for (int i = 0; i < MeteorClient.mc.player.getInventory().getContainerSize(); i++) {
               ItemStack itemStack = MeteorClient.mc.player.getInventory().getItem(i);
               if (itemStack.getItem() == item) {
                  count += itemStack.getCount();
               }
            }

            return Value.number((double)count);
         } else {
            return Value.number(0.0);
         }
      }
   }

   private static Value getMeteorPrefix() {
      return Config.get() == null ? Value.null_() : Value.string(Config.get().prefix.get());
   }

   private static Value baritoneProcess() {
      Optional<IBaritoneProcess> process = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingControlManager().mostRecentInControl();
      return Value.string(process.isEmpty() ? "" : process.get().displayName0());
   }

   private static Value baritoneProcessName() {
      Optional<IBaritoneProcess> process = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingControlManager().mostRecentInControl();
      if (process.isEmpty()) {
         return Value.string("");
      } else {
         String className = process.get().getClass().getSimpleName();
         if (className.endsWith("Process")) {
            className = className.substring(0, className.length() - 7);
         }

         SB.append(className);
         int i = 0;

         for (int j = 0; j < className.length(); j++) {
            if (j > 0 && Character.isUpperCase(className.charAt(j))) {
               SB.insert(i, ' ');
               i++;
            }

            i++;
         }

         String name = SB.toString();
         SB.setLength(0);
         return Value.string(name);
      }
   }

   private static Value baritoneETA() {
      if (MeteorClient.mc.player == null) {
         return Value.number(0.0);
      } else {
         Optional<Double> ticksTillGoal = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().estimatedTicksToGoal();
         return ticksTillGoal.<Value>map(aDouble -> Value.number(aDouble / 20.0)).orElseGet(() -> Value.number(0.0));
      }
   }

   private static Value oppositeX(boolean camera) {
      double x = camera ? MeteorClient.mc.gameRenderer.getMainCamera().getPosition().x : (MeteorClient.mc.player != null ? MeteorClient.mc.player.getX() : 0.0);
      Dimension dimension = PlayerUtils.getDimension();
      if (dimension == Dimension.Overworld) {
         x /= 8.0;
      } else if (dimension == Dimension.Nether) {
         x *= 8.0;
      }

      return Value.number(x);
   }

   private static Value oppositeZ(boolean camera) {
      double z = camera ? MeteorClient.mc.gameRenderer.getMainCamera().getPosition().z : (MeteorClient.mc.player != null ? MeteorClient.mc.player.getZ() : 0.0);
      Dimension dimension = PlayerUtils.getDimension();
      if (dimension == Dimension.Overworld) {
         z /= 8.0;
      } else if (dimension == Dimension.Nether) {
         z *= 8.0;
      }

      return Value.number(z);
   }

   private static Value yaw(boolean camera) {
      float yaw;
      if (camera) {
         yaw = MeteorClient.mc.gameRenderer.getMainCamera().getYRot();
      } else {
         yaw = MeteorClient.mc.player != null ? MeteorClient.mc.player.getYRot() : 0.0F;
      }

      yaw %= 360.0F;
      if (yaw < 0.0F) {
         yaw += 360.0F;
      }

      if (yaw > 180.0F) {
         yaw -= 360.0F;
      }

      return Value.number((double)yaw);
   }

   private static Value pitch(boolean camera) {
      float pitch;
      if (camera) {
         pitch = MeteorClient.mc.gameRenderer.getMainCamera().getXRot();
      } else {
         pitch = MeteorClient.mc.player != null ? MeteorClient.mc.player.getXRot() : 0.0F;
      }

      pitch %= 360.0F;
      if (pitch < 0.0F) {
         pitch += 360.0F;
      }

      if (pitch > 180.0F) {
         pitch -= 360.0F;
      }

      return Value.number((double)pitch);
   }

   private static Value direction(boolean camera) {
      float yaw;
      if (camera) {
         yaw = MeteorClient.mc.gameRenderer.getMainCamera().getYRot();
      } else {
         yaw = MeteorClient.mc.player != null ? MeteorClient.mc.player.getYRot() : 0.0F;
      }

      return wrap(HorizontalDirection.get(yaw));
   }

   private static Value biome() {
      if (MeteorClient.mc.player != null && MeteorClient.mc.level != null) {
         BP.set(MeteorClient.mc.player.getX(), MeteorClient.mc.player.getY(), MeteorClient.mc.player.getZ());
         ResourceLocation id = MeteorClient.mc
            .level
            .registryAccess()
            .registryOrThrow(Registries.BIOME)
            .getKey((Biome)MeteorClient.mc.level.getBiome(BP).value());
         return id == null
            ? Value.string("Unknown")
            : Value.string(Arrays.stream(id.getPath().split("_")).<CharSequence>map(StringUtils::capitalize).collect(Collectors.joining(" ")));
      } else {
         return Value.string("");
      }
   }

   private static Value handOrOffhand() {
      if (MeteorClient.mc.player == null) {
         return Value.null_();
      } else {
         ItemStack itemStack = MeteorClient.mc.player.getMainHandItem();
         if (itemStack.isEmpty()) {
            itemStack = MeteorClient.mc.player.getOffhandItem();
         }

         return itemStack != null ? wrap(itemStack) : Value.null_();
      }
   }

   private static Value ping() {
      if (MeteorClient.mc.getConnection() != null && MeteorClient.mc.player != null) {
         PlayerInfo playerListEntry = MeteorClient.mc.getConnection().getPlayerInfo(MeteorClient.mc.player.getUUID());
         return Value.number(playerListEntry != null ? (double)playerListEntry.getLatency() : 0.0);
      } else {
         return Value.number(0.0);
      }
   }

   private static Value baritoneDistanceToGoal() {
      Goal goal = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().getGoal();
      return Value.number(goal != null && MeteorClient.mc.player != null ? goal.heuristic(MeteorClient.mc.player.blockPosition()) : 0.0);
   }

   private static Value posString(boolean opposite, boolean camera) {
      Vec3 pos;
      if (camera) {
         pos = MeteorClient.mc.gameRenderer.getMainCamera().getPosition();
      } else {
         pos = MeteorClient.mc.player != null ? MeteorClient.mc.player.position() : Vec3.ZERO;
      }

      double x = pos.x;
      double z = pos.z;
      if (opposite) {
         Dimension dimension = PlayerUtils.getDimension();
         if (dimension == Dimension.Overworld) {
            x /= 8.0;
            z /= 8.0;
         } else if (dimension == Dimension.Nether) {
            x *= 8.0;
            z *= 8.0;
         }
      }

      return posString(x, pos.y, z);
   }

   private static Value posString(double x, double y, double z) {
      return Value.string(String.format("X: %.0f Y: %.0f Z: %.0f", x, y, z));
   }

   private static Value crosshairType() {
      if (MeteorClient.mc.hitResult == null) {
         return Value.string("miss");
      } else {
         return Value.string(switch (MeteorClient.mc.hitResult.getType()) {
            case MISS -> "miss";
            case BLOCK -> "block";
            case ENTITY -> "entity";
            default -> throw new MatchException(null, null);
         });
      }
   }

   private static Value crosshairValue() {
      if (MeteorClient.mc.level != null && MeteorClient.mc.hitResult != null) {
         if (MeteorClient.mc.hitResult.getType() == Type.MISS) {
            return Value.string("");
         } else {
            return MeteorClient.mc.hitResult instanceof BlockHitResult hit
               ? wrap(hit.getBlockPos(), MeteorClient.mc.level.getBlockState(hit.getBlockPos()))
               : wrap(((EntityHitResult)MeteorClient.mc.hitResult).getEntity());
         }
      } else {
         return Value.null_();
      }
   }

   public static ResourceLocation popIdentifier(Starscript ss, String errorMessage) {
      try {
         return ResourceLocation.parse(ss.popString(errorMessage));
      } catch (ResourceLocationException var3) {
         ss.error(var3.getMessage());
         return null;
      }
   }

   public static Value wrap(ItemStack itemStack) {
      String name = itemStack.isEmpty() ? "" : Names.get(itemStack.getItem());
      int durability = 0;
      if (!itemStack.isEmpty() && itemStack.isDamageableItem()) {
         durability = itemStack.getMaxDamage() - itemStack.getDamageValue();
      }

      return Value.map(
         new ValueMap()
            .set("_toString", Value.string(itemStack.getCount() <= 1 ? name : String.format("%s %dx", name, itemStack.getCount())))
            .set("name", Value.string(name))
            .set("id", Value.string(BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString()))
            .set("count", Value.number((double)itemStack.getCount()))
            .set("durability", Value.number((double)durability))
            .set("max_durability", Value.number((double)itemStack.getMaxDamage()))
      );
   }

   public static Value wrap(BlockPos blockPos, BlockState blockState) {
      return Value.map(
         new ValueMap()
            .set("_toString", Value.string(Names.get(blockState.getBlock())))
            .set("id", Value.string(BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString()))
            .set(
               "pos",
               Value.map(
                  new ValueMap()
                     .set("_toString", posString((double)blockPos.getX(), (double)blockPos.getY(), (double)blockPos.getZ()))
                     .set("x", Value.number((double)blockPos.getX()))
                     .set("y", Value.number((double)blockPos.getY()))
                     .set("z", Value.number((double)blockPos.getZ()))
               )
            )
      );
   }

   public static Value wrap(Entity entity) {
      return Value.map(
         new ValueMap()
            .set("_toString", Value.string(entity.getName().getString()))
            .set("id", Value.string(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()))
            .set("health", Value.number(entity instanceof LivingEntity ex ? (double)ex.getHealth() : 0.0))
            .set("absorption", Value.number(entity instanceof LivingEntity e ? (double)e.getAbsorptionAmount() : 0.0))
            .set(
               "pos",
               Value.map(
                  new ValueMap()
                     .set("_toString", posString(entity.getX(), entity.getY(), entity.getZ()))
                     .set("x", Value.number(entity.getX()))
                     .set("y", Value.number(entity.getY()))
                     .set("z", Value.number(entity.getZ()))
               )
            )
      );
   }

   public static Value wrap(HorizontalDirection dir) {
      return Value.map(
         new ValueMap().set("_toString", Value.string(dir.name + " " + dir.axis)).set("name", Value.string(dir.name)).set("axis", Value.string(dir.axis))
      );
   }

   public static Value wrap(MobEffectInstance effectInstance) {
      return Value.map(new ValueMap().set("duration", (double)effectInstance.getDuration()).set("level", (double)(effectInstance.getAmplifier() + 1)));
   }
}
