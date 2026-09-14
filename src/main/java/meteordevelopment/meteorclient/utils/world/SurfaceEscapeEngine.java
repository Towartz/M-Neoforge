package meteordevelopment.meteorclient.utils.world;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

public class SurfaceEscapeEngine {
   private static boolean active = false;
   private static BlockPos startPos = null;
   private static int startPickaxeDamage = 0;
   private static ItemStack trackedPickaxe = ItemStack.EMPTY;
   private static Integer currentTargetX = null;
   private static Integer currentTargetY = null;
   private static Integer currentTargetZ = null;
   private static int currentStageTargetY = 0;
   private static int currentAnchorX = 0;
   private static int currentAnchorZ = 0;
   private static int totalTargetSurfaceY = 0;
   private static int activeStageStep = 100;

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

   public static Integer getTargetY() {
      return currentTargetY;
   }

   public static Integer getTargetZ() {
      return currentTargetZ;
   }

   public static int getCurrentStageTargetY() {
      return currentStageTargetY;
   }

   public static int getTotalTargetSurfaceY() {
      return totalTargetSurfaceY;
   }

   public static int getCurrentAnchorX() {
      return currentAnchorX;
   }

   public static int getCurrentAnchorZ() {
      return currentAnchorZ;
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

   public static class ShaftOpening {
      public final BlockPos pos;
      public final int clearance;
      public final boolean isWaterColumn;
      public final double horizontalDistance;

      public ShaftOpening(BlockPos pos, int clearance, boolean isWaterColumn, double horizontalDistance) {
         this.pos = pos;
         this.clearance = clearance;
         this.isWaterColumn = isWaterColumn;
         this.horizontalDistance = horizontalDistance;
      }
   }

   public static ShaftOpening findBestVicinityShaft(BlockPos center, int radius) {
      if (MeteorClient.mc.level == null) return null;
      ClientLevel level = MeteorClient.mc.level;

      ShaftOpening bestShaft = null;
      double bestScore = -1.0;

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dz = -radius; dz <= radius; dz++) {
            int x = center.getX() + dx;
            int z = center.getZ() + dz;
            double hDist = FastMath.hypot(dx, dz);

            int yStart = center.getY();
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, yStart, z);

            BlockState baseState = level.getBlockState(m);
            boolean isWater = baseState.is(Blocks.WATER);
            if (!isWater && !isPassableVertical(baseState)) {
               m.setY(yStart + 1);
               baseState = level.getBlockState(m);
               isWater = baseState.is(Blocks.WATER);
               if (!isWater && !isPassableVertical(baseState)) {
                  continue;
               }
               yStart++;
            }

            int clearance = 0;
            int maxBuildHeight = level.getMaxBuildHeight();

            if (isWater) {
               while (m.getY() < maxBuildHeight && level.getBlockState(m).is(Blocks.WATER)) {
                  clearance++;
                  m.move(Direction.UP);
               }
            } else {
               while (m.getY() < maxBuildHeight && isPassableVertical(level.getBlockState(m))) {
                  clearance++;
                  m.move(Direction.UP);
               }
            }

            if (clearance >= 4) {
               double score = (isWater ? 50.0 : 20.0) + clearance - (hDist * 3.0);
               if (score > bestScore) {
                  bestScore = score;
                  bestShaft = new ShaftOpening(new BlockPos(x, yStart, z), clearance, isWater, hDist);
               }
            }
         }
      }

      return bestShaft;
   }

   public static int calculateSurfaceY(int x, int z, int minSurfaceY) {
      if (MeteorClient.mc.level == null) return minSurfaceY;
      ClientLevel level = MeteorClient.mc.level;

      if (level.dimension() == Level.NETHER) {
         return Math.max(minSurfaceY, 65);
      }

      int cx = x >> 4;
      int cz = z >> 4;
      int surfY = minSurfaceY;

      if (level.getChunkSource().hasChunk(cx, cz)) {
         LevelChunk chunk = level.getChunk(cx, cz);
         if (chunk != null) {
            int h1 = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
            int h2 = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
            surfY = Math.max(minSurfaceY, Math.max(h1, h2));

            // Liquid check: if top block is water or bubble column, ascend above water surface
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, surfY, z);
            while (surfY < level.getMaxBuildHeight() && (level.getBlockState(m).is(Blocks.WATER) || level.getBlockState(m).is(Blocks.BUBBLE_COLUMN))) {
               surfY++;
               m.setY(surfY);
            }
         }
      } else {
         surfY = Math.max(minSurfaceY, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
      }

      return surfY;
   }

   public static int calculateNextStageY(int currentY, int surfaceY, int stageStep) {
      if (currentY >= surfaceY) return surfaceY;
      int step = Math.max(20, stageStep);
      return Math.min(surfaceY, currentY + step);
   }

   public static boolean isAlreadyOnSurface(int minSurfaceY) {
      if (MeteorClient.mc.player == null || MeteorClient.mc.level == null) return false;
      ClientLevel level = MeteorClient.mc.level;
      BlockPos playerPos = MeteorClient.mc.player.blockPosition();

      // Dimension awareness: Nether
      if (level.dimension() == Level.NETHER) {
         if (playerPos.getY() >= minSurfaceY && playerPos.getY() <= 100) {
            BlockPos torso = playerPos.above();
            BlockPos head = playerPos.above(2);
            return level.getBlockState(playerPos).isAir()
               && level.getBlockState(torso).isAir()
               && level.getBlockState(head).isAir()
               && level.getBlockState(playerPos.below()).isSolid();
         }
         return false;
      }

      // Targeted navigation arrival check (allowing 2 blocks tolerance)
      if (currentTargetX != null && currentTargetZ != null) {
         if (Math.abs(playerPos.getX() - currentTargetX) <= 2 && Math.abs(playerPos.getZ() - currentTargetZ) <= 2) {
            int surfaceAtXZ = calculateSurfaceY(currentTargetX, currentTargetZ, minSurfaceY);
            return playerPos.getY() >= surfaceAtXZ - 1;
         }
         return false;
      }

      if (currentTargetY != null) {
         return playerPos.getY() >= currentTargetY;
      }

      if (playerPos.getY() < minSurfaceY - 1) return false;

      int surfaceAtPlayer = calculateSurfaceY(playerPos.getX(), playerPos.getZ(), minSurfaceY);
      if (playerPos.getY() >= surfaceAtPlayer - 1) {
         return true;
      }

      // Cave mouth, ravine, or low saddle exposed to sky with head clearance
      if (level.canSeeSky(playerPos) || level.canSeeSky(playerPos.above())) {
         BlockPos torsoPos = playerPos.above();
         int solidSides = 0;
         if (level.getBlockState(torsoPos.north()).isSolid()) solidSides++;
         if (level.getBlockState(torsoPos.south()).isSolid()) solidSides++;
         if (level.getBlockState(torsoPos.east()).isSolid()) solidSides++;
         if (level.getBlockState(torsoPos.west()).isSolid()) solidSides++;

         // Only consider trapped if >= 3 sides are walled in AND significantly below local surface
         if (solidSides >= 3 && playerPos.getY() < surfaceAtPlayer - 3) {
            return false;
         }
         return true;
      }

      return false;
   }

   public static int getDistanceToSurface(int minSurfaceY) {
      if (MeteorClient.mc.player == null || MeteorClient.mc.level == null) return 0;
      BlockPos p = MeteorClient.mc.player.blockPosition();

      if (MeteorClient.mc.level.dimension() == Level.NETHER) {
         if (p.getY() < minSurfaceY) {
            return minSurfaceY - p.getY();
         } else if (p.getY() > 95) {
            return p.getY() - 65;
         }
         return 0;
      }

      if (currentTargetX != null && currentTargetZ != null) {
         double dx = p.getX() - currentTargetX;
         double dz = p.getZ() - currentTargetZ;
         int surfaceAtTarget = calculateSurfaceY(currentTargetX, currentTargetZ, minSurfaceY);
         double dy = Math.max(0, surfaceAtTarget - p.getY());
         return (int)Math.sqrt(dx * dx + dz * dz + dy * dy);
      }

      if (currentTargetY != null) {
         return Math.max(0, currentTargetY - p.getY());
      }

      int surfaceAtXZ = calculateSurfaceY(p.getX(), p.getZ(), minSurfaceY);
      return Math.max(0, surfaceAtXZ - p.getY());
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

      s.blockBreakAdditionalPenalty.value = Math.max(1.0, breakPenalty);
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
      s.primaryTimeoutMS.value = 1500L;
      s.failureTimeoutMS.value = 6000L;
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

   public static boolean startEscape(double breakPenalty, boolean allowPlace, int minSurfaceY, int stageStep, boolean prioritizeShafts) {
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
      currentTargetY = null;
      currentTargetZ = null;
      activeStageStep = stageStep;

      baritone.getPathingBehavior().cancelEverything();

      if (prioritizeShafts) {
         ShaftOpening shaft = findBestVicinityShaft(startPos, 3);
         int blocksInInv = countThrowawayBlocksInInventory();

         if (shaft != null && shaft.horizontalDistance <= 2.5 && (shaft.isWaterColumn || (allowPlace && blocksInInv >= 4))) {
            int targetY = shaft.pos.getY() + shaft.clearance;
            currentAnchorX = shaft.pos.getX();
            currentAnchorZ = shaft.pos.getZ();
            currentStageTargetY = targetY;
            totalTargetSurfaceY = targetY;
            Goal goal = new GoalBlock(shaft.pos.getX(), targetY, shaft.pos.getZ());
            baritone.getCustomGoalProcess().setGoalAndPath(goal);
            return true;
         }
      }

      currentAnchorX = startPos.getX();
      currentAnchorZ = startPos.getZ();
      totalTargetSurfaceY = calculateSurfaceY(currentAnchorX, currentAnchorZ, minSurfaceY);
      currentStageTargetY = calculateNextStageY(startPos.getY(), totalTargetSurfaceY, stageStep);

      Goal goal = new GoalBlock(currentAnchorX, currentStageTargetY, currentAnchorZ);
      baritone.getCustomGoalProcess().setGoalAndPath(goal);
      return true;
   }

   public static boolean startEscape(double breakPenalty, boolean allowPlace, int minSurfaceY, int stageStep) {
      return startEscape(breakPenalty, allowPlace, minSurfaceY, stageStep, true);
   }

   public static boolean startEscape(double breakPenalty, boolean allowPlace, int minSurfaceY, boolean useSkyLightGradient) {
      return startEscape(breakPenalty, allowPlace, minSurfaceY, 100, true);
   }

   public static boolean startEscape(double breakPenalty, boolean allowPlace, int minSurfaceY) {
      return startEscape(breakPenalty, allowPlace, minSurfaceY, 100, true);
   }

   public static boolean startNavigation(int targetX, int targetZ, double breakPenalty, boolean allowPlace, int minSurfaceY, int stageStep) {
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
      currentTargetY = null;
      currentTargetZ = targetZ;
      currentAnchorX = targetX;
      currentAnchorZ = targetZ;
      activeStageStep = stageStep;

      baritone.getPathingBehavior().cancelEverything();

      totalTargetSurfaceY = calculateSurfaceY(targetX, targetZ, minSurfaceY);
      currentStageTargetY = totalTargetSurfaceY;

      Goal goal = new GoalBlock(targetX, totalTargetSurfaceY, targetZ);
      baritone.getCustomGoalProcess().setGoalAndPath(goal);
      return true;
   }

   public static boolean startNavigation(int targetX, int targetZ, double breakPenalty, boolean allowPlace, int minSurfaceY, boolean useSkyLightGradient) {
      return startNavigation(targetX, targetZ, breakPenalty, allowPlace, minSurfaceY, 100);
   }

   public static boolean startNavigation(int targetX, int targetZ, double breakPenalty, boolean allowPlace, int minSurfaceY) {
      return startNavigation(targetX, targetZ, breakPenalty, allowPlace, minSurfaceY, 100);
   }

   public static boolean startDirectY(int targetY, double breakPenalty, boolean allowPlace, int stageStep) {
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
      currentTargetY = targetY;
      currentTargetZ = null;
      currentAnchorX = startPos.getX();
      currentAnchorZ = startPos.getZ();
      totalTargetSurfaceY = targetY;
      activeStageStep = stageStep;

      baritone.getPathingBehavior().cancelEverything();

      currentStageTargetY = calculateNextStageY(startPos.getY(), targetY, stageStep);
      Goal goal = new GoalBlock(currentAnchorX, currentStageTargetY, currentAnchorZ);
      baritone.getCustomGoalProcess().setGoalAndPath(goal);
      return true;
   }

   public static boolean updateStage(int currentX, int currentY, int currentZ, int minSurfaceY, int stageStep) {
      if (!active || BaritoneAPI.getProvider() == null) return false;

      if (currentTargetY != null) {
         totalTargetSurfaceY = currentTargetY;
      } else if (currentTargetX != null && currentTargetZ != null) {
         totalTargetSurfaceY = calculateSurfaceY(currentTargetX, currentTargetZ, minSurfaceY);
      } else {
         totalTargetSurfaceY = calculateSurfaceY(currentX, currentZ, minSurfaceY);
      }

      if (currentY >= totalTargetSurfaceY - 1) {
         return false; // Already reached surface!
      }

      currentAnchorX = (currentTargetX != null) ? currentTargetX : currentX;
      currentAnchorZ = (currentTargetZ != null) ? currentTargetZ : currentZ;
      currentStageTargetY = calculateNextStageY(currentY, totalTargetSurfaceY, stageStep);

      IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
      Goal goal = new GoalBlock(currentAnchorX, currentStageTargetY, currentAnchorZ);
      baritone.getCustomGoalProcess().setGoalAndPath(goal);
      return true;
   }

   public static void reanchorColumn(int newX, int newZ) {
      if (!active || BaritoneAPI.getProvider() == null) return;
      if (currentTargetX != null && currentTargetZ != null) return; // Do not re-anchor targeted navigation

      currentAnchorX = newX;
      currentAnchorZ = newZ;
      IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
      Goal goal = new GoalBlock(currentAnchorX, currentStageTargetY, currentAnchorZ);
      baritone.getCustomGoalProcess().setGoalAndPath(goal);
   }

   public static void applyAntiGiveUpRecovery(boolean boostExcavation, int minSurfaceY) {
      if (!active || BaritoneAPI.getProvider() == null || MeteorClient.mc.player == null) return;
      IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
      Settings s = BaritoneAPI.getSettings();

      s.allowInventory.value = true;
      s.autoTool.value = true;
      s.allowBreak.value = true;
      s.allowPlace.value = true;
      s.allowDiagonalAscend.value = true;
      s.sprintAscends.value = true;

      if (boostExcavation) {
         s.blockBreakAdditionalPenalty.value = 1.0;
      }

      BlockPos p = MeteorClient.mc.player.blockPosition();
      if (currentTargetX == null || currentTargetZ == null) {
         currentAnchorX = p.getX();
         currentAnchorZ = p.getZ();
      }

      if (currentTargetY != null) {
         totalTargetSurfaceY = currentTargetY;
      } else {
         totalTargetSurfaceY = calculateSurfaceY(currentAnchorX, currentAnchorZ, minSurfaceY);
      }
      currentStageTargetY = calculateNextStageY(p.getY(), totalTargetSurfaceY, activeStageStep);

      baritone.getPathingBehavior().cancelEverything();
      Goal goal = new GoalBlock(currentAnchorX, currentStageTargetY, currentAnchorZ);
      baritone.getCustomGoalProcess().setGoalAndPath(goal);
   }

   public static void applyEscalationTier(boolean excavationMode, int minSurfaceY, boolean useSkyLightGradient) {
      applyAntiGiveUpRecovery(excavationMode, minSurfaceY);
   }

   public static void applyEscalationTier(boolean excavationMode, int minSurfaceY) {
      applyAntiGiveUpRecovery(excavationMode, minSurfaceY);
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
      currentTargetY = null;
      currentTargetZ = null;
      currentStageTargetY = 0;
      currentAnchorX = 0;
      currentAnchorZ = 0;
      totalTargetSurfaceY = 0;
      startPos = null;
      trackedPickaxe = ItemStack.EMPTY;
      startPickaxeDamage = 0;

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
            prevThrowawayItems = null;
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

                  // Find lowest sky-exposed opening or valley floor
                  int openY = topY;
                  BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos(x, topY, z);

                  while (openY > 60 && level.canSeeSky(checkPos)) {
                     openY--;
                     checkPos.setY(openY);
                  }
                  openY++;
                  checkPos.setY(openY);

                  // Ensure the opening is not in a hazard (lava / magma / fire)
                  BlockState floorState = level.getBlockState(checkPos.below());
                  if (floorState.is(Blocks.LAVA) || floorState.is(Blocks.MAGMA_BLOCK) || floorState.is(Blocks.FIRE)) {
                     continue;
                  }

                  double hDist = FastMath.hypot((double)(x - playerPos.getX()), (double)(z - playerPos.getZ()));
                  double verticalDiff = Math.max(0, openY - playerPos.getY());
                  double score = hDist + verticalDiff * 1.25;

                  if (score < minScore) {
                     minScore = score;
                     bestPos = new BlockPos(x, openY, z);
                     bestElevation = openY;
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
