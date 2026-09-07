package meteordevelopment.meteorclient.utils.world;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.pathing.goals.GoalYLevel;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.pathing.GoalDynamicSurface;
import meteordevelopment.meteorclient.utils.misc.FastMath;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public class SurfaceEscapeEngine {
   private static boolean active = false;
   private static BlockPos startPos = null;
   private static int startPickaxeDamage = 0;
   private static ItemStack trackedPickaxe = ItemStack.EMPTY;
   private static Integer currentTargetX = null;
   private static Integer currentTargetZ = null;

   // Backup of original Baritone settings
   private static double prevBreakPenalty = 2.0;
   private static boolean prevAllowBreak = true;
   private static boolean prevAllowPlace = true;
   private static boolean prevAvoidFallingBlocks = false;
   private static boolean prevAllowWaterBucketFall = true;
   private static boolean prevAllowParkour = false;
   private static boolean prevAllowParkourAscend = false;
   private static boolean prevAllowParkourPlace = false;
   private static boolean prevAllowDiagonalAscend = false;
   private static boolean prevSprintAscends = true;
   private static double prevMaxCostIncrease = 10.0;
   private static long prevPrimaryTimeoutMS = 500L;
   private static long prevFailureTimeoutMS = 2000L;
   private static boolean prevAllowInventory = false;
   private static boolean prevAutoTool = false;
   private static List<Item> prevThrowawayItems = null;

   public static boolean isEscaping() {
      return active;
   }

   public static boolean isTargetedNavigation() {
      return active && currentTargetX != null && currentTargetZ != null;
   }

   public static Integer getTargetX() {
      return currentTargetX;
   }

   public static Integer getTargetZ() {
      return currentTargetZ;
   }

   public static BlockPos getStartPos() {
      return startPos;
   }

   public static boolean isPillarableBlock(Block b) {
      return b == Blocks.COBBLESTONE || b == Blocks.COBBLED_DEEPSLATE || b == Blocks.DEEPSLATE
         || b == Blocks.DIRT || b == Blocks.STONE || b == Blocks.TUFF || b == Blocks.ANDESITE
         || b == Blocks.DIORITE || b == Blocks.GRANITE || b == Blocks.NETHERRACK || b == Blocks.BASALT
         || b == Blocks.BLACKSTONE || b == Blocks.CALCITE || b == Blocks.DRIPSTONE_BLOCK
         || b == Blocks.SANDSTONE || b == Blocks.RED_SANDSTONE || b == Blocks.MUD || b == Blocks.PACKED_MUD;
   }

   public static int countThrowawayBlocksInInventory() {
      if (MeteorClient.mc.player == null) return 0;
      int count = 0;
      for (int i = 0; i < 36; i++) {
         ItemStack stack = MeteorClient.mc.player.getInventory().getItem(i);
         if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
            if (isPillarableBlock(blockItem.getBlock())) {
               count += stack.getCount();
            }
         }
      }
      return count;
   }

   public static boolean isPassableVertical(BlockState state) {
      if (state.isAir()) return true;
      if (!state.blocksMotion()) return true;
      Block b = state.getBlock();
      return b instanceof TorchBlock || b instanceof LadderBlock || b instanceof VineBlock
         || b == Blocks.SCAFFOLDING || b == Blocks.SHORT_GRASS || b == Blocks.TALL_GRASS;
   }

   public static int detectVerticalAirShaft(BlockPos playerPos) {
      if (MeteorClient.mc.level == null) return 0;
      ClientLevel level = MeteorClient.mc.level;
      int airBlocks = 0;
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(playerPos.getX(), playerPos.getY() + 1, playerPos.getZ());
      while (airBlocks < 320 && isPassableVertical(level.getBlockState(m))) {
         airBlocks++;
         m.move(Direction.UP);
      }
      return airBlocks;
   }

   public static boolean isAlreadyOnSurface(int minSurfaceY) {
      if (MeteorClient.mc.player == null || MeteorClient.mc.level == null) return false;
      ClientLevel level = MeteorClient.mc.level;
      BlockPos playerPos = MeteorClient.mc.player.blockPosition();

      if (currentTargetX != null && currentTargetZ != null) {
         if (playerPos.getX() == currentTargetX && playerPos.getZ() == currentTargetZ) {
            int surfaceAtXZ = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, currentTargetX, currentTargetZ);
            return playerPos.getY() >= surfaceAtXZ - 1;
         }
         return false;
      }

      if (playerPos.getY() < minSurfaceY) return false;

      int maxSurrounding = Integer.MIN_VALUE;
      for (int dx = -2; dx <= 2; dx++) {
         for (int dz = -2; dz <= 2; dz++) {
            if (dx == 0 && dz == 0) continue;
            int h = level.getHeight(Heightmap.Types.WORLD_SURFACE, playerPos.getX() + dx, playerPos.getZ() + dz);
            if (h > maxSurrounding) maxSurrounding = h;
         }
      }

      if (playerPos.getY() < maxSurrounding - 1) {
         return false;
      }

      BlockPos torsoPos = playerPos.above();
      int solidSides = 0;
      if (level.getBlockState(torsoPos.north()).isSolid()) solidSides++;
      if (level.getBlockState(torsoPos.south()).isSolid()) solidSides++;
      if (level.getBlockState(torsoPos.east()).isSolid()) solidSides++;
      if (level.getBlockState(torsoPos.west()).isSolid()) solidSides++;

      if (solidSides >= 3) {
         return false;
      }

      return level.canSeeSky(playerPos) || playerPos.getY() >= level.getHeight(Heightmap.Types.WORLD_SURFACE, playerPos.getX(), playerPos.getZ());
   }

   public static int getDistanceToSurface(int minSurfaceY) {
      if (MeteorClient.mc.player == null || MeteorClient.mc.level == null) return 0;
      BlockPos p = MeteorClient.mc.player.blockPosition();

      if (currentTargetX != null && currentTargetZ != null) {
         double dx = p.getX() - currentTargetX;
         double dz = p.getZ() - currentTargetZ;
         int surfaceAtTarget = MeteorClient.mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, currentTargetX, currentTargetZ);
         double dy = Math.max(0, surfaceAtTarget - p.getY());
         return (int)Math.sqrt(dx * dx + dz * dz + dy * dy);
      }

      int surfaceAtXZ = MeteorClient.mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, p.getX(), p.getZ());
      int targetY = Math.max(minSurfaceY, surfaceAtXZ);
      return Math.max(0, targetY - p.getY());
   }

   private static void configureBaritoneSettings(double breakPenalty, boolean allowPlace) {
      Settings s = BaritoneAPI.getSettings();

      prevBreakPenalty = s.blockBreakAdditionalPenalty.value;
      prevAllowBreak = s.allowBreak.value;
      prevAllowPlace = s.allowPlace.value;
      prevAvoidFallingBlocks = s.avoidUpdatingFallingBlocks.value;
      prevAllowWaterBucketFall = s.allowWaterBucketFall.value;
      prevAllowParkour = s.allowParkour.value;
      prevAllowParkourAscend = s.allowParkourAscend.value;
      prevAllowParkourPlace = s.allowParkourPlace.value;
      prevAllowDiagonalAscend = s.allowDiagonalAscend.value;
      prevSprintAscends = s.sprintAscends.value;
      prevMaxCostIncrease = s.maxCostIncrease.value;
      prevPrimaryTimeoutMS = s.primaryTimeoutMS.value;
      prevFailureTimeoutMS = s.failureTimeoutMS.value;
      prevAllowInventory = s.allowInventory.value;
      prevAutoTool = s.autoTool.value;
      prevThrowawayItems = new ArrayList<>(s.acceptableThrowawayItems.value);

      s.blockBreakAdditionalPenalty.value = Math.max(10.0, breakPenalty);
      s.allowBreak.value = true;
      s.allowPlace.value = allowPlace;
      s.avoidUpdatingFallingBlocks.value = true;
      s.allowWaterBucketFall.value = true;
      s.allowParkour.value = true;
      s.allowParkourAscend.value = true;
      s.allowParkourPlace.value = true;
      s.allowDiagonalAscend.value = true;
      s.sprintAscends.value = true;
      s.maxCostIncrease.value = 4000.0;
      s.primaryTimeoutMS.value = 5000L;
      s.failureTimeoutMS.value = 15000L;
      s.allowInventory.value = true;
      s.autoTool.value = true;

      List<Item> throwaways = new ArrayList<>(s.acceptableThrowawayItems.value);
      addIfMissing(throwaways, Blocks.COBBLED_DEEPSLATE);
      addIfMissing(throwaways, Blocks.DEEPSLATE);
      addIfMissing(throwaways, Blocks.TUFF);
      addIfMissing(throwaways, Blocks.ANDESITE);
      addIfMissing(throwaways, Blocks.DIORITE);
      addIfMissing(throwaways, Blocks.GRANITE);
      addIfMissing(throwaways, Blocks.STONE);
      addIfMissing(throwaways, Blocks.COBBLESTONE);
      addIfMissing(throwaways, Blocks.DIRT);
      addIfMissing(throwaways, Blocks.NETHERRACK);
      addIfMissing(throwaways, Blocks.BASALT);
      addIfMissing(throwaways, Blocks.BLACKSTONE);
      addIfMissing(throwaways, Blocks.CALCITE);
      addIfMissing(throwaways, Blocks.DRIPSTONE_BLOCK);
      addIfMissing(throwaways, Blocks.SANDSTONE);
      addIfMissing(throwaways, Blocks.RED_SANDSTONE);
      addIfMissing(throwaways, Blocks.MUD);
      addIfMissing(throwaways, Blocks.PACKED_MUD);

      if (MeteorClient.mc.player != null) {
         for (int i = 0; i < 36; i++) {
            ItemStack stack = MeteorClient.mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
               if (isPillarableBlock(blockItem.getBlock())) {
                  addIfMissing(throwaways, blockItem.getBlock());
               }
            }
         }
      }
      s.acceptableThrowawayItems.value = throwaways;
   }

   public static boolean startEscape(double breakPenalty, boolean allowPlace, int minSurfaceY) {
      if (MeteorClient.mc.player == null || MeteorClient.mc.level == null) return false;
      if (BaritoneAPI.getProvider() == null) return false;

      IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
      configureBaritoneSettings(breakPenalty, allowPlace);

      startPos = MeteorClient.mc.player.blockPosition();
      ItemStack mainHand = MeteorClient.mc.player.getMainHandItem();
      if (mainHand.getItem() instanceof PickaxeItem) {
         trackedPickaxe = mainHand;
         startPickaxeDamage = mainHand.getDamageValue();
      } else {
         trackedPickaxe = ItemStack.EMPTY;
         startPickaxeDamage = 0;
      }

      active = true;
      currentTargetX = null;
      currentTargetZ = null;

      baritone.getPathingBehavior().cancelEverything();

      int airAbove = detectVerticalAirShaft(startPos);
      int blocksInInv = countThrowawayBlocksInInventory();

      if (airAbove >= 4 && blocksInInv >= 4) {
         int surfaceAtXZ = MeteorClient.mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, startPos.getX(), startPos.getZ());
         int targetY = Math.max(minSurfaceY, surfaceAtXZ + 1);
         Goal goal = new GoalComposite(new GoalXZ(startPos.getX(), startPos.getZ()), new GoalYLevel(targetY));
         baritone.getCustomGoalProcess().setGoalAndPath(goal);
      } else {
         baritone.getCustomGoalProcess().setGoalAndPath(new GoalDynamicSurface(minSurfaceY));
      }

      return true;
   }

   public static boolean startNavigation(int targetX, int targetZ, double breakPenalty, boolean allowPlace, int minSurfaceY) {
      if (MeteorClient.mc.player == null || MeteorClient.mc.level == null) return false;
      if (BaritoneAPI.getProvider() == null) return false;

      IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
      configureBaritoneSettings(breakPenalty, allowPlace);

      startPos = MeteorClient.mc.player.blockPosition();
      ItemStack mainHand = MeteorClient.mc.player.getMainHandItem();
      if (mainHand.getItem() instanceof PickaxeItem) {
         trackedPickaxe = mainHand;
         startPickaxeDamage = mainHand.getDamageValue();
      } else {
         trackedPickaxe = ItemStack.EMPTY;
         startPickaxeDamage = 0;
      }

      active = true;
      currentTargetX = targetX;
      currentTargetZ = targetZ;

      baritone.getPathingBehavior().cancelEverything();
      baritone.getCustomGoalProcess().setGoalAndPath(new GoalDynamicSurface(targetX, targetZ, minSurfaceY));
      return true;
   }

   public static void applyEscalationTier(boolean excavationMode, int minSurfaceY) {
      if (!active || BaritoneAPI.getProvider() == null) return;
      IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
      Settings s = BaritoneAPI.getSettings();

      s.allowInventory.value = true;
      s.autoTool.value = true;
      s.allowBreak.value = true;
      s.allowPlace.value = true;
      s.allowDiagonalAscend.value = true;
      s.sprintAscends.value = true;
      s.maxCostIncrease.value = 4000.0;
      s.failureTimeoutMS.value = 15000L;
      s.primaryTimeoutMS.value = 5000L;

      if (excavationMode) {
         s.blockBreakAdditionalPenalty.value = 2.0;
      } else {
         s.blockBreakAdditionalPenalty.value = 60.0;
      }

      baritone.getPathingBehavior().cancelEverything();
      if (currentTargetX != null && currentTargetZ != null) {
         baritone.getCustomGoalProcess().setGoalAndPath(new GoalDynamicSurface(currentTargetX, currentTargetZ, minSurfaceY));
      } else {
         baritone.getCustomGoalProcess().setGoalAndPath(new GoalDynamicSurface(minSurfaceY));
      }
   }

   private static void addIfMissing(List<Item> list, Block block) {
      Item item = block.asItem();
      if (item != null && item != net.minecraft.world.item.Items.AIR && !list.contains(item)) {
         list.add(item);
      }
   }

   public static void stopEscape() {
      if (!active) return;
      active = false;
      currentTargetX = null;
      currentTargetZ = null;

      if (BaritoneAPI.getProvider() != null) {
         IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
         baritone.getPathingBehavior().cancelEverything();

         Settings s = BaritoneAPI.getSettings();
         s.blockBreakAdditionalPenalty.value = prevBreakPenalty;
         s.allowBreak.value = prevAllowBreak;
         s.allowPlace.value = prevAllowPlace;
         s.avoidUpdatingFallingBlocks.value = prevAvoidFallingBlocks;
         s.allowWaterBucketFall.value = prevAllowWaterBucketFall;
         s.allowParkour.value = prevAllowParkour;
         s.allowParkourAscend.value = prevAllowParkourAscend;
         s.allowParkourPlace.value = prevAllowParkourPlace;
         s.allowDiagonalAscend.value = prevAllowDiagonalAscend;
         s.sprintAscends.value = prevSprintAscends;
         s.maxCostIncrease.value = prevMaxCostIncrease;
         s.primaryTimeoutMS.value = prevPrimaryTimeoutMS;
         s.failureTimeoutMS.value = prevFailureTimeoutMS;
         s.allowInventory.value = prevAllowInventory;
         s.autoTool.value = prevAutoTool;
         if (prevThrowawayItems != null) {
            s.acceptableThrowawayItems.value = prevThrowawayItems;
         }
      }
   }

   public static int getDurabilityConsumed() {
      if (trackedPickaxe.isEmpty() || !(trackedPickaxe.getItem() instanceof PickaxeItem)) return 0;
      return Math.max(0, trackedPickaxe.getDamageValue() - startPickaxeDamage);
   }

   public static class NaturalOpening {
      public final BlockPos pos;
      public final double horizontalDistance;
      public final int elevation;

      public NaturalOpening(BlockPos pos, double horizontalDistance, int elevation) {
         this.pos = pos;
         this.horizontalDistance = horizontalDistance;
         this.elevation = elevation;
      }
   }

   public static NaturalOpening findNearestNaturalOpening(int chunkRadius) {
      if (MeteorClient.mc.player == null || MeteorClient.mc.level == null) return null;
      ClientLevel level = MeteorClient.mc.level;
      BlockPos playerPos = MeteorClient.mc.player.blockPosition();

      int pChunkX = playerPos.getX() >> 4;
      int pChunkZ = playerPos.getZ() >> 4;

      BlockPos bestPos = null;
      double minScore = Double.MAX_VALUE;
      int bestElevation = 0;

      for (int cx = pChunkX - chunkRadius; cx <= pChunkX + chunkRadius; cx++) {
         for (int cz = pChunkZ - chunkRadius; cz <= pChunkZ + chunkRadius; cz++) {
            if (!level.getChunkSource().hasChunk(cx, cz)) continue;

            int startX = cx << 4;
            int startZ = cz << 4;

            for (int dx = 0; dx < 16; dx += 4) {
               for (int dz = 0; dz < 16; dz += 4) {
                  int x = startX + dx;
                  int z = startZ + dz;
                  int topY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                  BlockPos checkPos = new BlockPos(x, topY, z);

                  if (level.canSeeSky(checkPos)) {
                     double hDist = FastMath.hypot((double)(x - playerPos.getX()), (double)(z - playerPos.getZ()));
                     double score = hDist + (double)(topY - playerPos.getY()) * 1.5;
                     if (score < minScore) {
                        minScore = score;
                        bestPos = checkPos;
                        bestElevation = topY;
                     }
                  }
               }
            }
         }
      }

      if (bestPos != null) {
         double hDist = FastMath.hypot((double)(bestPos.getX() - playerPos.getX()), (double)(bestPos.getZ() - playerPos.getZ()));
         return new NaturalOpening(bestPos, hDist, bestElevation);
      }
      return null;
   }
}
