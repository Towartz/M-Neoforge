package baritone.pathing.movement;

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.pathing.movement.IMovement;
import baritone.api.pathing.movement.MovementStatus;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.RayTraceUtils;
import baritone.api.utils.VecUtils;
import baritone.api.utils.input.Input;
import baritone.behavior.PathingBehavior;
import baritone.utils.BlockStateInterface;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.phys.AABB;

public abstract class Movement implements IMovement, MovementHelper {
   public static final Direction[] HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP = new Direction[]{
      Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN
   };
   protected final IBaritone baritone;
   protected final IPlayerContext ctx;
   private MovementState currentState = new MovementState().setStatus(MovementStatus.PREPPING);
   protected final BetterBlockPos src;
   protected final BetterBlockPos dest;
   protected final BetterBlockPos[] positionsToBreak;
   protected final BetterBlockPos positionToPlace;
   private Double cost;
   public List<BlockPos> toBreakCached = null;
   public List<BlockPos> toPlaceCached = null;
   public List<BlockPos> toWalkIntoCached = null;
   private Set<BetterBlockPos> validPositionsCached = null;
   private Boolean calculatedWhileLoaded;

   protected Movement(IBaritone baritone, BetterBlockPos src, BetterBlockPos dest, BetterBlockPos[] toBreak, BetterBlockPos toPlace) {
      this.baritone = baritone;
      this.ctx = baritone.getPlayerContext();
      this.src = src;
      this.dest = dest;
      this.positionsToBreak = toBreak;
      this.positionToPlace = toPlace;
   }

   protected Movement(IBaritone baritone, BetterBlockPos src, BetterBlockPos dest, BetterBlockPos[] toBreak) {
      this(baritone, src, dest, toBreak, null);
   }

   @Override
   public double getCost() throws NullPointerException {
      return this.cost;
   }

   public double getCost(CalculationContext context) {
      if (this.cost == null) {
         this.cost = this.calculateCost(context);
      }

      return this.cost;
   }

   public abstract double calculateCost(CalculationContext var1);

   public double recalculateCost(CalculationContext context) {
      this.cost = null;
      return this.getCost(context);
   }

   public void override(double cost) {
      this.cost = cost;
   }

   protected abstract Set<BetterBlockPos> calculateValidPositions();

   public Set<BetterBlockPos> getValidPositions() {
      if (this.validPositionsCached == null) {
         this.validPositionsCached = this.calculateValidPositions();
         Objects.requireNonNull(this.validPositionsCached);
      }

      return this.validPositionsCached;
   }

   protected boolean playerInValidPosition() {
      return this.getValidPositions().contains(this.ctx.playerFeet())
         || this.getValidPositions().contains(((PathingBehavior)this.baritone.getPathingBehavior()).pathStart());
   }

   @Override
   public MovementStatus update() {
      this.ctx.player().getAbilities().flying = false;
      this.currentState = this.updateState(this.currentState);
      if (MovementHelper.isLiquid(this.ctx, this.ctx.playerFeet())) {
         double var10001 = (double)this.dest.y;
         if (this.ctx.player().position().y < var10001 + 0.6) {
            this.currentState.setInput(Input.JUMP, true);
         }
      }

      if (this.ctx.player().isInWall()) {
         this.ctx.getSelectedBlock().ifPresent(pos -> MovementHelper.switchToBestToolFor(this.ctx, BlockStateInterface.get(this.ctx, pos)));
         this.currentState.setInput(Input.CLICK_LEFT, true);
      }

      this.currentState
         .getTarget()
         .getRotation()
         .ifPresent(rotation -> this.baritone.getLookBehavior().updateTarget(rotation, this.currentState.getTarget().hasToForceRotations()));
      this.baritone.getInputOverrideHandler().clearAllKeys();
      this.currentState.getInputStates().forEach((input, forced) -> this.baritone.getInputOverrideHandler().setInputForceState(input, forced));
      this.currentState.getInputStates().clear();
      if (this.currentState.getStatus().isComplete()) {
         this.baritone.getInputOverrideHandler().clearAllKeys();
      }

      return this.currentState.getStatus();
   }

   protected int prepTicks = 0;

   protected boolean prepared(MovementState state) {
      if (state.getStatus() == MovementStatus.WAITING) {
         return true;
      } else {
         boolean isBreaking = (this.ctx.minecraft().gameMode != null && this.ctx.minecraft().gameMode.isDestroying())
            || this.baritone.getInputOverrideHandler().isInputForcedDown(Input.CLICK_LEFT)
            || this.ctx.getSelectedBlock().isPresent();
         if (isBreaking) {
            this.prepTicks = 0;
         } else {
            this.prepTicks++;
         }

         // If stuck prepping for over 100 ticks (5 seconds) without breaking obstacles, mark UNREACHABLE to trigger recalculation
         if (this.prepTicks > 100) {
            state.setStatus(MovementStatus.UNREACHABLE);
            return true;
         }

         boolean somethingInTheWay = false;

         for (BetterBlockPos blockPos : this.positionsToBreak) {
            if (!this.ctx.world().getEntitiesOfClass(FallingBlockEntity.class, new AABB(0.0, 0.0, 0.0, 1.0, 1.1, 1.0).move(blockPos)).isEmpty()
               && Baritone.settings().pauseMiningForFallingBlocks.value) {
               return false;
            }

            if (!MovementHelper.canWalkThrough(this.ctx, blockPos)) {
               somethingInTheWay = true;
               double reachDist = this.ctx.playerController().getBlockReachDistance();
               double distToBlock = this.ctx.playerHead().distanceTo(VecUtils.getBlockPosCenter(blockPos));

               // If obstacle block is out of reach, walk forward towards dest to get in range instead of freezing static
               if (distToBlock > reachDist) {
                  MovementHelper.moveTowards(this.ctx, state, this.dest);
                  return false;
               }

               MovementHelper.switchToBestToolFor(this.ctx, BlockStateInterface.get(this.ctx, blockPos));
               Optional<Rotation> reachable = RotationUtils.reachable(this.ctx, blockPos, reachDist);
               if (!reachable.isPresent()) {
                  // Block is within reach radius but direct angle is occluded by an intermediate block.
                  // Raycast from player head towards target center to find and break the obstructing block!
                  net.minecraft.world.phys.HitResult trace = RayTraceUtils.rayTraceTowards(
                     this.ctx.player(),
                     RotationUtils.calcRotationFromVec3d(this.ctx.playerHead(), VecUtils.getBlockPosCenter(blockPos), this.ctx.playerRotations()),
                     reachDist
                  );
                  if (trace != null && trace.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                     BlockPos occludingPos = ((net.minecraft.world.phys.BlockHitResult) trace).getBlockPos();
                     if (!occludingPos.equals(blockPos) && !MovementHelper.canWalkThrough(this.ctx, new BetterBlockPos(occludingPos))) {
                        MovementHelper.switchToBestToolFor(this.ctx, BlockStateInterface.get(this.ctx, occludingPos));
                        state.setTarget(new MovementState.MovementTarget(
                           RotationUtils.calcRotationFromVec3d(this.ctx.playerHead(), VecUtils.getBlockPosCenter(occludingPos), this.ctx.playerRotations()), true
                        ));
                        state.setInput(Input.CLICK_LEFT, true);
                        return false;
                     }
                  }

                  state.setTarget(
                     new MovementState.MovementTarget(
                        RotationUtils.calcRotationFromVec3d(this.ctx.playerHead(), VecUtils.getBlockPosCenter(blockPos), this.ctx.playerRotations()), true
                     )
                  );
                  state.setInput(Input.CLICK_LEFT, true);
                  if (!this.ctx.player().horizontalCollision) {
                     state.setInput(Input.MOVE_FORWARD, true);
                  }
                  return false;
               }

               Rotation rotTowardsBlock = reachable.get();
               state.setTarget(new MovementState.MovementTarget(rotTowardsBlock, true));
               if (this.ctx.isLookingAt(blockPos) || this.ctx.playerRotations().isReallyCloseTo(rotTowardsBlock)) {
                  state.setInput(Input.CLICK_LEFT, true);
               }

               return false;
            }
         }

         if (somethingInTheWay) {
            state.setStatus(MovementStatus.UNREACHABLE);
            return true;
         } else {
            return true;
         }
      }
   }

   @Override
   public boolean safeToCancel() {
      return this.safeToCancel(this.currentState);
   }

   protected boolean safeToCancel(MovementState currentState) {
      return true;
   }

   @Override
   public BetterBlockPos getSrc() {
      return this.src;
   }

   @Override
   public BetterBlockPos getDest() {
      return this.dest;
   }

   @Override
   public void reset() {
      this.currentState = new MovementState().setStatus(MovementStatus.PREPPING);
      this.prepTicks = 0;
   }

   public MovementState updateState(MovementState state) {
      if (!this.prepared(state)) {
         return state.setStatus(MovementStatus.PREPPING);
      } else {
         if (state.getStatus() == MovementStatus.PREPPING) {
            state.setStatus(MovementStatus.WAITING);
         }

         if (state.getStatus() == MovementStatus.WAITING) {
            state.setStatus(MovementStatus.RUNNING);
         }

         return state;
      }
   }

   @Override
   public BlockPos getDirection() {
      return this.getDest().subtract(this.getSrc());
   }

   public void checkLoadedChunk(CalculationContext context) {
      this.calculatedWhileLoaded = context.bsi.worldContainsLoadedChunk(this.dest.x, this.dest.z);
   }

   @Override
   public boolean calculatedWhileLoaded() {
      return this.calculatedWhileLoaded;
   }

   @Override
   public void resetBlockCache() {
      this.toBreakCached = null;
      this.toPlaceCached = null;
      this.toWalkIntoCached = null;
   }

   public List<BlockPos> toBreak(BlockStateInterface bsi) {
      if (this.toBreakCached != null) {
         return this.toBreakCached;
      } else {
         List<BlockPos> result = new ArrayList<>();

         for (BetterBlockPos positionToBreak : this.positionsToBreak) {
            if (!MovementHelper.canWalkThrough(bsi, positionToBreak.x, positionToBreak.y, positionToBreak.z)) {
               result.add(positionToBreak);
            }
         }

         this.toBreakCached = result;
         return result;
      }
   }

   public List<BlockPos> toPlace(BlockStateInterface bsi) {
      if (this.toPlaceCached != null) {
         return this.toPlaceCached;
      } else {
         List<BlockPos> result = new ArrayList<>();
         if (this.positionToPlace != null && !MovementHelper.canWalkOn(bsi, this.positionToPlace.x, this.positionToPlace.y, this.positionToPlace.z)) {
            result.add(this.positionToPlace);
         }

         this.toPlaceCached = result;
         return result;
      }
   }

   public List<BlockPos> toWalkInto(BlockStateInterface bsi) {
      if (this.toWalkIntoCached == null) {
         this.toWalkIntoCached = new ArrayList<>();
      }

      return this.toWalkIntoCached;
   }

   public BlockPos[] toBreakAll() {
      return this.positionsToBreak;
   }
}
