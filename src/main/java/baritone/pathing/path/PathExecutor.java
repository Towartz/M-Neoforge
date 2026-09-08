package baritone.pathing.path;

import baritone.Baritone;
import baritone.api.pathing.calc.IPath;
import baritone.api.pathing.movement.IMovement;
import baritone.api.pathing.movement.MovementStatus;
import baritone.api.pathing.path.IPathExecutor;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.Helper;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.VecUtils;
import baritone.api.utils.input.Input;
import baritone.behavior.PathingBehavior;
import baritone.pathing.calc.AbstractNodeCostSearch;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.Movement;
import baritone.pathing.movement.MovementHelper;
import baritone.pathing.movement.MovementState;
import baritone.pathing.movement.movements.MovementAscend;
import baritone.pathing.movement.movements.MovementDescend;
import baritone.pathing.movement.movements.MovementDiagonal;
import baritone.pathing.movement.movements.MovementFall;
import baritone.pathing.movement.movements.MovementParkour;
import baritone.pathing.movement.movements.MovementTraverse;
import baritone.utils.BlockStateInterface;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Tuple;
import net.minecraft.world.phys.Vec3;

public class PathExecutor implements IPathExecutor, Helper {
   private static final double MAX_MAX_DIST_FROM_PATH = 3.0;
   private static final double MAX_DIST_FROM_PATH = 2.0;
   private static final double MAX_TICKS_AWAY = 200.0;
   private final IPath path;
   private int pathPosition;
   private int ticksAway;
   private int ticksOnCurrent;
   private Double currentMovementOriginalCostEstimate;
   private Integer costEstimateIndex;
   private boolean failed;
   private boolean recalcBP = true;
   private HashSet<BlockPos> toBreak = new HashSet<>();
   private HashSet<BlockPos> toPlace = new HashSet<>();
   private HashSet<BlockPos> toWalkInto = new HashSet<>();
   private final PathingBehavior behavior;
   private final IPlayerContext ctx;
   private boolean sprintNextTick;
   private int onTickDepth = 0;
   private static final int MAX_ON_TICK_DEPTH = 3;
   private Vec3 lastStuckSamplePos = null;
   private int ticksStationary = 0;

   public PathExecutor(PathingBehavior behavior, IPath path) {
      this.behavior = behavior;
      this.ctx = behavior.ctx;
      this.path = path;
      this.pathPosition = 0;
   }

   public boolean onTick() {
      if (this.onTickDepth >= MAX_ON_TICK_DEPTH) {
         return false;
      }
      this.onTickDepth++;
      try {
         return this.onTickInternal();
      } finally {
         this.onTickDepth--;
      }
   }

   private boolean onTickInternal() {
      int numMovements = this.path.movements().size();
      if (this.pathPosition >= numMovements) {
         this.pathPosition = this.path.length();
         return true;
      }

      BetterBlockPos whereAmI = this.ctx.playerFeet();
      if (whereAmI != null && whereAmI.equals(this.path.getDest())) {
         this.pathPosition = this.path.length();
         return true;
      }

      Movement movement = (Movement)this.path.movements().get(this.pathPosition);
      if (!movement.getValidPositions().contains(whereAmI)) {
         for (int i = 0; i < this.pathPosition && i < numMovements; i++) {
            if (((Movement)this.path.movements().get(i)).getValidPositions().contains(whereAmI)) {
               int previousPos = this.pathPosition;
               this.pathPosition = i;

               for (int j = this.pathPosition; j <= previousPos && j < numMovements; j++) {
                  this.path.movements().get(j).reset();
               }

               this.onChangeInPathPosition();
               return false;
            }
         }

         for (int ix = this.pathPosition + 1; ix < numMovements; ix++) {
            if (((Movement)this.path.movements().get(ix)).getValidPositions().contains(whereAmI)) {
               if (ix > this.pathPosition) {
                  this.logDebug("Advancing forward " + (ix - this.pathPosition) + " steps, to " + ix);
               }

               this.pathPosition = ix;
               this.onChangeInPathPosition();
               this.onTick();
               return false;
            }
         }
      }

         Tuple<Double, BlockPos> status = this.closestPathPos(this.path);
         double hSpeed = this.ctx.player() != null ? this.ctx.player().getDeltaMovement().horizontalDistance() : 0.0;
         double dynamicThresholdNear = Math.max(2.0, 2.0 + hSpeed * 1.5);
         double dynamicThresholdFar = Math.max(3.0, 3.0 + hSpeed * 2.5);
         if (this.possiblyOffPath(status, dynamicThresholdNear)) {
            this.ticksAway++;
            this.logDebug("FAR AWAY FROM PATH FOR " + this.ticksAway + " TICKS. Current distance: " + status.getA() + ". Threshold: " + dynamicThresholdNear);
            if ((double)this.ticksAway > 200.0) {
               this.logDebug("Too far away from path for too long, cancelling path");
               this.cancel();
               return false;
            }
         } else {
            this.ticksAway = 0;
         }

         if (this.possiblyOffPath(status, dynamicThresholdFar)) {
            this.logDebug("too far from path");
            this.cancel();
            return false;
         } else {
            BlockStateInterface bsi = new BlockStateInterface(this.ctx);

            for (int ixx = this.pathPosition - 10; ixx < this.pathPosition + 10; ixx++) {
               if (ixx >= 0 && ixx < this.path.movements().size()) {
                  Movement m = (Movement)this.path.movements().get(ixx);
                  List<BlockPos> prevBreak = m.toBreak(bsi);
                  List<BlockPos> prevPlace = m.toPlace(bsi);
                  List<BlockPos> prevWalkInto = m.toWalkInto(bsi);
                  m.resetBlockCache();
                  if (!prevBreak.equals(m.toBreak(bsi))) {
                     this.recalcBP = true;
                  }

                  if (!prevPlace.equals(m.toPlace(bsi))) {
                     this.recalcBP = true;
                  }

                  if (!prevWalkInto.equals(m.toWalkInto(bsi))) {
                     this.recalcBP = true;
                  }
               }
            }

            if (this.recalcBP) {
               HashSet<BlockPos> newBreak = new HashSet<>();
               HashSet<BlockPos> newPlace = new HashSet<>();
               HashSet<BlockPos> newWalkInto = new HashSet<>();

               for (int ixxx = this.pathPosition; ixxx < this.path.movements().size(); ixxx++) {
                  Movement mx = (Movement)this.path.movements().get(ixxx);
                  newBreak.addAll(mx.toBreak(bsi));
                  newPlace.addAll(mx.toPlace(bsi));
                  newWalkInto.addAll(mx.toWalkInto(bsi));
               }

               this.toBreak = newBreak;
               this.toPlace = newPlace;
               this.toWalkInto = newWalkInto;
               this.recalcBP = false;
            }

            if (this.pathPosition < this.path.movements().size() - 1) {
               IMovement next = this.path.movements().get(this.pathPosition + 1);
               if (!this.behavior.baritone.bsi.worldContainsLoadedChunk(next.getDest().x, next.getDest().z)) {
                  this.logDebug("Pausing since destination is at edge of loaded chunks");
                  this.clearKeys();
                  return true;
               }
            }

            boolean canCancel = movement.safeToCancel();
            if (this.costEstimateIndex == null || this.costEstimateIndex != this.pathPosition) {
               this.costEstimateIndex = this.pathPosition;
               this.currentMovementOriginalCostEstimate = movement.getCost();

               for (int ixxx = 1; ixxx < Baritone.settings().costVerificationLookahead.value && this.pathPosition + ixxx < this.path.movements().size(); ixxx++) {
                  if (((Movement)this.path.movements().get(this.pathPosition + ixxx)).calculateCost(this.behavior.secretInternalGetCalculationContext())
                        >= 1000000.0
                     && canCancel) {
                     this.logDebug("Something has changed in the world and a future movement has become impossible. Cancelling.");
                     this.cancel();
                     return true;
                  }
               }
            }

            double currentCost = movement.recalculateCost(this.behavior.secretInternalGetCalculationContext());
            if (currentCost >= 1000000.0 && canCancel) {
               this.logDebug("Something has changed in the world and this movement has become impossible. Cancelling.");
               this.cancel();
               return true;
            } else if (!movement.calculatedWhileLoaded()
               && currentCost - this.currentMovementOriginalCostEstimate > Baritone.settings().maxCostIncrease.value
               && canCancel) {
               this.logDebug("Original cost " + this.currentMovementOriginalCostEstimate + " current cost " + currentCost + ". Cancelling.");
               this.cancel();
               return true;
            } else if (this.shouldPause()) {
               this.logDebug("Pausing since current best path is a backtrack");
               this.clearKeys();
               return true;
            } else {
               MovementStatus movementStatus = movement.update();
               if (movementStatus == MovementStatus.UNREACHABLE || movementStatus == MovementStatus.FAILED) {
                  this.logDebug("Movement returns status " + movementStatus);
                  this.cancel();
                  return true;
               } else if (movementStatus == MovementStatus.SUCCESS) {
                  this.pathPosition++;
                  this.onChangeInPathPosition();
                  this.onTick();
                  return true;
               } else {
                  this.sprintNextTick = this.shouldSprintNextTick();
                  if (!this.sprintNextTick && !MovementHelper.isSprintActive()) {
                     this.ctx.player().setSprinting(false);
                  }

                  this.ticksOnCurrent++;

                  // Multi-Stage Anti-Stuck Displacement Watchdog
                  Vec3 currentPos = this.ctx.player().position();
                  boolean isBreakingOrPreparingToMine = (this.ctx.minecraft().gameMode != null && this.ctx.minecraft().gameMode.isDestroying())
                     || this.behavior.baritone.getInputOverrideHandler().getBlockBreakHelper().isBreakingBlock()
                     || this.behavior.baritone.getInputOverrideHandler().isInputForcedDown(Input.CLICK_LEFT)
                     || movementStatus == MovementStatus.PREPPING
                     || (this.behavior.baritone.getMineProcess().isActive() && this.ctx.getSelectedBlock().isPresent());

                  if (!isBreakingOrPreparingToMine && movement.toBreakAll().length > 0) {
                     for (BlockPos b : movement.toBreakAll()) {
                        if (!MovementHelper.canWalkThrough(this.ctx, new BetterBlockPos(b))) {
                           isBreakingOrPreparingToMine = true;
                           break;
                        }
                     }
                  }

                  if (isBreakingOrPreparingToMine) {
                     this.lastStuckSamplePos = currentPos;
                     this.ticksStationary = 0;
                  } else if (this.lastStuckSamplePos == null) {
                     this.lastStuckSamplePos = currentPos;
                     this.ticksStationary = 0;
                  } else {
                     double dx = currentPos.x - this.lastStuckSamplePos.x;
                     double dz = currentPos.z - this.lastStuckSamplePos.z;
                     double flatDistMoved = Math.sqrt(dx * dx + dz * dz);

                     if (flatDistMoved < 0.05) {
                        this.ticksStationary++;

                        // Stage 1 (25 - 32 ticks stationary): Unwedge hop & sprint pulse to clear corners and steps (only if headroom exists!)
                        if (this.ticksStationary >= 25 && this.ticksStationary <= 32) {
                           boolean hasHeadroom = MovementHelper.canWalkThrough(this.ctx, this.ctx.playerFeet().above(2));
                           if (hasHeadroom) {
                              this.behavior.baritone.getInputOverrideHandler().setInputForceState(Input.JUMP, true);
                           }
                           this.behavior.baritone.getInputOverrideHandler().setInputForceState(Input.SPRINT, true);
                           this.behavior.baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, true);
                        }

                        // Stage 2 (35 - 45 ticks stationary): Check if player is entangled inside a block or wall
                        if (this.ticksStationary >= 35 && this.ticksStationary <= 45) {
                           BetterBlockPos headPos = this.ctx.playerFeet().above();
                           BetterBlockPos feetPos = this.ctx.playerFeet();
                           if (!MovementHelper.canWalkThrough(this.ctx, headPos)) {
                              MovementHelper.switchToBestToolFor(this.ctx, BlockStateInterface.get(this.ctx, headPos));
                              this.behavior.baritone.getLookBehavior().updateTarget(
                                 RotationUtils.calcRotationFromVec3d(this.ctx.playerHead(), VecUtils.getBlockPosCenter(headPos), this.ctx.playerRotations()), true
                              );
                              this.behavior.baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                           } else if (!MovementHelper.canWalkThrough(this.ctx, feetPos)) {
                              MovementHelper.switchToBestToolFor(this.ctx, BlockStateInterface.get(this.ctx, feetPos));
                              this.behavior.baritone.getLookBehavior().updateTarget(
                                 RotationUtils.calcRotationFromVec3d(this.ctx.playerHead(), VecUtils.getBlockPosCenter(feetPos), this.ctx.playerRotations()),
                                 true
                              );
                              this.behavior.baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                           }
                        }

                        // Stage 3 (50 ticks stationary): Fast-cancel stuck movement to trigger immediate recalculation!
                        if (this.ticksStationary >= 50) {
                           this.logDebug("Anti-stuck: Player stationary for 50 ticks during " + movement.getClass().getSimpleName() + ". Cancelling to re-route!");
                           this.cancel();
                           return true;
                        }
                     } else {
                        this.lastStuckSamplePos = currentPos;
                        this.ticksStationary = 0;
                     }
                  }

                  if ((double)this.ticksOnCurrent
                     > this.currentMovementOriginalCostEstimate + (double)Baritone.settings().movementTimeoutTicks.value.intValue()) {
                     this.logDebug(
                        "This movement has taken too long ("
                           + this.ticksOnCurrent
                           + " ticks, expected "
                           + this.currentMovementOriginalCostEstimate
                           + "). Cancelling."
                     );
                     this.cancel();
                     return true;
                  } else {
                     return canCancel;
                  }
               }
            }
         }
      }

   private Tuple<Double, BlockPos> closestPathPos(IPath path) {
      double best = -1.0;
      BlockPos bestPos = null;

      for (IMovement movement : path.movements()) {
         for (BlockPos pos : ((Movement)movement).getValidPositions()) {
            double dist = VecUtils.entityDistanceToCenter(this.ctx.player(), pos);
            if (dist < best || best == -1.0) {
               best = dist;
               bestPos = pos;
            }
         }
      }

      return new Tuple(best, bestPos);
   }

   private boolean shouldPause() {
      if (this.pathPosition >= this.path.movements().size()) {
         return false;
      }
      Optional<AbstractNodeCostSearch> current = this.behavior.getInProgress();
      if (!current.isPresent()) {
         return false;
      } else if (!this.ctx.player().onGround()) {
         return false;
      } else if (!MovementHelper.canWalkOn(this.ctx, this.ctx.playerFeet().below())) {
         return false;
      } else if (!MovementHelper.canWalkThrough(this.ctx, this.ctx.playerFeet()) || !MovementHelper.canWalkThrough(this.ctx, this.ctx.playerFeet().above())) {
         return false;
      } else if (!this.path.movements().get(this.pathPosition).safeToCancel()) {
         return false;
      } else {
         Optional<IPath> currentBest = current.get().bestPathSoFar();
         if (!currentBest.isPresent()) {
            return false;
         } else {
            List<BetterBlockPos> positions = currentBest.get().positions();
            if (positions.size() < 3) {
               return false;
            } else {
               positions = positions.subList(1, positions.size());
               return positions.contains(this.ctx.playerFeet());
            }
         }
      }
   }

   private boolean possiblyOffPath(Tuple<Double, BlockPos> status, double leniency) {
      double distanceFromPath = (Double)status.getA();
      if (distanceFromPath > leniency) {
         if (this.pathPosition < this.path.movements().size()
               && this.pathPosition + 1 < this.path.positions().size()
               && this.path.movements().get(this.pathPosition) instanceof MovementFall) {
            BlockPos fallDest = this.path.positions().get(this.pathPosition + 1);
            return VecUtils.entityFlatDistanceToCenter(this.ctx.player(), fallDest) >= leniency;
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   public boolean snipsnapifpossible() {
      if (!this.ctx.player().onGround() && this.ctx.world().getFluidState(this.ctx.playerFeet()).isEmpty()) {
         return false;
      } else if (this.ctx.player().getDeltaMovement().y < -0.1) {
         return false;
      } else {
         int index = this.path.positions().indexOf(this.ctx.playerFeet());
         if (index == -1) {
            return false;
         } else {
            this.pathPosition = index;
            this.clearKeys();
            return true;
         }
      }
   }

   private boolean shouldSprintNextTick() {
      boolean requested = this.behavior.baritone.getInputOverrideHandler().isInputForcedDown(Input.SPRINT);
      this.behavior.baritone.getInputOverrideHandler().setInputForceState(Input.SPRINT, false);
      if (!(new CalculationContext(this.behavior.baritone, false)).canSprint) {
         return false;
      } else if (this.pathPosition >= this.path.movements().size()) {
         return false;
      } else {
         IMovement current = this.path.movements().get(this.pathPosition);
         if (current instanceof MovementTraverse && this.pathPosition < this.path.length() - 3) {
            IMovement next = this.path.movements().get(this.pathPosition + 1);
            if (next instanceof MovementAscend
               && sprintableAscend(this.ctx, (MovementTraverse)current, (MovementAscend)next, this.path.movements().get(this.pathPosition + 2))) {
               if (skipNow(this.ctx, current)) {
                  this.logDebug("Skipping traverse to straight ascend");
                  this.pathPosition++;
                  this.onChangeInPathPosition();
                  this.onTick();
                  this.behavior.baritone.getInputOverrideHandler().setInputForceState(Input.JUMP, true);
                  return true;
               }

               this.logDebug("Too far to the side to safely sprint ascend");
            }
         }

         if (requested) {
            return true;
         } else {
            if (current instanceof MovementDescend) {
               if (this.pathPosition < this.path.length() - 2) {
                  IMovement next = this.path.movements().get(this.pathPosition + 1);
                  if (MovementHelper.canUseFrostWalker(this.ctx, next.getDest().below())
                     && (next instanceof MovementTraverse || next instanceof MovementParkour)) {
                     boolean couldPlaceInstead = Baritone.settings().allowPlace.value
                        && this.behavior.baritone.getInventoryBehavior().hasGenericThrowaway()
                        && next instanceof MovementParkour;
                     boolean sameFlatDirection = !current.getDirection().above().offset(next.getDirection()).equals(BlockPos.ZERO)
                        && current.getDirection().above().cross(next.getDirection()).equals(BlockPos.ZERO);
                     if (sameFlatDirection && !couldPlaceInstead) {
                        ((MovementDescend)current).forceSafeMode();
                     }
                  }
               }

               if (((MovementDescend)current).safeMode() && !((MovementDescend)current).skipToAscend()) {
                  this.logDebug("Sprinting would be unsafe");
                  return false;
               }

               if (this.pathPosition < this.path.length() - 2) {
                  IMovement next = this.path.movements().get(this.pathPosition + 1);
                  if (next instanceof MovementAscend && current.getDirection().above().equals(next.getDirection().below())) {
                     this.pathPosition++;
                     this.onChangeInPathPosition();
                     this.onTick();
                     this.logDebug("Skipping descend to straight ascend");
                     return true;
                  }

                  if (canSprintFromDescendInto(this.ctx, current, next)) {
                     if (next instanceof MovementDescend && this.pathPosition < this.path.length() - 3) {
                        IMovement next_next = this.path.movements().get(this.pathPosition + 2);
                        if (next_next instanceof MovementDescend && !canSprintFromDescendInto(this.ctx, next, next_next)) {
                           return false;
                        }
                     }

                     if (this.ctx.playerFeet().equals(current.getDest())) {
                        this.pathPosition++;
                        this.onChangeInPathPosition();
                        this.onTick();
                     }

                     return true;
                  }
               }
            }

            if (current instanceof MovementAscend && this.pathPosition != 0) {
               IMovement prev = this.path.movements().get(this.pathPosition - 1);
               if (prev instanceof MovementDescend && prev.getDirection().above().equals(current.getDirection().below())) {
                  BlockPos center = current.getSrc().above();
                  if (this.ctx.player().position().y >= (double)center.getY() - 0.07) {
                     this.behavior.baritone.getInputOverrideHandler().setInputForceState(Input.JUMP, false);
                     return true;
                  }
               }

               if (this.pathPosition < this.path.length() - 2
                  && prev instanceof MovementTraverse
                  && sprintableAscend(this.ctx, (MovementTraverse)prev, (MovementAscend)current, this.path.movements().get(this.pathPosition + 1))) {
                  return true;
               }
            }

            if (current instanceof MovementFall) {
               Tuple<Vec3, BlockPos> data = this.overrideFall((MovementFall)current);
               if (data != null) {
                  BetterBlockPos fallDest = new BetterBlockPos((BlockPos)data.getB());
                  if (!this.path.positions().contains(fallDest)) {
                     throw new IllegalStateException(
                        String.format("Fall override at %s %s %s returned illegal destination %s %s %s", current.getSrc(), fallDest)
                     );
                  }

                  if (this.ctx.playerFeet().equals(fallDest)) {
                     this.pathPosition = this.path.positions().indexOf(fallDest);
                     this.onChangeInPathPosition();
                     this.onTick();
                     return true;
                  }

                  this.clearKeys();
                  BetterBlockPos src = current.getSrc();
                  BetterBlockPos dest = current.getDest();
                  MovementState fakeState = new MovementState();
                  if (!MovementHelper.openDoors(this.ctx, fakeState, src, new BetterBlockPos(dest.x, src.y, dest.z))) {
                     boolean forceRotations = fakeState.getTarget().hasToForceRotations();
                     fakeState.getTarget().getRotation().ifPresent(rotation -> this.behavior.baritone.getLookBehavior().updateTarget(rotation, forceRotations));
                     fakeState.getInputStates().forEach(this.behavior.baritone.getInputOverrideHandler()::setInputForceState);
                     fakeState.getInputStates().clear();
                     return true;
                  }

                  this.behavior
                     .baritone
                     .getLookBehavior()
                     .updateTarget(RotationUtils.calcRotationFromVec3d(this.ctx.playerHead(), (Vec3)data.getA(), this.ctx.playerRotations()), false);
                  this.behavior.baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, true);
                  return true;
               }
            }

            return false;
         }
      }
   }

   private Tuple<Vec3, BlockPos> overrideFall(MovementFall movement) {
      Vec3i dir = movement.getDirection();
      if (dir.getY() < -3) {
         return null;
      } else if (!movement.toBreakCached.isEmpty()) {
         return null;
      } else {
         Vec3i flatDir = new Vec3i(dir.getX(), 0, dir.getZ());

         int i;
         label49:
         for (i = this.pathPosition + 1; i < this.path.length() - 1 && i < this.pathPosition + 3; i++) {
            IMovement next = this.path.movements().get(i);
            if (!(next instanceof MovementTraverse) || !flatDir.equals(next.getDirection())) {
               break;
            }

            for (int y = next.getDest().y; y <= movement.getSrc().y + 1; y++) {
               BlockPos chk = new BlockPos(next.getDest().x, y, next.getDest().z);
               if (!MovementHelper.fullyPassable(this.ctx, chk)) {
                  break label49;
               }
            }

            if (!MovementHelper.canWalkOn(this.ctx, next.getDest().below())) {
               break;
            }
         }

         if (--i == this.pathPosition) {
            return null;
         } else {
            double len = (double)(i - this.pathPosition) - 0.4;
            return new Tuple(
               new Vec3(
                  (double)flatDir.getX() * len + (double)movement.getDest().x + 0.5,
                  (double)movement.getDest().y,
                  (double)flatDir.getZ() * len + (double)movement.getDest().z + 0.5
               ),
               movement.getDest().offset(flatDir.getX() * (i - this.pathPosition), 0, flatDir.getZ() * (i - this.pathPosition))
            );
         }
      }
   }

   private static boolean skipNow(IPlayerContext ctx, IMovement current) {
      double offTarget = Math.abs((double)current.getDirection().getX() * ((double)current.getSrc().z + 0.5 - ctx.player().position().z))
         + Math.abs((double)current.getDirection().getZ() * ((double)current.getSrc().x + 0.5 - ctx.player().position().x));
      if (offTarget > 0.1) {
         return false;
      } else {
         BlockPos headBonk = current.getSrc().subtract(current.getDirection()).above(2);
         if (MovementHelper.fullyPassable(ctx, headBonk)) {
            return true;
         } else {
            double flatDist = Math.abs((double)current.getDirection().getX() * ((double)headBonk.getX() + 0.5 - ctx.player().position().x))
               + Math.abs((double)current.getDirection().getZ() * ((double)headBonk.getZ() + 0.5 - ctx.player().position().z));
            return flatDist > 0.8;
         }
      }
   }

   private static boolean sprintableAscend(IPlayerContext ctx, MovementTraverse current, MovementAscend next, IMovement nextnext) {
      if (!Baritone.settings().sprintAscends.value) {
         return false;
      } else if (!current.getDirection().equals(next.getDirection().below())) {
         return false;
      } else if (nextnext.getDirection().getX() == next.getDirection().getX() && nextnext.getDirection().getZ() == next.getDirection().getZ()) {
         if (!MovementHelper.canWalkOn(ctx, current.getDest().below())) {
            return false;
         } else if (!MovementHelper.canWalkOn(ctx, next.getDest().below())) {
            return false;
         } else if (!next.toBreakCached.isEmpty()) {
            return false;
         } else {
            for (int x = 0; x < 2; x++) {
               for (int y = 0; y < 3; y++) {
                  BlockPos chk = current.getSrc().above(y);
                  if (x == 1) {
                     chk = chk.offset(current.getDirection());
                  }

                  if (!MovementHelper.fullyPassable(ctx, chk)) {
                     return false;
                  }
               }
            }

            return MovementHelper.avoidWalkingInto(ctx.world().getBlockState(current.getSrc().above(3)))
               ? false
               : !MovementHelper.avoidWalkingInto(ctx.world().getBlockState(next.getDest().above(2)));
         }
      } else {
         return false;
      }
   }

   private static boolean canSprintFromDescendInto(IPlayerContext ctx, IMovement current, IMovement next) {
      if (next instanceof MovementDescend && next.getDirection().equals(current.getDirection())) {
         return true;
      } else if (!MovementHelper.canWalkOn(ctx, current.getDest().offset(current.getDirection()))) {
         return false;
      } else {
         return next instanceof MovementTraverse && next.getDirection().equals(current.getDirection())
            ? true
            : next instanceof MovementDiagonal && Baritone.settings().allowOvershootDiagonalDescend.value;
      }
   }

   private void onChangeInPathPosition() {
      this.clearKeys();
      this.ticksOnCurrent = 0;
      this.ticksStationary = 0;
      this.lastStuckSamplePos = null;
   }

   private void clearKeys() {
      this.behavior.baritone.getInputOverrideHandler().clearAllKeys();
   }

   private void cancel() {
      this.clearKeys();
      this.behavior.baritone.getInputOverrideHandler().getBlockBreakHelper().stopBreakingBlock();
      this.pathPosition = this.path.length() + 3;
      this.failed = true;
   }

   @Override
   public int getPosition() {
      return this.pathPosition;
   }

   public PathExecutor trySplice(PathExecutor next) {
      return next == null ? this.cutIfTooLong() : SplicedPath.trySplice(this.path, next.path, false).map(path -> {
         if (!path.getDest().equals(next.getPath().getDest())) {
            throw new IllegalStateException(String.format("Path has end %s instead of %s after splicing", path.getDest(), next.getPath().getDest()));
         } else {
            PathExecutor ret = new PathExecutor(this.behavior, path);
            ret.pathPosition = this.pathPosition;
            ret.currentMovementOriginalCostEstimate = this.currentMovementOriginalCostEstimate;
            ret.costEstimateIndex = this.costEstimateIndex;
            ret.ticksOnCurrent = this.ticksOnCurrent;
            return ret;
         }
      }).orElseGet(this::cutIfTooLong);
   }

   private PathExecutor cutIfTooLong() {
      if (this.pathPosition > Baritone.settings().maxPathHistoryLength.value) {
         int cutoffAmt = Baritone.settings().pathHistoryCutoffAmount.value;
         CutoffPath newPath = new CutoffPath(this.path, cutoffAmt, this.path.length() - 1);
         if (!newPath.getDest().equals(this.path.getDest())) {
            throw new IllegalStateException(String.format("Path has end %s instead of %s after trimming its start", newPath.getDest(), this.path.getDest()));
         } else {
            this.logDebug("Discarding earliest segment movements, length cut from " + this.path.length() + " to " + newPath.length());
            PathExecutor ret = new PathExecutor(this.behavior, newPath);
            ret.pathPosition = this.pathPosition - cutoffAmt;
            ret.currentMovementOriginalCostEstimate = this.currentMovementOriginalCostEstimate;
            if (this.costEstimateIndex != null) {
               ret.costEstimateIndex = this.costEstimateIndex - cutoffAmt;
            }

            ret.ticksOnCurrent = this.ticksOnCurrent;
            return ret;
         }
      } else {
         return this;
      }
   }

   @Override
   public IPath getPath() {
      return this.path;
   }

   public boolean failed() {
      return this.failed;
   }

   public boolean finished() {
      return this.pathPosition >= this.path.length();
   }

   public Set<BlockPos> toBreak() {
      return Collections.unmodifiableSet(this.toBreak);
   }

   public Set<BlockPos> toPlace() {
      return Collections.unmodifiableSet(this.toPlace);
   }

   public Set<BlockPos> toWalkInto() {
      return Collections.unmodifiableSet(this.toWalkInto);
   }

   public boolean isSprinting() {
      return this.sprintNextTick;
   }
}
