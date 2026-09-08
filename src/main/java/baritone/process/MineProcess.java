package baritone.process;

import baritone.Baritone;
import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.pathing.goals.GoalRunAway;
import baritone.api.pathing.goals.GoalTwoBlocks;
import baritone.api.process.IMineProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.BlockOptionalMeta;
import baritone.api.utils.BlockOptionalMetaLookup;
import baritone.api.utils.BlockUtils;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.SettingsUtil;
import baritone.api.utils.input.Input;
import baritone.cache.CachedChunk;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.MovementHelper;
import baritone.utils.BaritoneProcessHelper;
import baritone.utils.BlockStateInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class MineProcess extends BaritoneProcessHelper implements IMineProcess {
   private BlockOptionalMetaLookup filter;
   private List<BlockPos> knownOreLocations;
   private List<BlockPos> blacklist;
   private Map<BlockPos, Long> anticipatedDrops;
   private BlockPos branchPoint;
   private GoalRunAway branchPointRunaway;
   private int desiredQuantity;
   private int tickCount;

   // Clingy & Vein Tracking States
   private BlockPos currentTarget;
   private final Set<BlockPos> activeVeinCluster = new LinkedHashSet<>();
   private final Map<BlockPos, Integer> pathFailureCounts = new HashMap<>();

   public MineProcess(Baritone baritone) {
      super(baritone);
   }

   @Override
   public boolean isActive() {
      return this.filter != null;
   }

   @Override
   public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
      if (this.desiredQuantity > 0) {
         int curr = this.ctx.player().getInventory().items.stream().filter(stack -> this.filter.has(stack)).mapToInt(ItemStack::getCount).sum();
         if (curr >= this.desiredQuantity) {
            this.logDirect("Have " + curr + " valid items");
            this.cancel();
            return null;
         }
      }

      if (calcFailed) {
         BlockPos failedTarget = this.currentTarget;
         if (failedTarget == null && !this.knownOreLocations.isEmpty()) {
            failedTarget = this.knownOreLocations.stream().min(Comparator.comparingDouble(this.ctx.playerFeet()::distSqr)).orElse(null);
         }

         if (failedTarget != null && Baritone.settings().blacklistClosestOnFailure.value) {
            int retries = this.pathFailureCounts.merge(failedTarget, 1, Integer::sum);
            int maxRetries = Baritone.settings().mineClingyTarget.value ? Baritone.settings().mineFailureRetryCount.value : 2;

            if (retries >= maxRetries) {
               this.logDirect("Unable to find path to " + failedTarget + " after " + retries + " attempts, blacklisting presumably unreachable target...");
               if (Baritone.settings().notificationOnMineFail.value) {
                  this.logNotification("Unable to find path to " + failedTarget + " after " + retries + " attempts, blacklisting presumably unreachable target...", true);
               }
               this.blacklist.add(failedTarget);
               this.knownOreLocations.remove(failedTarget);
               this.activeVeinCluster.remove(failedTarget);
               if (failedTarget.equals(this.currentTarget)) {
                  this.currentTarget = null;
               }
            } else {
               this.logDebug("Path calculation failed to " + failedTarget + ", retrying (" + retries + "/" + maxRetries + ")...");
            }
         }

         // If knownOreLocations is now empty, perform a fresh synchronous scan of loaded chunks before giving up!
         if (this.knownOreLocations.isEmpty()) {
            CalculationContext context = new CalculationContext(this.baritone);
            List<BlockPos> fresh = searchWorld(context, this.filter, Baritone.settings().mineMaxOreLocationsCount.value, Collections.emptyList(), this.blacklist, this.droppedItemsScan());
            if (!fresh.isEmpty()) {
               this.knownOreLocations = fresh;
               this.currentTarget = null;
            } else {
               this.logDirect("Unable to find any path to " + this.filter + ", canceling mine");
               if (Baritone.settings().notificationOnMineFail.value) {
                  this.logNotification("Unable to find any path to " + this.filter + ", canceling mine", true);
               }
               this.cancel();
               return null;
            }
         }
      }

      // Cleanup target if it has already been mined or turned to air (guarding against drops)
      if (this.currentTarget != null) {
         boolean isDrop = this.droppedItemsScan().contains(this.currentTarget);
         if (!isDrop) {
            BlockState state = this.baritone.bsi.get0(this.currentTarget);
            if (state.getBlock() instanceof AirBlock || (this.filter != null && !this.filter.has(state))) {
               this.knownOreLocations.remove(this.currentTarget);
               this.activeVeinCluster.remove(this.currentTarget);
               this.pathFailureCounts.remove(this.currentTarget);
               this.currentTarget = null;
            }
         }
      }

      this.activeVeinCluster.removeIf(pos -> {
         BlockState state = this.baritone.bsi.get0(pos);
         return state.getBlock() instanceof AirBlock || (this.filter != null && !this.filter.has(state));
      });

      this.updateLoucaSystem();
      int mineGoalUpdateInterval = Baritone.settings().mineGoalUpdateInterval.value;
      List<BlockPos> curr = new ArrayList<>(this.knownOreLocations);
      if (mineGoalUpdateInterval != 0 && this.tickCount++ % mineGoalUpdateInterval == 0) {
         CalculationContext context = new CalculationContext(this.baritone, true);
         Baritone.getExecutor().execute(() -> this.rescan(curr, context));
      }

      if (Baritone.settings().legitMine.value && !this.addNearby()) {
         this.cancel();
         return null;
      } else {
         // Direct In-Place Reachable Mining & Shaft Mining
         Optional<BlockPos> shaft = curr.stream()
            .filter(pos -> pos.getX() == this.ctx.playerFeet().getX() && pos.getZ() == this.ctx.playerFeet().getZ())
            .filter(pos -> pos.getY() >= this.ctx.playerFeet().getY())
            .filter(pos -> !(BlockStateInterface.get(this.ctx, pos).getBlock() instanceof AirBlock))
            .filter(pos -> !MovementHelper.avoidBreaking(this.baritone.bsi, pos.getX(), pos.getY(), pos.getZ(), this.baritone.bsi.get0(pos)))
            .min(Comparator.comparingDouble(this.ctx.playerFeet().above()::distSqr));

         BlockPos targetToBreak = null;
         if (Baritone.settings().mineDirectReachable.value) {
            Optional<BlockPos> direct = this.findReachableTarget(curr);
            if (direct.isPresent()) {
               targetToBreak = direct.get();
            }
         }

         if (targetToBreak == null && shaft.isPresent() && this.ctx.player().onGround()) {
            targetToBreak = shaft.get();
         }

         this.baritone.getInputOverrideHandler().clearAllKeys();
         if (targetToBreak != null) {
            BlockState state = this.baritone.bsi.get0(targetToBreak);
            if (!MovementHelper.avoidBreaking(this.baritone.bsi, targetToBreak.getX(), targetToBreak.getY(), targetToBreak.getZ(), state)) {
               Optional<Rotation> rot = RotationUtils.reachable(this.ctx, targetToBreak);
               if (rot.isPresent() && isSafeToCancel) {
                  this.currentTarget = targetToBreak;
                  if (Baritone.settings().mineVeinCluster.value && this.activeVeinCluster.isEmpty()) {
                     this.discoverVeinCluster(targetToBreak, new CalculationContext(this.baritone, true));
                  }
                    this.baritone.getLookBehavior().updateTarget(rot.get(), true);
                    MovementHelper.switchToBestToolFor(this.ctx, this.ctx.world().getBlockState(targetToBreak));
                    this.baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);

                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
               }
            }
         }

         PathingCommand command = this.updateGoal();
         if (command == null) {
            this.cancel();
            return null;
         } else {
            return command;
         }
      }
   }

   private Optional<BlockPos> findReachableTarget(List<BlockPos> candidates) {
      // Only mine reachable blocks in-place if player is standing on the ground or swimming in liquid
      // (Never interrupt pathing or pillaring mid-air during jumps!)
      if (!this.ctx.player().onGround() && !MovementHelper.isLiquid(this.ctx, this.ctx.playerFeet())) {
         return Optional.empty();
      }

      // 1. Check currentTarget first (highest priority)
      if (this.currentTarget != null && candidates.contains(this.currentTarget)) {
         BlockState state = this.baritone.bsi.get0(this.currentTarget);
         if (!(state.getBlock() instanceof AirBlock) && (this.filter == null || this.filter.has(state))) {
            if (!MovementHelper.avoidBreaking(this.baritone.bsi, this.currentTarget.getX(), this.currentTarget.getY(), this.currentTarget.getZ(), state)) {
               if (RotationUtils.reachable(this.ctx, this.currentTarget).isPresent()) {
                  return Optional.of(this.currentTarget);
               }
            }
         }
      }

      // 2. Check activeVeinCluster
      for (BlockPos pos : this.activeVeinCluster) {
         if (candidates.contains(pos)) {
            BlockState state = this.baritone.bsi.get0(pos);
            if (!(state.getBlock() instanceof AirBlock) && (this.filter == null || this.filter.has(state))) {
               if (!MovementHelper.avoidBreaking(this.baritone.bsi, pos.getX(), pos.getY(), pos.getZ(), state)) {
                  if (RotationUtils.reachable(this.ctx, pos).isPresent()) {
                     return Optional.of(pos);
                  }
               }
            }
         }
      }

      // 3. Check all nearby candidates within 5 blocks
      double reachSq = 25.0; // 5.0 blocks squared
      BlockPos feet = this.ctx.playerFeet();
      return candidates.stream()
         .filter(pos -> pos.distSqr(feet) <= reachSq)
         .filter(pos -> {
            BlockState state = this.baritone.bsi.get0(pos);
            return !(state.getBlock() instanceof AirBlock) && (this.filter == null || this.filter.has(state));
         })
         .filter(pos -> !MovementHelper.avoidBreaking(this.baritone.bsi, pos.getX(), pos.getY(), pos.getZ(), this.baritone.bsi.get0(pos)))
         .filter(pos -> RotationUtils.reachable(this.ctx, pos).isPresent())
         .min(Comparator.comparingDouble(this.ctx.playerFeet()::distSqr));
   }

   private void updateLoucaSystem() {
      Map<BlockPos, Long> copy = new HashMap<>(this.anticipatedDrops);
      this.ctx.getSelectedBlock().ifPresent(posx -> {
         if (this.knownOreLocations.contains(posx)) {
            copy.put(posx, System.currentTimeMillis() + Baritone.settings().mineDropLoiterDurationMSThanksLouca.value);
         }
      });

      for (BlockPos pos : this.anticipatedDrops.keySet()) {
         if (copy.get(pos) < System.currentTimeMillis()) {
            copy.remove(pos);
         }
      }

      this.anticipatedDrops = copy;
   }

   @Override
   public void onLostControl() {
      this.mine(0, (BlockOptionalMetaLookup)null);
   }

   @Override
   public String displayName0() {
      return "Mine " + this.filter;
   }

   private PathingCommand updateGoal() {
      BlockOptionalMetaLookup filter = this.filterFilter();
      if (filter == null) {
         return null;
      } else {
         boolean legit = Baritone.settings().legitMine.value;
         List<BlockPos> locs = this.knownOreLocations;
         CalculationContext context = new CalculationContext(this.baritone);
         List<BlockPos> dropped = this.droppedItemsScan();
         List<BlockPos> candidates = new ArrayList<>(locs);
         for (BlockPos drop : dropped) {
            if (!candidates.contains(drop)) {
               candidates.add(drop);
            }
         }

         if (candidates.isEmpty()) {
            List<BlockPos> fresh = searchWorld(context, filter, Baritone.settings().mineMaxOreLocationsCount.value, Collections.emptyList(), this.blacklist, dropped);
            for (BlockPos p : fresh) {
               if (!candidates.contains(p)) {
                  candidates.add(p);
               }
            }
         }

         if (!candidates.isEmpty()) {
            List<BlockPos> locs2 = prune(
               context, candidates, filter, Baritone.settings().mineMaxOreLocationsCount.value, this.blacklist, dropped
            );
            for (BlockPos dropPos : dropped) {
               if (locs2.contains(dropPos)) {
                  locs2.remove(dropPos);
                  locs2.add(0, dropPos);
               } else {
                  locs2.add(0, dropPos);
               }
            }
            this.knownOreLocations = locs2;

            if (locs2.isEmpty()) {
               if (!Baritone.settings().exploreForBlocks.value) {
                  return null;
               }
               return this.exploreGoal();
            }

            if (Baritone.settings().mineClingyTarget.value) {
               this.maintainTarget(locs2, context);
            }

            Goal goal;
            if (Baritone.settings().mineClingyTarget.value && this.currentTarget != null) {
               if (Baritone.settings().mineVeinCluster.value && !this.activeVeinCluster.isEmpty()) {
                  List<BlockPos> clusterList = this.activeVeinCluster.stream()
                     .filter(locs2::contains)
                     .collect(Collectors.toList());
                  if (!clusterList.isEmpty()) {
                     goal = new GoalComposite(clusterList.stream().map(loc -> this.coalesce(loc, clusterList, context)).toArray(Goal[]::new));
                  } else {
                     goal = this.coalesce(this.currentTarget, locs2, context);
                  }
               } else {
                  goal = this.coalesce(this.currentTarget, locs2, context);
               }
            } else {
               goal = new GoalComposite(locs2.stream().map(loc -> this.coalesce(loc, locs2, context)).toArray(Goal[]::new));
            }

            return new PathingCommand(goal, PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH);
         } else if (!Baritone.settings().exploreForBlocks.value) {
            return null;
         } else {
            return this.exploreGoal();
         }
      }
   }

   private void maintainTarget(List<BlockPos> locs, CalculationContext context) {
      BlockPos feet = this.ctx.playerFeet();
      List<BlockPos> drops = this.droppedItemsScan();

      // Priority 0: If there are dropped items nearby, target the nearest dropped item immediately!
      if (!drops.isEmpty()) {
         Optional<BlockPos> nearestDrop = drops.stream().min(Comparator.comparingDouble(feet::distSqr));
         if (nearestDrop.isPresent()) {
            this.currentTarget = nearestDrop.get();
            return;
         }
      }

      // 1. Verify existing currentTarget
      if (this.currentTarget != null) {
         boolean stillKnown = locs.contains(this.currentTarget);
         BlockState state = context.bsi.get0(this.currentTarget);
         boolean isDrop = drops.contains(this.currentTarget);
         boolean stillValid = stillKnown
            && !this.blacklist.contains(this.currentTarget)
            && (isDrop || (!(state.getBlock() instanceof AirBlock) && (this.filter == null || this.filter.has(state))));

         if (stillValid) {
            double currentDist = Math.sqrt(this.currentTarget.distSqr(feet));
            double hysteresisMargin = Baritone.settings().mineHysteresisDistance.value;

            if (hysteresisMargin > 0.0 && !isDrop) {
               Optional<BlockPos> muchCloser = locs.stream()
                  .filter(pos -> !pos.equals(this.currentTarget))
                  .filter(pos -> Math.sqrt(pos.distSqr(feet)) < (currentDist - hysteresisMargin))
                  .min(Comparator.comparingDouble(feet::distSqr));

               if (muchCloser.isPresent()) {
                  this.currentTarget = muchCloser.get();
                  if (Baritone.settings().mineVeinCluster.value) {
                     this.activeVeinCluster.clear();
                     this.discoverVeinCluster(this.currentTarget, context);
                  }
               }
            }
            return;
         } else {
            this.currentTarget = null;
         }
      }

      // 2. If activeVeinCluster still has remaining valid blocks, pick the nearest one in the cluster
      if (Baritone.settings().mineVeinCluster.value && !this.activeVeinCluster.isEmpty()) {
         Optional<BlockPos> nextInCluster = this.activeVeinCluster.stream()
            .filter(locs::contains)
            .filter(pos -> {
               BlockState state = context.bsi.get0(pos);
               return !(state.getBlock() instanceof AirBlock) && (this.filter == null || this.filter.has(state));
            })
            .min(Comparator.comparingDouble(feet::distSqr));

         if (nextInCluster.isPresent()) {
            this.currentTarget = nextInCluster.get();
            return;
         } else {
            this.activeVeinCluster.clear();
         }
      }

      // 3. Otherwise pick the best new candidate from locs (nearest)
      if (!locs.isEmpty()) {
         this.currentTarget = locs.stream().min(Comparator.comparingDouble(feet::distSqr)).orElse(null);
         if (this.currentTarget != null && Baritone.settings().mineVeinCluster.value) {
            this.activeVeinCluster.clear();
            this.discoverVeinCluster(this.currentTarget, context);
         }
      }
   }

   private void discoverVeinCluster(BlockPos seed, CalculationContext context) {
      this.activeVeinCluster.add(seed);
      BlockOptionalMetaLookup filter = this.filterFilter();
      if (filter == null) return;

      List<BlockPos> queue = new ArrayList<>();
      queue.add(seed);
      int maxClusterSize = 24;

      while (!queue.isEmpty() && this.activeVeinCluster.size() < maxClusterSize) {
         BlockPos current = queue.remove(0);

         for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
               for (int dz = -1; dz <= 1; dz++) {
                  if (dx == 0 && dy == 0 && dz == 0) continue;
                  BlockPos neighbor = current.offset(dx, dy, dz);
                  if (this.activeVeinCluster.contains(neighbor) || this.blacklist.contains(neighbor)) continue;

                  if (context.bsi.worldContainsLoadedChunk(neighbor.getX(), neighbor.getZ())) {
                     BlockState state = context.bsi.get0(neighbor);
                     if (filter.has(state) && plausibleToBreak(context, neighbor)) {
                        this.activeVeinCluster.add(neighbor);
                        queue.add(neighbor);
                     }
                  }
               }
            }
         }
      }
   }

   private PathingCommand exploreGoal() {
      if (!Baritone.settings().exploreForBlocks.value) {
         this.logDirect("No target blocks found in loaded area, stopping.");
         this.cancel();
         return null;
      }
      int y = Baritone.settings().legitMineYLevel.value;
      if (this.branchPoint == null) {
         this.branchPoint = this.ctx.playerFeet();
      }

      if (this.branchPointRunaway == null) {
         this.branchPointRunaway = new GoalRunAway(1.0, y, this.branchPoint) {
            @Override
            public boolean isInGoal(int x, int y, int z) {
               return false;
            }

            @Override
            public double heuristic() {
               return Double.NEGATIVE_INFINITY;
            }
         };
      }

      return new PathingCommand(this.branchPointRunaway, PathingCommandType.REVALIDATE_GOAL_AND_PATH);
   }

   private void rescan(List<BlockPos> already, CalculationContext context) {
      BlockOptionalMetaLookup filter = this.filterFilter();
      if (filter != null) {
         if (!Baritone.settings().legitMine.value) {
            List<BlockPos> dropped = this.droppedItemsScan();
            List<BlockPos> locs = searchWorld(context, filter, Baritone.settings().mineMaxOreLocationsCount.value, already, this.blacklist, dropped);
            locs.addAll(dropped);
            if (locs.isEmpty() && !Baritone.settings().exploreForBlocks.value) {
               this.logDirect("No locations for " + filter + " known, cancelling");
               if (Baritone.settings().notificationOnMineFail.value) {
                  this.logNotification("No locations for " + filter + " known, cancelling", true);
               }

               this.cancel();
            } else {
               this.knownOreLocations = locs;
            }
         }
      }
   }

   private boolean internalMiningGoal(BlockPos pos, CalculationContext context, List<BlockPos> locs) {
      if (locs.contains(pos)) {
         return true;
      } else {
         BlockState state = context.bsi.get0(pos);
         return Baritone.settings().internalMiningAirException.value && state.getBlock() instanceof AirBlock
            ? true
            : this.filter.has(state) && plausibleToBreak(context, pos);
      }
   }

   private Goal coalesce(BlockPos loc, List<BlockPos> locs, CalculationContext context) {
      if (this.droppedItemsScan().contains(loc)) {
         return new GoalBlock(loc);
      }
      BlockStateInterface bsi = context != null && context.bsi != null ? context.bsi : this.baritone.bsi;
      BlockState aboveState = bsi.get0(loc.above());
      boolean assumeVerticalShaftMine = !(aboveState.getBlock() instanceof FallingBlock)
         && aboveState.getFluidState().isEmpty()
         && !MovementHelper.avoidBreaking(bsi, loc.getX(), loc.getY(), loc.getZ(), bsi.get0(loc));
      if (!Baritone.settings().forceInternalMining.value) {
         return (Goal)(assumeVerticalShaftMine ? new MineProcess.GoalThreeBlocks(loc) : new GoalTwoBlocks(loc));
      } else {
         boolean upwardGoal = this.internalMiningGoal(loc.above(), context, locs);
         boolean downwardGoal = this.internalMiningGoal(loc.below(), context, locs);
         boolean doubleDownwardGoal = this.internalMiningGoal(loc.below(2), context, locs);
         if (upwardGoal == downwardGoal) {
            return (Goal)(doubleDownwardGoal && assumeVerticalShaftMine ? new MineProcess.GoalThreeBlocks(loc) : new GoalTwoBlocks(loc));
         } else if (upwardGoal) {
            return new GoalBlock(loc);
         } else {
            return (Goal)(doubleDownwardGoal && assumeVerticalShaftMine ? new GoalTwoBlocks(loc.below()) : new GoalBlock(loc.below()));
         }
      }
   }

   public List<BlockPos> droppedItemsScan() {
      if (!Baritone.settings().mineScanDroppedItems.value) {
         return Collections.emptyList();
      } else {
         List<BlockPos> ret = new ArrayList<>();

         for (Entity entity : ((ClientLevel)this.ctx.world()).entitiesForRendering()) {
            if (entity instanceof ItemEntity) {
               ItemEntity ei = (ItemEntity)entity;
               if (this.filter == null || this.filter.has(ei.getItem())) {
                  BlockPos bp = entity.blockPosition();
                  if (!ret.contains(bp)) {
                     ret.add(bp);
                  }
               }
            }
         }

         for (BlockPos p : this.anticipatedDrops.keySet()) {
            if (!ret.contains(p)) {
               ret.add(p);
            }
         }
         return ret;
      }
   }

   public static List<BlockPos> searchWorld(
      CalculationContext ctx, BlockOptionalMetaLookup filter, int max, List<BlockPos> alreadyKnown, List<BlockPos> blacklist, List<BlockPos> dropped
   ) {
      List<BlockPos> locs = new ArrayList<>();
      List<Block> untracked = new ArrayList<>();
      boolean onlyLoaded = Baritone.settings().mineOnlyLoadedChunks.value;

      for (BlockOptionalMeta bom : filter.blocks()) {
         Block block = bom.getBlock();
         if (CachedChunk.BLOCKS_TO_KEEP_TRACK_OF.contains(block)) {
            if (!onlyLoaded) {
               BetterBlockPos pf = ctx.baritone.getPlayerContext().playerFeet();
               locs.addAll(
                  ctx.worldData.getCachedWorld().getLocationsOf(BlockUtils.blockToString(block), Baritone.settings().maxCachedWorldScanCount.value, pf.x, pf.z, 2)
               );
            }
         } else {
            untracked.add(block);
         }
      }

      locs = prune(ctx, locs, filter, max, blacklist, dropped);
      if (onlyLoaded || !untracked.isEmpty() || (Baritone.settings().extendCacheOnThreshold.value && locs.size() < max) || locs.isEmpty()) {
         int scanRadius = Math.max(1, Math.min(32, Baritone.settings().mineMaxChunkRadius.value));
         List<BlockPos> scanned = BaritoneAPI.getProvider().getWorldScanner().scanChunkRadius(ctx.getBaritone().getPlayerContext(), filter, max, 10, scanRadius);
         for (BlockPos p : scanned) {
            if (!locs.contains(p)) {
               locs.add(p);
            }
         }
      }

      for (BlockPos p : alreadyKnown) {
         if (!locs.contains(p)) {
            locs.add(p);
         }
      }
      return prune(ctx, locs, filter, max, blacklist, dropped);
   }

   private boolean addNearby() {
      List<BlockPos> dropped = this.droppedItemsScan();
      this.knownOreLocations.addAll(dropped);
      BlockPos playerFeet = this.ctx.playerFeet();
      BlockStateInterface bsi = new BlockStateInterface(this.ctx);
      BlockOptionalMetaLookup filter = this.filterFilter();
      if (filter == null) {
         return false;
      } else {
         int searchDist = 10;
         double fakedBlockReachDistance = 20.0;

         for (int x = playerFeet.getX() - searchDist; x <= playerFeet.getX() + searchDist; x++) {
            for (int y = playerFeet.getY() - searchDist; y <= playerFeet.getY() + searchDist; y++) {
               for (int z = playerFeet.getZ() - searchDist; z <= playerFeet.getZ() + searchDist; z++) {
                  if (filter.has(bsi.get0(x, y, z))) {
                     BlockPos pos = new BlockPos(x, y, z);
                     if (Baritone.settings().legitMineIncludeDiagonals.value && this.knownOreLocations.stream().anyMatch(ore -> ore.distSqr(pos) <= 2.0)
                        || RotationUtils.reachable(this.ctx, pos, fakedBlockReachDistance).isPresent()) {
                        this.knownOreLocations.add(pos);
                     }
                  }
               }
            }
         }

         this.knownOreLocations = prune(
            new CalculationContext(this.baritone), this.knownOreLocations, filter, Baritone.settings().mineMaxOreLocationsCount.value, this.blacklist, dropped
         );
         return true;
      }
   }

   private static List<BlockPos> prune(
      CalculationContext ctx, List<BlockPos> locs2, BlockOptionalMetaLookup filter, int max, List<BlockPos> blacklist, List<BlockPos> dropped
   ) {
      boolean onlyLoaded = Baritone.settings().mineOnlyLoadedChunks.value;
      List<BlockPos> locs = locs2.stream()
         .distinct()
         .filter(pos -> {
            boolean isLoaded = ctx.bsi.worldContainsLoadedChunk(pos.getX(), pos.getZ());
            if (onlyLoaded && !isLoaded) {
               return false;
            }
            return !isLoaded
                  || filter.has(ctx.get(pos.getX(), pos.getY(), pos.getZ()))
                  || dropped.contains(pos);
         })
         .filter(pos -> dropped.contains(pos) || plausibleToBreak(ctx, pos))
         .filter(pos -> dropped.contains(pos) || (Baritone.settings().allowOnlyExposedOres.value ? isNextToAir(ctx, pos) : true))
         .filter(pos -> dropped.contains(pos) || (pos.getY() >= Baritone.settings().minYLevelWhileMining.value + ctx.world.dimensionType().minY() && pos.getY() <= Baritone.settings().maxYLevelWhileMining.value))
         .filter(pos -> !blacklist.contains(pos))
         .sorted(Comparator.comparingDouble(ctx.getBaritone().getPlayerContext().player().blockPosition()::distSqr))
         .collect(Collectors.toList());
      return locs.size() > max ? locs.subList(0, max) : locs;
   }

   public static boolean isNextToAir(CalculationContext ctx, BlockPos pos) {
      int radius = Baritone.settings().allowOnlyExposedOresDistance.value;

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
               if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= radius
                  && MovementHelper.isTransparent(ctx.getBlock(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz))) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   public static boolean plausibleToBreak(CalculationContext ctx, BlockPos pos) {
      BlockState state = ctx.bsi.get0(pos);
      if (MovementHelper.getMiningDurationTicks(ctx, pos.getX(), pos.getY(), pos.getZ(), state, true) >= 1000000.0) {
         return false;
      } else {
         return MovementHelper.avoidBreaking(ctx.bsi, pos.getX(), pos.getY(), pos.getZ(), state)
            ? false
            : ctx.bsi.get0(pos.above()).getBlock() != Blocks.BEDROCK || ctx.bsi.get0(pos.below()).getBlock() != Blocks.BEDROCK;
      }
   }

   @Override
   public void mineByName(int quantity, String... blocks) {
      this.mine(quantity, new BlockOptionalMetaLookup(blocks));
   }

   @Override
   public void mine(int quantity, BlockOptionalMetaLookup filter) {
      this.filter = filter;
      if (this.filterFilter() == null) {
         this.filter = null;
      }

      this.desiredQuantity = quantity;
      this.knownOreLocations = new ArrayList<>();
      this.blacklist = new ArrayList<>();
      this.branchPoint = null;
      this.branchPointRunaway = null;
      this.anticipatedDrops = new HashMap<>();
      this.currentTarget = null;
      this.activeVeinCluster.clear();
      this.pathFailureCounts.clear();
      if (filter != null) {
         this.rescan(new ArrayList<>(), new CalculationContext(this.baritone));
      }
   }

   @Override
   public void mine(int quantity, BlockOptionalMetaLookup filter, List<BlockPos> initialLocations) {
      this.mine(quantity, filter);
      if (initialLocations != null && !initialLocations.isEmpty()) {
         for (BlockPos p : initialLocations) {
            if (!this.knownOreLocations.contains(p) && !this.blacklist.contains(p)) {
               this.knownOreLocations.add(p);
            }
         }
      }
   }

   private BlockOptionalMetaLookup filterFilter() {
      if (this.filter == null) {
         return null;
      } else if (!Baritone.settings().allowBreak.value) {
         BlockOptionalMetaLookup f = new BlockOptionalMetaLookup(
            this.filter.blocks().stream().filter(e -> Baritone.settings().allowBreakAnyway.value.contains(e.getBlock())).toArray(BlockOptionalMeta[]::new)
         );
         if (f.blocks().isEmpty()) {
            this.logDirect("Unable to mine when allowBreak is false and target block is not in allowBreakAnyway!");
            return null;
         } else {
            return f;
         }
      } else {
         return this.filter;
      }
   }

   private static class GoalThreeBlocks extends GoalTwoBlocks {
      public GoalThreeBlocks(BlockPos pos) {
         super(pos);
      }

      @Override
      public boolean isInGoal(int x, int y, int z) {
         return x == this.x && (y == this.y || y == this.y - 1 || y == this.y - 2) && z == this.z;
      }

      @Override
      public double heuristic(int x, int y, int z) {
         int xDiff = x - this.x;
         int yDiff = y - this.y;
         int zDiff = z - this.z;
         return GoalBlock.calculate((double)xDiff, yDiff < -1 ? yDiff + 2 : (yDiff == -1 ? 0 : yDiff), (double)zDiff);
      }

      @Override
      public boolean equals(Object o) {
         return super.equals(o);
      }

      @Override
      public int hashCode() {
         return super.hashCode() * 393857768;
      }

      @Override
      public String toString() {
         return String.format(
            "GoalThreeBlocks{x=%s,y=%s,z=%s}", SettingsUtil.maybeCensor(this.x), SettingsUtil.maybeCensor(this.y), SettingsUtil.maybeCensor(this.z)
         );
      }
   }
}
