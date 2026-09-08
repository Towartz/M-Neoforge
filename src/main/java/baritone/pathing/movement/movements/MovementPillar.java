package baritone.pathing.movement.movements;

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.pathing.movement.MovementStatus;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.VecUtils;
import baritone.api.utils.input.Input;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.Movement;
import baritone.pathing.movement.MovementHelper;
import baritone.pathing.movement.MovementState;
import baritone.utils.BlockStateInterface;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

public class MovementPillar extends Movement {
   public MovementPillar(IBaritone baritone, BetterBlockPos start, BetterBlockPos end) {
      super(baritone, start, end, new BetterBlockPos[]{start.above(2)}, start);
   }

   @Override
   public double calculateCost(CalculationContext context) {
      return cost(context, this.src.x, this.src.y, this.src.z);
   }

   @Override
   protected Set<BetterBlockPos> calculateValidPositions() {
      return ImmutableSet.of(this.src, this.dest);
   }

   public static double cost(CalculationContext context, int x, int y, int z) {
      BlockState fromState = context.get(x, y, z);
      Block from = fromState.getBlock();
      boolean ladder = MovementHelper.isClimbable(from);
      BlockState fromDown = context.get(x, y - 1, z);
      if (!ladder) {
         if (MovementHelper.isClimbable(fromDown.getBlock())) {
            return 1000000.0;
         }

         if (fromDown.getBlock() instanceof SlabBlock && fromDown.getValue(SlabBlock.TYPE) == SlabType.BOTTOM) {
            return 1000000.0;
         }
      }

      BlockState toBreak = context.get(x, y + 2, z);
      Block toBreakBlock = toBreak.getBlock();
      if (toBreakBlock instanceof FenceGateBlock) {
         return 1000000.0;
      } else {
         BlockState destState = context.get(x, y + 1, z);
         if (MovementHelper.isLava(destState) || MovementHelper.isLava(toBreak)
            || destState.getBlock() instanceof BaseFireBlock || toBreak.getBlock() instanceof BaseFireBlock
            || destState.is(BlockTags.FIRE) || toBreak.is(BlockTags.FIRE)) {
            return 1000000.0;
         }

         BlockState aboveHead = context.get(x, y + 3, z);
         if (MovementHelper.isLava(aboveHead)) {
            return 1000000.0;
         }

         BlockState srcUp = null;
         if (MovementHelper.isWater(toBreak) && MovementHelper.isWater(fromState)) {
            srcUp = context.get(x, y + 1, z);
            if (MovementHelper.isWater(srcUp)) {
               return 8.51063829787234;
            }
         }

         double placeCost = 0.0;
         if (!ladder) {
            placeCost = context.costOfPlacingAt(x, y, z, fromState);
            if (placeCost >= 1000000.0) {
               return 1000000.0;
            }

            if (fromDown.getBlock() instanceof AirBlock) {
               placeCost += 0.1;
            }
         }

         if ((!MovementHelper.isLiquid(fromState) || MovementHelper.canPlaceAgainst(context.bsi, x, y - 1, z, fromDown))
            && (!MovementHelper.isLiquid(fromDown) || !context.assumeWalkOnWater)) {
            if ((from == Blocks.LILY_PAD || from instanceof CarpetBlock) && !fromDown.getFluidState().isEmpty()) {
               return 1000000.0;
            } else {
               double hardness = MovementHelper.getMiningDurationTicks(context, x, y + 2, z, toBreak, true);
               if (hardness >= 1000000.0) {
                  return 1000000.0;
               } else {
                  if (hardness != 0.0) {
                     if (MovementHelper.isClimbable(toBreakBlock)) {
                        hardness = 0.0;
                     } else {
                        if (MovementHelper.avoidBreaking(context.bsi, x, y + 2, z, toBreak)) {
                           return 1000000.0;
                        }

                        BlockState check = context.get(x, y + 3, z);
                        if (check.getBlock() instanceof FallingBlock) {
                           if (srcUp == null) {
                              srcUp = context.get(x, y + 1, z);
                           }

                           if (!(toBreakBlock instanceof FallingBlock) || !(srcUp.getBlock() instanceof FallingBlock)) {
                              return 1000000.0;
                           }
                        }
                     }
                  }

                  return ladder ? 8.51063829787234 + hardness * 5.0 : JUMP_ONE_BLOCK_COST + placeCost + context.jumpPenalty + hardness;
               }
            }
         } else {
            return 1000000.0;
         }
      }
   }

   @Override
   public MovementState updateState(MovementState state) {
      super.updateState(state);
      if (state.getStatus() != MovementStatus.RUNNING) {
         return state;
      } else if (this.ctx.playerFeet().y < this.src.y) {
         return state.setStatus(MovementStatus.UNREACHABLE);
      } else {
         BlockState fromDown = BlockStateInterface.get(this.ctx, this.src);
         if (MovementHelper.isWater(fromDown) && MovementHelper.isWater(this.ctx, this.dest)) {
            state.setTarget(
               new MovementState.MovementTarget(
                  RotationUtils.calcRotationFromVec3d(this.ctx.playerHead(), VecUtils.getBlockPosCenter(this.dest), this.ctx.playerRotations()), false
               )
            );
            Vec3 destCenter = VecUtils.getBlockPosCenter(this.dest);
            if (Math.abs(this.ctx.player().position().x - destCenter.x) > 0.2 || Math.abs(this.ctx.player().position().z - destCenter.z) > 0.2) {
               state.setInput(Input.MOVE_FORWARD, true);
            }

            return this.ctx.playerFeet().equals(this.dest) ? state.setStatus(MovementStatus.SUCCESS) : state;
         } else {
            // Hazard checks for non-water pillar
            BlockState destState = BlockStateInterface.get(this.ctx, this.dest);
            BlockState destAbove = BlockStateInterface.get(this.ctx, this.dest.above());
            if (MovementHelper.isLava(destState) || MovementHelper.isLava(destAbove)
               || destState.getBlock() instanceof BaseFireBlock || destAbove.getBlock() instanceof BaseFireBlock
               || destState.is(BlockTags.FIRE) || destAbove.is(BlockTags.FIRE)
               || (!destState.getFluidState().isEmpty() && !MovementHelper.isWater(destState))
               || (!destAbove.getFluidState().isEmpty() && !MovementHelper.isWater(destAbove))) {
               return state.setStatus(MovementStatus.UNREACHABLE);
            }

            // Check overhead lava or falling hazards directly above head
            BlockState destAbove2 = BlockStateInterface.get(this.ctx, this.dest.above(2));
            if (MovementHelper.isLava(destAbove2)) {
               return state.setStatus(MovementStatus.UNREACHABLE);
            }
            if (!MovementHelper.canWalkThrough(this.ctx, this.dest.above())) {
               if (MovementHelper.avoidBreaking(this.ctx, this.dest.above())) {
                  return state.setStatus(MovementStatus.UNREACHABLE);
               }
            }

            boolean ladder = MovementHelper.isClimbable(fromDown.getBlock());
            Rotation rotation = RotationUtils.calcRotationFromVec3d(
               this.ctx.playerHead(), VecUtils.getBlockPosCenter(this.positionToPlace), this.ctx.playerRotations()
            );

            boolean blockIsThere = MovementHelper.canWalkOn(this.ctx, this.src) || ladder;
            if (ladder) {
               if (this.ctx.playerFeet().equals(this.dest)) {
                  return state.setStatus(MovementStatus.SUCCESS);
               } else {
                  MovementHelper.moveTowards(this.ctx, state, this.dest);
                  state.setInput(Input.JUMP, true);
                  return state;
               }
            } else if (!((Baritone)this.baritone).getInventoryBehavior().selectThrowawayForLocation(true, this.src.x, this.src.y, this.src.z)) {
               return state.setStatus(MovementStatus.UNREACHABLE);
            } else {
               double diffX = this.ctx.player().position().x - ((double)this.dest.getX() + 0.5);
               double diffZ = this.ctx.player().position().z - ((double)this.dest.getZ() + 0.5);
               double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);
               double flatMotion = Math.sqrt(
                  this.ctx.player().getDeltaMovement().x * this.ctx.player().getDeltaMovement().x
                     + this.ctx.player().getDeltaMovement().z * this.ctx.player().getDeltaMovement().z
               );
               if (dist > 0.17) {
                  state.setInput(Input.MOVE_FORWARD, true);
                  state.setTarget(new MovementState.MovementTarget(rotation, true));
               } else {
                  state.setInput(Input.SNEAK, true);
                  state.setTarget(new MovementState.MovementTarget(new Rotation(this.ctx.playerRotations().getYaw(), 90.0F), true));
                  if (flatMotion < 0.05) {
                     state.setInput(Input.JUMP, this.ctx.player().position().y < (double)this.dest.getY() + 0.1);
                  }
               }

               if (!blockIsThere) {
                  BlockState frState = BlockStateInterface.get(this.ctx, this.src);
                  Block fr = frState.getBlock();
                  if (!(fr instanceof AirBlock) && !frState.canBeReplaced()) {
                     RotationUtils.reachable(this.ctx, this.src, this.ctx.playerController().getBlockReachDistance())
                        .map(rot -> new MovementState.MovementTarget(rot, true))
                        .ifPresent(state::setTarget);
                     state.setInput(Input.JUMP, false);
                     state.setInput(Input.CLICK_LEFT, true);
                     blockIsThere = false;
                  } else if (this.ctx.player().position().y >= (double)this.dest.getY() - 0.05 && dist <= 0.25) {
                     state.setTarget(new MovementState.MovementTarget(new Rotation(this.ctx.playerRotations().getYaw(), 90.0F), true));
                     state.setInput(Input.CLICK_RIGHT, true);
                  }
               }

               boolean landed = this.ctx.player().onGround() || Math.abs(this.ctx.player().position().y - (double)this.dest.getY()) < 0.15;
               return this.ctx.playerFeet().equals(this.dest) && blockIsThere && landed ? state.setStatus(MovementStatus.SUCCESS) : state;
            }
         }
      }
   }

   @Override
   protected boolean prepared(MovementState state) {
      if (this.ctx.playerFeet().equals(this.src) || this.ctx.playerFeet().equals(this.src.below())) {
         Block block = BlockStateInterface.getBlock(this.ctx, this.src.below());
         if (MovementHelper.isClimbable(block)) {
            state.setInput(Input.SNEAK, true);
         }
      }

      return MovementHelper.isWater(this.ctx, this.dest.above()) ? true : super.prepared(state);
   }
}
