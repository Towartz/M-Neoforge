package baritone.pathing.movement.movements;

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.pathing.movement.MovementStatus;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.input.Input;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.Movement;
import baritone.pathing.movement.MovementHelper;
import baritone.pathing.movement.MovementState;
import baritone.utils.BlockStateInterface;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

public class MovementAscend extends Movement {
   private int ticksWithoutPlacement = 0;

   public MovementAscend(IBaritone baritone, BetterBlockPos src, BetterBlockPos dest) {
      super(baritone, src, dest, new BetterBlockPos[]{dest, src.above(2), dest.above()}, dest.below());
   }

   @Override
   public void reset() {
      super.reset();
      this.ticksWithoutPlacement = 0;
   }

   @Override
   public double calculateCost(CalculationContext context) {
      return cost(context, this.src.x, this.src.y, this.src.z, this.dest.x, this.dest.z);
   }

   @Override
   protected Set<BetterBlockPos> calculateValidPositions() {
      BetterBlockPos prior = new BetterBlockPos(this.src.subtract(this.getDirection()).above());
      BetterBlockPos overshoot = new BetterBlockPos(this.dest.offset(this.getDirection()));
      return ImmutableSet.of(this.src, this.src.above(), this.dest, this.dest.above(), prior, prior.above(), overshoot);
   }

   public static double cost(CalculationContext context, int x, int y, int z, int destX, int destZ) {
      BlockState toPlace = context.get(destX, y, destZ);
      double additionalPlacementCost = 0.0;
      if (!MovementHelper.canWalkOn(context, destX, y, destZ, toPlace)) {
         additionalPlacementCost = context.costOfPlacingAt(destX, y, destZ, toPlace);
         if (additionalPlacementCost >= 1000000.0) {
            return 1000000.0;
         }

         if (!MovementHelper.isReplaceable(destX, y, destZ, toPlace, context.bsi)) {
            return 1000000.0;
         }

         boolean foundPlaceOption = false;

         for (int i = 0; i < 5; i++) {
            int againstX = destX + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i].getStepX();
            int againstY = y + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i].getStepY();
            int againstZ = destZ + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i].getStepZ();
            if ((againstX != x || againstZ != z) && MovementHelper.canPlaceAgainst(context.bsi, againstX, againstY, againstZ)) {
               foundPlaceOption = true;
               break;
            }
         }

         if (!foundPlaceOption) {
            return 1000000.0;
         }
      }

      BlockState srcUp2 = context.get(x, y + 2, z);
      if (!(context.get(x, y + 3, z).getBlock() instanceof FallingBlock)
         || !MovementHelper.canWalkThrough(context, x, y + 1, z) && srcUp2.getBlock() instanceof FallingBlock) {
         BlockState srcDown = context.get(x, y - 1, z);
         if (MovementHelper.isClimbable(srcDown.getBlock())) {
            return 1000000.0;
         } else {
            boolean jumpingFromBottomSlab = MovementHelper.isBottomSlab(srcDown);
            boolean jumpingToBottomSlab = MovementHelper.isBottomSlab(toPlace);
            if (jumpingFromBottomSlab && !jumpingToBottomSlab) {
               return 1000000.0;
            } else {
               double walk;
               if (context.assumeStep) {
                  walk = 4.63284688441047;
               } else if (jumpingToBottomSlab) {
                  if (jumpingFromBottomSlab) {
                     walk = Math.max(JUMP_ONE_BLOCK_COST, 4.63284688441047);
                     walk += context.jumpPenalty;
                  } else {
                     walk = 4.63284688441047;
                  }
               } else {
                  if (toPlace.is(Blocks.SOUL_SAND)) {
                     walk = 9.26569376882094;
                  } else if (toPlace.is(Blocks.MAGMA_BLOCK)) {
                     walk = 15.384615384615383;
                  } else {
                     walk = Math.max(JUMP_ONE_BLOCK_COST, 4.63284688441047);
                  }

                  walk += context.jumpPenalty;
               }

               double totalCost = walk + additionalPlacementCost;
               totalCost += MovementHelper.getMiningDurationTicks(context, x, y + 2, z, srcUp2, false);
               if (totalCost >= 1000000.0) {
                  return 1000000.0;
               } else {
                  totalCost += MovementHelper.getMiningDurationTicks(context, destX, y + 1, destZ, false);
                  return totalCost >= 1000000.0 ? 1000000.0 : totalCost + MovementHelper.getMiningDurationTicks(context, destX, y + 2, destZ, true);
               }
            }
         }
      } else {
         return 1000000.0;
      }
   }

   @Override
   public MovementState updateState(MovementState state) {
      if (this.ctx.player().position().y < (double) this.src.y - 0.5) {
         return state.setStatus(MovementStatus.UNREACHABLE);
      } else {
         super.updateState(state);
         if (state.getStatus() != MovementStatus.RUNNING) {
            return state;
         } else if (this.ctx.playerFeet().equals(this.dest)
               || this.ctx.playerFeet().equals(this.dest.offset(this.getDirection().below()))
               || (this.ctx.playerFeet().getY() >= this.dest.getY()
                   && (this.ctx.playerFeet().equals(this.dest.offset(this.getDirection()))
                       || MovementHelper.hasArrivedHorizontally(this.ctx, this.dest, 0.2)
                       || MovementHelper.isCrossingDestination(this.ctx, this.dest)))) {
            return state.setStatus(MovementStatus.SUCCESS);
         } else if (!MovementHelper.openDoors(this.ctx, state, this.src.above(), this.dest)) {
            return state;
         } else {
            BlockState jumpingOnto = BlockStateInterface.get(this.ctx, this.positionToPlace);
            if (!MovementHelper.canWalkOn(this.ctx, this.positionToPlace, jumpingOnto)) {
               this.ticksWithoutPlacement++;
               if (MovementHelper.attemptToPlaceABlock(state, this.baritone, this.dest.below(), false, true) == MovementHelper.PlaceResult.READY_TO_PLACE) {
                  state.setInput(Input.SNEAK, true);
                  if (this.ctx.player().isCrouching()) {
                     state.setInput(Input.CLICK_RIGHT, true);
                  }
               }

               if (this.ticksWithoutPlacement > 10) {
                  state.setInput(Input.MOVE_BACK, true);
               }

               return state;
            } else {
               MovementHelper.moveTowards(this.ctx, state, this.dest);
               state.setInput(Input.SPRINT, true);
               state.setInput(Input.SNEAK, Baritone.settings().allowWalkOnMagmaBlocks.value && jumpingOnto.is(Blocks.MAGMA_BLOCK));
               if (MovementHelper.isBottomSlab(jumpingOnto) && !MovementHelper.isBottomSlab(BlockStateInterface.get(this.ctx, this.src.below()))) {
                  return state;
               } else if (!MovementHelper.canAutoStep(this.ctx, 1.0) && !this.ctx.playerFeet().equals(this.src.above())) {
                  int xAxis = Math.abs(this.src.getX() - this.dest.getX());
                  int zAxis = Math.abs(this.src.getZ() - this.dest.getZ());
                  double flatDistToNext = (double)xAxis * Math.abs((double)this.dest.getX() + 0.5 - this.ctx.player().position().x)
                     + (double)zAxis * Math.abs((double)this.dest.getZ() + 0.5 - this.ctx.player().position().z);
                  double sideDist = (double)zAxis * Math.abs((double)this.dest.getX() + 0.5 - this.ctx.player().position().x)
                     + (double)xAxis * Math.abs((double)this.dest.getZ() + 0.5 - this.ctx.player().position().z);
                  double lateralMotion = (double)xAxis * this.ctx.player().getDeltaMovement().z + (double)zAxis * this.ctx.player().getDeltaMovement().x;
                  if (this.headBonkClear()) {
                     if (Math.abs(lateralMotion) <= 0.25 || this.ctx.player().horizontalCollision || flatDistToNext <= 1.4) {
                        return state.setInput(Input.JUMP, true);
                     }
                  } else {
                     if (flatDistToNext <= 1.35 && sideDist <= 0.5 && Math.abs(lateralMotion) <= 0.25) {
                        return state.setInput(Input.JUMP, true);
                     }
                  }

                  if (this.ctx.player().horizontalCollision && this.ctx.player().onGround()) {
                     return state.setInput(Input.JUMP, true);
                  }

                  return state;
               } else {
                  return state;
               }
            }
         }
      }
   }

   public boolean headBonkClear() {
      double effectiveJump = MovementHelper.getEffectiveJumpHeight(this.ctx);
      int maxExtraY = (int) Math.ceil(effectiveJump);
      if (maxExtraY < 2) maxExtraY = 2;
      for (int dy = 2; dy <= maxExtraY; dy++) {
         BetterBlockPos up = this.src.above(dy);
         if (!MovementHelper.canWalkThrough(this.ctx, up)) {
            return false;
         }
      }
      if (!MovementHelper.canWalkThrough(this.ctx, this.dest.above())) {
         return false;
      }
      return true;
   }

   @Override
   public boolean safeToCancel(MovementState state) {
      return state.getStatus() != MovementStatus.RUNNING || this.ticksWithoutPlacement == 0;
   }
}
