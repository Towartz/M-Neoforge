package meteordevelopment.meteorclient.commands.commands;

import baritone.api.BaritoneAPI;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.item.component.MapDecorations.Entry;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class LocateCommand extends Command {
   private Vec3 firstStart;
   private Vec3 firstEnd;
   private Vec3 secondStart;
   private Vec3 secondEnd;
   private final List<Block> netherFortressBlocks = List.of(Blocks.NETHER_BRICKS, Blocks.NETHER_BRICK_FENCE, Blocks.NETHER_WART);
   private final List<Block> monumentBlocks = List.of(Blocks.PRISMARINE_BRICKS, Blocks.SEA_LANTERN, Blocks.DARK_PRISMARINE);
   private final List<Block> strongholdBlocks = List.of(Blocks.END_PORTAL_FRAME);
   private final List<Block> endCityBlocks = List.of(
      Blocks.PURPUR_BLOCK, Blocks.PURPUR_PILLAR, Blocks.PURPUR_SLAB, Blocks.PURPUR_STAIRS, Blocks.END_STONE_BRICKS, Blocks.END_ROD
   );

   public LocateCommand() {
      super("locate", "Locates structures", "loc");
   }

   @Override
   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      builder.then(
         literal("buried_treasure")
            .executes(
               s -> {
                  ItemStack stack = mc.player.getInventory().getSelected();
                  if (stack.getItem() == Items.FILLED_MAP
                     && stack.get(DataComponents.ITEM_NAME) != null
                     && ((Component)stack.get(DataComponents.ITEM_NAME)).getString().equals(Component.translatable("filled_map.buried_treasure").getString())) {
                     MapDecorations mapDecorationsComponent = (MapDecorations)stack.get(DataComponents.MAP_DECORATIONS);
                     if (mapDecorationsComponent == null) {
                        this.error("Couldn't locate the map icons!", new Object[0]);
                        return 1;
                     } else {
                        for (Entry decoration : mapDecorationsComponent.decorations().values()) {
                           if (((MapDecorationType)decoration.type().value()).assetId().toString().equals("minecraft:red_x")) {
                              Vec3 coords = new Vec3(decoration.x(), 62.0, decoration.z());
                              MutableComponent text = Component.literal("Buried Treasure located at ");
                              text.append(ChatUtils.formatCoords(coords));
                              text.append(".");
                              this.info(text);
                              return 1;
                           }
                        }

                        this.error("Couldn't locate the buried treasure!", new Object[0]);
                        return 1;
                     }
                  } else {
                     this.error("You need to hold a (highlight)buried treasure map(default)!", new Object[0]);
                     return 1;
                  }
               }
            )
      );
      builder.then(
         literal("mansion")
            .executes(
               s -> {
                  ItemStack stack = mc.player.getInventory().getSelected();
                  if (stack.getItem() == Items.FILLED_MAP
                     && stack.get(DataComponents.ITEM_NAME) != null
                     && ((Component)stack.get(DataComponents.ITEM_NAME)).getString().equals(Component.translatable("filled_map.mansion").getString())) {
                     MapDecorations mapDecorationsComponent = (MapDecorations)stack.get(DataComponents.MAP_DECORATIONS);
                     if (mapDecorationsComponent == null) {
                        this.error("Couldn't locate the map icons!", new Object[0]);
                        return 1;
                     } else {
                        for (Entry decoration : mapDecorationsComponent.decorations().values()) {
                           if (((MapDecorationType)decoration.type().value()).assetId().toString().equals("minecraft:woodland_mansion")) {
                              Vec3 coords = new Vec3(decoration.x(), 62.0, decoration.z());
                              MutableComponent text = Component.literal("Mansion located at ");
                              text.append(ChatUtils.formatCoords(coords));
                              text.append(".");
                              this.info(text);
                              return 1;
                           }
                        }

                        this.error("Couldn't locate the mansion!", new Object[0]);
                        return 1;
                     }
                  } else {
                     this.error("You need to hold a (highlight)woodland explorer map(default)!", new Object[0]);
                     return 1;
                  }
               }
            )
      );
      builder.then(
         literal("monument")
            .executes(
               s -> {
                  ItemStack stack = mc.player.getInventory().getSelected();
                  if (stack.getItem() == Items.FILLED_MAP
                     && stack.get(DataComponents.ITEM_NAME) != null
                     && ((Component)stack.get(DataComponents.ITEM_NAME)).getString().equals(Component.translatable("filled_map.monument").getString())) {
                     MapDecorations mapDecorationsComponent = (MapDecorations)stack.get(DataComponents.MAP_DECORATIONS);
                     if (mapDecorationsComponent == null) {
                        this.error("Couldn't locate the map icons!", new Object[0]);
                        return 1;
                     } else {
                        for (Entry decoration : mapDecorationsComponent.decorations().values()) {
                           if (((MapDecorationType)decoration.type().value()).assetId().toString().equals("minecraft:ocean_monument")) {
                              Vec3 coords = new Vec3(decoration.x(), 62.0, decoration.z());
                              MutableComponent text = Component.literal("Monument located at ");
                              text.append(ChatUtils.formatCoords(coords));
                              text.append(".");
                              this.info(text);
                              return 1;
                           }
                        }

                        this.error("Couldn't locate the monument!", new Object[0]);
                        return 1;
                     }
                  } else if (BaritoneUtils.IS_AVAILABLE) {
                     Vec3 coords = this.findByBlockList(this.monumentBlocks);
                     if (coords == null) {
                        this.error("No monument found. Try using an (highlight)ocean explorer map(default) for more success.", new Object[0]);
                        return 1;
                     } else {
                        MutableComponent text = Component.literal("Monument located at ");
                        text.append(ChatUtils.formatCoords(coords));
                        text.append(".");
                        this.info(text);
                        return 1;
                     }
                  } else {
                     this.error("Locating this structure without an (highlight)ocean explorer map(default) requires Baritone.", new Object[0]);
                     return 1;
                  }
               }
            )
      );
      builder.then(literal("stronghold").executes(s -> {
         boolean foundEye = InvUtils.testInHotbar(Items.ENDER_EYE);
         if (foundEye) {
            if (BaritoneUtils.IS_AVAILABLE) {
               PathManagers.get().follow(EyeOfEnder.class::isInstance);
            }

            this.firstStart = null;
            this.firstEnd = null;
            this.secondStart = null;
            this.secondEnd = null;
            MeteorClient.EVENT_BUS.subscribe(this);
            this.info("Please throw the first Eye of Ender", new Object[0]);
         } else if (BaritoneUtils.IS_AVAILABLE) {
            Vec3 coords = this.findByBlockList(this.strongholdBlocks);
            if (coords == null) {
               this.error("No stronghold found nearby. You can use (highlight)Ender Eyes(default) for more success.", new Object[0]);
               return 1;
            }

            MutableComponent text = Component.literal("Stronghold located at ");
            text.append(ChatUtils.formatCoords(coords));
            text.append(".");
            this.info(text);
         } else {
            this.error("No Eyes of Ender found in hotbar.", new Object[0]);
         }

         return 1;
      }));
      builder.then(literal("nether_fortress").executes(s -> {
         if (mc.level.dimension() != Level.NETHER) {
            this.error("You need to be in the nether to locate a nether fortress.", new Object[0]);
            return 1;
         } else if (!BaritoneUtils.IS_AVAILABLE) {
            this.error("Locating this structure requires Baritone.", new Object[0]);
            return 1;
         } else {
            Vec3 coords = this.findByBlockList(this.netherFortressBlocks);
            if (coords == null) {
               this.error("No nether fortress found.", new Object[0]);
               return 1;
            } else {
               MutableComponent text = Component.literal("Fortress located at ");
               text.append(ChatUtils.formatCoords(coords));
               text.append(".");
               this.info(text);
               return 1;
            }
         }
      }));
      builder.then(literal("end_city").executes(s -> {
         if (mc.level.dimension() != Level.END) {
            this.error("You need to be in the end to locate an end city.", new Object[0]);
            return 1;
         } else if (!BaritoneUtils.IS_AVAILABLE) {
            this.error("Locating this structure requires Baritone.", new Object[0]);
            return 1;
         } else {
            Vec3 coords = this.findByBlockList(this.endCityBlocks);
            if (coords == null) {
               this.error("No end city found.", new Object[0]);
               return 1;
            } else {
               MutableComponent text = Component.literal("End city located at ");
               text.append(ChatUtils.formatCoords(coords));
               text.append(".");
               this.info(text);
               return 1;
            }
         }
      }));
      builder.then(literal("lodestone").executes(s -> {
         ItemStack stack = mc.player.getInventory().getSelected();
         if (stack.getItem() != Items.COMPASS) {
            this.error("You need to hold a (highlight)lodestone(default) compass!", new Object[0]);
            return 1;
         } else {
            DataComponentMap components = stack.getComponents();
            if (components == null) {
               this.error("Couldn't get the components data. Are you holding a (highlight)lodestone(default) compass?", new Object[0]);
               return 1;
            } else {
               LodestoneTracker lodestoneTrackerComponent = (LodestoneTracker)components.get(DataComponents.LODESTONE_TRACKER);
               if (lodestoneTrackerComponent == null) {
                  this.error("Couldn't get the components data. Are you holding a (highlight)lodestone(default) compass?", new Object[0]);
                  return 1;
               } else if (lodestoneTrackerComponent.target().isEmpty()) {
                  this.error("Couldn't get the lodestone's target!", new Object[0]);
                  return 1;
               } else {
                  Vec3 coords = Vec3.atLowerCornerOf(((GlobalPos)lodestoneTrackerComponent.target().get()).pos());
                  MutableComponent text = Component.literal("Lodestone located at ");
                  text.append(ChatUtils.formatCoords(coords));
                  text.append(".");
                  this.info(text);
                  return 1;
               }
            }
         }
      }));
      builder.then(literal("cancel").executes(s -> {
         this.cancel();
         return 1;
      }));
   }

   private void cancel() {
      this.warning("Locate canceled", new Object[0]);
      MeteorClient.EVENT_BUS.unsubscribe(this);
   }

   @Nullable
   private Vec3 findByBlockList(List<Block> blockList) {
      List<BlockPos> posList = BaritoneAPI.getProvider()
         .getWorldScanner()
         .scanChunkRadius(BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext(), blockList, 64, 10, 32);
      if (posList.isEmpty()) {
         return null;
      } else {
         if (posList.size() < 3) {
            this.warning("Only %d block(s) found. This search might be a false positive.", new Object[]{posList.size()});
         }

         return new Vec3((double)posList.getFirst().getX(), (double)posList.getFirst().getY(), (double)posList.getFirst().getZ());
      }
   }

   @EventHandler
   private void onReadPacket(PacketEvent.Receive event) {
      if (event.packet instanceof ClientboundAddEntityPacket packet && packet.getType() == EntityType.EYE_OF_ENDER) {
         this.firstPosition(packet.getX(), packet.getY(), packet.getZ());
      }

      if (event.packet instanceof ClientboundSoundPacket packet && packet.getSound().value() == SoundEvents.ENDER_EYE_DEATH) {
         this.lastPosition(packet.getX(), packet.getY(), packet.getZ());
      }
   }

   private void firstPosition(double x, double y, double z) {
      Vec3 pos = new Vec3(x, y, z);
      if (this.firstStart == null) {
         this.firstStart = pos;
      } else {
         this.secondStart = pos;
      }
   }

   private void lastPosition(double x, double y, double z) {
      this.info("%s Eye of Ender's trajectory saved.", new Object[]{this.firstEnd == null ? "First" : "Second"});
      Vec3 pos = new Vec3(x, y, z);
      if (this.firstEnd == null) {
         this.firstEnd = pos;
         this.info("Please throw the second Eye Of Ender from a different location.", new Object[0]);
      } else {
         this.secondEnd = pos;
         this.findStronghold();
      }
   }

   private void findStronghold() {
      PathManagers.get().stop();
      if (this.firstStart != null && this.firstEnd != null && this.secondStart != null && this.secondEnd != null) {
         double[] start = new double[]{this.secondStart.x, this.secondStart.z, this.secondEnd.x, this.secondEnd.z};
         double[] end = new double[]{this.firstStart.x, this.firstStart.z, this.firstEnd.x, this.firstEnd.z};
         double[] intersection = this.calcIntersection(start, end);
         if (!Double.isNaN(intersection[0]) && !Double.isNaN(intersection[1]) && !Double.isInfinite(intersection[0]) && !Double.isInfinite(intersection[1])) {
            MeteorClient.EVENT_BUS.unsubscribe(this);
            Vec3 coords = new Vec3(intersection[0], 0.0, intersection[1]);
            MutableComponent text = Component.literal("Stronghold roughly located at ");
            text.append(ChatUtils.formatCoords(coords));
            text.append(".");
            this.info(text);
         } else {
            this.error("Unable to calculate intersection.", new Object[0]);
            this.cancel();
         }
      } else {
         this.error("Missing position data", new Object[0]);
         this.cancel();
      }
   }

   private double[] calcIntersection(double[] line, double[] line2) {
      double a1 = line[3] - line[1];
      double b1 = line[0] - line[2];
      double c1 = a1 * line[0] + b1 * line[1];
      double a2 = line2[3] - line2[1];
      double b2 = line2[0] - line2[2];
      double c2 = a2 * line2[0] + b2 * line2[1];
      double delta = a1 * b2 - a2 * b1;
      return new double[]{(b2 * c1 - b1 * c2) / delta, (a1 * c2 - a2 * c1) / delta};
   }
}
