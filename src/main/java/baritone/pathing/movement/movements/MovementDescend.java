package baritone.pathing.movement.movements;

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.pathing.movement.MovementStatus;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.input.Input;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.Movement;
import baritone.pathing.movement.MovementHelper;
import baritone.pathing.movement.MovementState;
import baritone.utils.BlockStateInterface;
import baritone.utils.pathing.MutableMoveResult;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class MovementDescend extends Movement {
   private int numTicks = 0;
   public boolean forceSafeMode = false;

   public MovementDescend(IBaritone baritone, BetterBlockPos start, BetterBlockPos end) {
      super(baritone, start, end, new BetterBlockPos[]{end.above(2), end.above(), end}, end.below());
   }

   @Override
   public void reset() {
      super.reset();
      this.numTicks = 0;
      this.forceSafeMode = false;
   }

   public void forceSafeMode() {
      this.forceSafeMode = true;
   }

   @Override
   public double calculateCost(CalculationContext context) {
      MutableMoveResult result = new MutableMoveResult();
      cost(context, this.src.x, this.src.y, this.src.z, this.dest.x, this.dest.z, result);
      return result.y != this.dest.y ? 1000000.0 : result.cost;
   }

   @Override
   protected Set<BetterBlockPos> calculateValidPositions() {
      return ImmutableSet.of(this.src, this.dest.above(), this.dest);
   }

   public static void cost(CalculationContext context, int x, int y, int z, int destX, int destZ, MutableMoveResult res) {
      double totalCost = 0.0;
      BlockState destDown = context.get(destX, y - 1, destZ);
      totalCost += MovementHelper.getMiningDurationTicks(context, destX, y - 1, destZ, destDown, false);
      if (!(totalCost >= 1000000.0)) {
         totalCost += MovementHelper.getMiningDurationTicks(context, destX, y, destZ, false);
         if (!(totalCost >= 1000000.0)) {
            totalCost += MovementHelper.getMiningDurationTicks(context, destX, y + 1, destZ, true);
            if (!(totalCost >= 1000000.0)) {
               Block fromDown = context.get(x, y - 1, z).getBlock();
               if (!MovementHelper.isClimbable(fromDown)) {
                  BlockState below = context.get(destX, y - 2, destZ);
                  if (!MovementHelper.canWalkOn(context, destX, y - 2, destZ, below)) {
                     dynamicFallCost(context, x, y, z, destX, destZ, totalCost, below, res);
                  } else if (destDown.getBlock() != Blocks.LADDER && destDown.getBlock() != Blocks.VINE) {
                     if (!MovementHelper.canUseFrostWalker(context, destDown)) {
                        double walk = 3.7062775075283763;
                        if (fromDown == Blocks.SOUL_SAND) {
                           walk *= 2.0;
                        }

                        totalCost += walk + Math.max(FALL_N_BLOCKS_COST[1], 0.9265693768820937);
                        res.x = destX;
                        res.y = y - 1;
                        res.z = destZ;
                        res.cost = totalCost;
                     }
                  }
               }
            }
         }
      }
   }

   public static boolean dynamicFallCost(
      CalculationContext context, int x, int y, int z, int destX, int destZ, double frontBreak, BlockState below, MutableMoveResult res
   ) {
      if (frontBreak != 0.0 && context.get(destX, y + 2, destZ).getBlock() instanceof FallingBlock) {
         return false;
      } else if (!MovementHelper.canWalkThrough(context, destX, y - 2, destZ, below)) {
         return false;
      } else {
         double costSoFar = 0.0;
         int effectiveStartHeight = y;
         int fallHeight = 3;

         while (true) {
            int newY = y - fallHeight;
            if (newY < context.world.getMinBuildHeight()) {
               return false;
            }

            boolean reachedMinimum = fallHeight >= context.minFallHeight;
            BlockState ontoBlock = context.get(destX, newY, destZ);
            int unprotectedFallHeight = fallHeight - (y - effectiveStartHeight);
            double tentativeCost = 3.7062775075283763 + FALL_N_BLOCKS_COST[unprotectedFallHeight] + frontBreak + costSoFar;
            if (reachedMinimum && MovementHelper.isWater(ontoBlock)) {
               if (!MovementHelper.canWalkThrough(context, destX, newY, destZ, ontoBlock)) {
                  return false;
               }

               if (context.assumeWalkOnWater) {
                  return false;
               }

               if (MovementHelper.isFlowing(destX, newY, destZ, ontoBlock, context.bsi)) {
                  return false;
               }

               if (!MovementHelper.canWalkOn(context, destX, newY - 1, destZ)) {
                  return false;
               }

               res.x = destX;
               res.y = newY;
               res.z = destZ;
               res.cost = tentativeCost;
               return false;
            }

            if (reachedMinimum && context.allowFallIntoLava && MovementHelper.isLava(ontoBlock)) {
               res.x = destX;
               res.y = newY;
               res.z = destZ;
               res.cost = tentativeCost;
               return false;
            }

            if (unprotectedFallHeight <= 11 && MovementHelper.isClimbable(ontoBlock.getBlock())) {
               costSoFar += FALL_N_BLOCKS_COST[unprotectedFallHeight - 1];
               costSoFar += 6.666666666666667;
               effectiveStartHeight = newY;
            } else if (!MovementHelper.canWalkThrough(context, destX, newY, destZ, ontoBlock)) {
               if (!MovementHelper.canWalkOn(context, destX, newY, destZ, ontoBlock)) {
                  return false;
               }

               if (MovementHelper.isBottomSlab(ontoBlock)) {
                  return false;
               }

               if (reachedMinimum && unprotectedFallHeight <= context.maxFallHeightNoWater + 1) {
                  res.x = destX;
                  res.y = newY + 1;
                  res.z = destZ;
                  res.cost = tentativeCost;
                  return false;
               }

               if (reachedMinimum && context.hasWaterBucket && unprotectedFallHeight <= context.maxFallHeightBucket + 1) {
                  res.x = destX;
                  res.y = newY + 1;
                  res.z = destZ;
                  res.cost = tentativeCost + context.placeBucketCost();
                  return true;
               }

               return false;
            }

            fallHeight++;
         }
      }
   }

   @Override
   public MovementState updateState(MovementState state) {
      super.updateState(state);
      if (state.getStatus() != MovementStatus.RUNNING) {
         return state;
      } else {
         BlockPos playerFeet = this.ctx.playerFeet();
         BlockPos fakeDest = new BlockPos(this.dest.getX() * 2 - this.src.getX(), this.dest.getY(), this.dest.getZ() * 2 - this.src.getZ());
         if (!playerFeet.equals(this.dest) && !playerFeet.equals(fakeDest)
            || !MovementHelper.isLiquid(this.ctx, this.dest) && !(this.ctx.player().position().y - (double)this.dest.getY() < 0.5)) {
            if (!MovementHelper.openDoors(this.ctx, state, this.src, this.dest.above())) {
               return state;
            } else if (this.safeMode()) {
               double destX = ((double)this.src.getX() + 0.5) * 0.17 + ((double)this.dest.getX() + 0.5) * 0.83;
               double destZ = ((double)this.src.getZ() + 0.5) * 0.17 + ((double)this.dest.getZ() + 0.5) * 0.83;
               state.setTarget(
                     new MovementState.MovementTarget(
                        RotationUtils.calcRotationFromVec3d(this.ctx.playerHead(), new Vec3(destX, (double)this.dest.getY(), destZ), this.ctx.playerRotations())
                           .withPitch(this.ctx.playerRotations().getPitch()),
                        false
                     )
                  )
                  .setInput(Input.MOVE_FORWARD, true);
               return state;
            } else {
               double diffX = this.ctx.player().position().x - ((double)this.dest.getX() + 0.5);
               double diffZ = this.ctx.player().position().z - ((double)this.dest.getZ() + 0.5);
               double ab = Math.sqrt(diffX * diffX + diffZ * diffZ);
               double x = this.ctx.player().position().x - ((double)this.src.getX() + 0.5);
               double z = this.ctx.player().position().z - ((double)this.src.getZ() + 0.5);
               double fromStart = Math.sqrt(x * x + z * z);
               state.setInput(
                  Input.SNEAK,
                  Baritone.settings().allowWalkOnMagmaBlocks.value
                     && this.ctx.world().getBlockState(this.ctx.player().blockPosition().below()).is(Blocks.MAGMA_BLOCK)
               );
               if (!playerFeet.equals(this.dest) || ab > 0.25) {
                  if (this.numTicks++ < 20 && fromStart < 1.25) {
                     MovementHelper.moveTowards(this.ctx, state, fakeDest);
                  } else {
                     MovementHelper.moveTowards(this.ctx, state, this.dest);
                  }
               }

               return state;
            }
         } else {
            return state.setStatus(MovementStatus.SUCCESS);
         }
      }
   }

   public boolean safeMode() {
      if (this.forceSafeMode) {
         return true;
      } else {
         BlockPos into = this.dest.subtract(this.src.below()).offset(this.dest);
         if (this.skipToAscend()) {
            return true;
         } else {
            for (int y = 0; y <= 2; y++) {
               if (MovementHelper.avoidWalkingInto(BlockStateInterface.get(this.ctx, into.above(y)))) {
                  return true;
               }
            }

            return false;
         }
      }
   }

   public boolean skipToAscend() {
      BlockPos into = this.dest.subtract(this.src.below()).offset(this.dest);
      return !MovementHelper.canWalkThrough(this.ctx, new BetterBlockPos(into))
         && MovementHelper.canWalkThrough(this.ctx, new BetterBlockPos(into).above())
         && MovementHelper.canWalkThrough(this.ctx, new BetterBlockPos(into).above(2));
   }
}
