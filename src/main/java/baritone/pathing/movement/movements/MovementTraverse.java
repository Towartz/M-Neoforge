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
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

public class MovementTraverse extends Movement {
   private boolean wasTheBridgeBlockAlwaysThere = true;

   public MovementTraverse(IBaritone baritone, BetterBlockPos from, BetterBlockPos to) {
      super(baritone, from, to, new BetterBlockPos[]{to.above(), to}, to.below());
   }

   @Override
   public void reset() {
      super.reset();
      this.wasTheBridgeBlockAlwaysThere = true;
   }

   @Override
   public double calculateCost(CalculationContext context) {
      return cost(context, this.src.x, this.src.y, this.src.z, this.dest.x, this.dest.z);
   }

   @Override
   protected Set<BetterBlockPos> calculateValidPositions() {
      BetterBlockPos overshoot = new BetterBlockPos(this.dest.offset(this.getDirection()));
      return ImmutableSet.of(
         this.src, this.dest,
         this.src.above(), this.dest.above(),
         overshoot, overshoot.above()
      );
   }

   public static double cost(CalculationContext context, int x, int y, int z, int destX, int destZ) {
      BlockState pb0 = context.get(destX, y + 1, destZ);
      BlockState pb1 = context.get(destX, y, destZ);
      BlockState destOn = context.get(destX, y - 1, destZ);
      BlockState srcDown = context.get(x, y - 1, z);
      Block srcDownBlock = srcDown.getBlock();
      boolean standingOnABlock = MovementHelper.mustBeSolidToWalkOn(context, x, y - 1, z, srcDown);
      boolean frostWalker = standingOnABlock && !context.assumeWalkOnWater && MovementHelper.canUseFrostWalker(context, destOn);
      if (!frostWalker && !MovementHelper.canWalkOn(context, destX, y - 1, destZ, destOn)) {
         if (MovementHelper.isClimbable(srcDownBlock)) {
            return 1000000.0;
         } else if (!MovementHelper.isReplaceable(destX, y - 1, destZ, destOn, context.bsi)) {
            return 1000000.0;
         } else {
            boolean throughWater = MovementHelper.isWater(pb0) || MovementHelper.isWater(pb1);
            if (MovementHelper.isWater(destOn) && throughWater) {
               return 1000000.0;
            } else {
               double placeCost = context.costOfPlacingAt(destX, y - 1, destZ, destOn);
               if (placeCost >= 1000000.0) {
                  return 1000000.0;
               } else {
                  double hardness1 = MovementHelper.getMiningDurationTicks(context, destX, y, destZ, pb1, false);
                  if (hardness1 >= 1000000.0) {
                     return 1000000.0;
                  } else {
                     double hardness2 = MovementHelper.getMiningDurationTicks(context, destX, y + 1, destZ, pb0, true);
                     double WC = throughWater ? context.waterWalkSpeed : 4.63284688441047;

                     for (int i = 0; i < 5; i++) {
                        int againstX = destX + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i].getStepX();
                        int againstY = y - 1 + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i].getStepY();
                        int againstZ = destZ + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i].getStepZ();
                        if ((againstX != x || againstZ != z) && MovementHelper.canPlaceAgainst(context.bsi, againstX, againstY, againstZ)) {
                           return WC + placeCost + hardness1 + hardness2;
                        }
                     }

                     if (srcDownBlock == Blocks.SOUL_SAND || srcDownBlock instanceof SlabBlock && srcDown.getValue(SlabBlock.TYPE) != SlabType.DOUBLE) {
                        return 1000000.0;
                     } else if (!standingOnABlock) {
                        return 1000000.0;
                     } else {
                        Block blockSrc = context.getBlock(x, y, z);
                        if ((blockSrc == Blocks.LILY_PAD || blockSrc instanceof CarpetBlock) && !srcDown.getFluidState().isEmpty()) {
                           return 1000000.0;
                        } else {
                           WC *= 3.3207692307692307;
                           return WC + placeCost + hardness1 + hardness2;
                        }
                     }
                  }
               }
            }
         }
      } else {
         double WC = 4.63284688441047;
         boolean water = false;
         boolean sneaking = false;
         if (!MovementHelper.isWater(pb0) && !MovementHelper.isWater(pb1)) {
            if (destOn.getBlock() == Blocks.SOUL_SAND) {
               WC += 2.316423442205235;
            } else if (!frostWalker && destOn.getBlock() == Blocks.WATER) {
               WC += context.walkOnWaterOnePenalty;
            }

            if (srcDownBlock == Blocks.SOUL_SAND) {
               WC += 2.316423442205235;
            } else if (context.allowWalkOnMagmaBlocks && srcDownBlock.equals(Blocks.MAGMA_BLOCK)) {
               sneaking = true;
               WC += 5.375884250102457;
            }
         } else {
            WC = context.waterWalkSpeed;
            water = true;
         }

         double hardness1 = MovementHelper.getMiningDurationTicks(context, destX, y, destZ, pb1, false);
         if (hardness1 >= 1000000.0) {
            return 1000000.0;
         } else {
            double hardness2 = MovementHelper.getMiningDurationTicks(context, destX, y + 1, destZ, pb0, true);
            if (hardness1 == 0.0 && hardness2 == 0.0) {
               if (!water && !sneaking && context.canSprint) {
                  WC *= 0.7692444761225944;
               }

               return WC;
            } else {
               if (MovementHelper.isClimbable(srcDownBlock)) {
                  hardness1 *= 5.0;
                  hardness2 *= 5.0;
               }

               return WC + hardness1 + hardness2;
            }
         }
      }
   }

   @Override
   public MovementState updateState(MovementState state) {
      super.updateState(state);
      BlockState pb0 = BlockStateInterface.get(this.ctx, this.positionsToBreak[0]);
      BlockState pb1 = BlockStateInterface.get(this.ctx, this.positionsToBreak[1]);
      if (state.getStatus() != MovementStatus.RUNNING) {
         if (!Baritone.settings().walkWhileBreaking.value) {
            return state;
         } else if (state.getStatus() != MovementStatus.PREPPING) {
            return state;
         } else if (MovementHelper.avoidWalkingInto(pb0)) {
            return state;
         } else if (MovementHelper.avoidWalkingInto(pb1)) {
            return state;
         } else {
            double dist = Math.max(
               Math.abs(this.ctx.player().position().x - ((double)this.dest.getX() + 0.5)),
               Math.abs(this.ctx.player().position().z - ((double)this.dest.getZ() + 0.5))
            );
            if (dist < 0.83) {
               return state;
            } else if (!state.getTarget().getRotation().isPresent()) {
               return state;
            } else {
               float yawToDest = RotationUtils.calcRotationFromVec3d(
                     this.ctx.playerHead(), VecUtils.calculateBlockCenter(this.ctx.world(), this.dest), this.ctx.playerRotations()
                  )
                  .getYaw();
               float pitchToBreak = state.getTarget().getRotation().get().getPitch();
               if (MovementHelper.isBlockNormalCube(pb0)
                  || pb0.getBlock() instanceof AirBlock && (MovementHelper.isBlockNormalCube(pb1) || pb1.getBlock() instanceof AirBlock)) {
                  pitchToBreak = 26.0F;
               }

               return state.setTarget(new MovementState.MovementTarget(new Rotation(yawToDest, pitchToBreak), true))
                  .setInput(Input.MOVE_FORWARD, true)
                  .setInput(Input.SPRINT, true);
            }
         }
      } else {
         Block fd = BlockStateInterface.get(this.ctx, this.src.below()).getBlock();
         boolean ladder = MovementHelper.isClimbable(fd);
         state.setInput(
            Input.SNEAK,
            Baritone.settings().allowWalkOnMagmaBlocks.value
               && MovementHelper.steppingOnBlocks(this.ctx).stream().anyMatch(block -> this.ctx.world().getBlockState(block).is(Blocks.MAGMA_BLOCK))
         );
         if (!MovementHelper.openDoors(this.ctx, state, this.src, this.dest)) {
            return state;
         } else {
            if (pb0.getBlock() instanceof FenceGateBlock || pb1.getBlock() instanceof FenceGateBlock) {
               BlockPos blocked = !MovementHelper.isGatePassable(this.ctx, this.positionsToBreak[0], this.src.above())
                  ? this.positionsToBreak[0]
                  : (!MovementHelper.isGatePassable(this.ctx, this.positionsToBreak[1], this.src) ? this.positionsToBreak[1] : null);
               if (blocked != null) {
                  Optional<Rotation> rotation = RotationUtils.reachable(this.ctx, blocked);
                  if (rotation.isPresent()) {
                     return state.setTarget(new MovementState.MovementTarget(rotation.get(), true)).setInput(Input.CLICK_RIGHT, true);
                  }
               }
            }

            boolean isTheBridgeBlockThere = MovementHelper.canWalkOn(this.ctx, this.positionToPlace)
               || ladder
               || MovementHelper.canUseFrostWalker(this.ctx, this.positionToPlace);
            BlockPos feet = this.ctx.playerFeet();
            if (feet.getY() != this.dest.getY() && !ladder) {
               double effectiveJump = MovementHelper.getEffectiveJumpHeight(this.ctx);
               int maxAirY = this.dest.getY() + Math.max(2, (int) Math.ceil(effectiveJump));
               if (feet.getY() > this.dest.getY() && feet.getY() <= maxAirY) {
                  if (MovementHelper.hasArrivedHorizontally(this.ctx, this.dest, 0.25)
                     || MovementHelper.isCrossingDestination(this.ctx, this.dest)) {
                     return state.setStatus(MovementStatus.SUCCESS);
                  }
                  MovementHelper.moveTowards(this.ctx, state, this.dest);
                  if (this.wasTheBridgeBlockAlwaysThere) {
                     state.setInput(Input.SPRINT, true);
                  }
                  return state;
               }
               this.logDebug("Wrong Y coordinate");
               MovementHelper.moveTowards(this.ctx, state, this.dest);
               if (feet.getY() < this.dest.getY()) {
                  return state.setInput(Input.JUMP, true);
               } else {
                  return state;
               }
            } else if (isTheBridgeBlockThere) {
               if (feet.equals(this.dest)
                  || MovementHelper.hasArrivedHorizontally(this.ctx, this.dest, 0.25)
                  || MovementHelper.isCrossingDestination(this.ctx, this.dest)) {
                  return state.setStatus(MovementStatus.SUCCESS);
               } else if (!Baritone.settings().overshootTraverse.value
                  || !feet.equals(this.dest.offset(this.getDirection())) && !feet.equals(this.dest.offset(this.getDirection()).offset(this.getDirection()))) {
                  Block low = BlockStateInterface.get(this.ctx, this.src).getBlock();
                  Block high = BlockStateInterface.get(this.ctx, this.src.above()).getBlock();
                  double var10001 = (double)this.src.y;
                  if (!(this.ctx.player().position().y > var10001 + 0.1)
                     || this.ctx.player().onGround()
                     || !MovementHelper.isClimbable(low) && !MovementHelper.isClimbable(high)) {
                     BlockPos into = this.dest.subtract(this.src).offset(this.dest);
                     BlockState intoBelow = BlockStateInterface.get(this.ctx, into);
                     BlockState intoAbove = BlockStateInterface.get(this.ctx, into.above());
                     if (this.wasTheBridgeBlockAlwaysThere
                        && (!MovementHelper.isLiquid(this.ctx, feet) || Baritone.settings().sprintInWater.value)
                        && (!MovementHelper.avoidWalkingInto(intoBelow) || MovementHelper.isWater(intoBelow))
                        && !MovementHelper.avoidWalkingInto(intoAbove)) {
                        state.setInput(Input.SPRINT, true);
                     }

                     BlockState destDown = BlockStateInterface.get(this.ctx, this.dest.below());
                     if (feet.getY() != this.dest.getY() && ladder && MovementHelper.isClimbable(destDown.getBlock())) {
                        state.setInput(Input.JUMP, true);
                     }

                     MovementHelper.moveTowards(this.ctx, state, this.dest);
                     return state;
                  } else {
                     return state;
                  }
               } else {
                  return state.setStatus(MovementStatus.SUCCESS);
               }
            } else {
               this.wasTheBridgeBlockAlwaysThere = false;
               Block standingOn = BlockStateInterface.get(this.ctx, feet.below()).getBlock();
               if (standingOn.equals(Blocks.SOUL_SAND) || standingOn instanceof SlabBlock) {
                  double dist = Math.max(
                     Math.abs((double)this.dest.getX() + 0.5 - this.ctx.player().position().x),
                     Math.abs((double)this.dest.getZ() + 0.5 - this.ctx.player().position().z)
                  );
                  if (dist < 0.85) {
                     MovementHelper.moveTowards(this.ctx, state, this.dest);
                     return state.setInput(Input.MOVE_FORWARD, false).setInput(Input.MOVE_BACK, true);
                  }
               }

               double dist1 = Math.max(
                  Math.abs(this.ctx.player().position().x - ((double)this.dest.getX() + 0.5)),
                  Math.abs(this.ctx.player().position().z - ((double)this.dest.getZ() + 0.5))
               );
               MovementHelper.PlaceResult p = MovementHelper.attemptToPlaceABlock(
                  state, this.baritone, this.dest.below(), false, !Baritone.settings().assumeSafeWalk.value
               );
               if ((p == MovementHelper.PlaceResult.READY_TO_PLACE || dist1 < 0.6) && !Baritone.settings().assumeSafeWalk.value) {
                  state.setInput(Input.SNEAK, true);
               }

               switch (p) {
                  case READY_TO_PLACE:
                     if (this.ctx.player().isCrouching() || Baritone.settings().assumeSafeWalk.value) {
                        state.setInput(Input.CLICK_RIGHT, true);
                     }

                     return state;
                  case ATTEMPTING:
                     if (dist1 > 0.83) {
                        float yaw = RotationUtils.calcRotationFromVec3d(
                              this.ctx.playerHead(), VecUtils.getBlockPosCenter(this.dest), this.ctx.playerRotations()
                           )
                           .getYaw();
                        if ((double)Math.abs(state.getTarget().rotation.getYaw() - yaw) < 0.1) {
                           return state.setInput(Input.MOVE_FORWARD, true);
                        }
                     } else if (this.ctx.playerRotations().isReallyCloseTo(state.getTarget().rotation)) {
                        return state.setInput(Input.CLICK_LEFT, true);
                     }

                     return state;
                  default:
                     if (feet.equals(this.dest)) {
                        double faceX = ((double)(this.dest.getX() + this.src.getX()) + 1.0) * 0.5;
                        double faceY = ((double)(this.dest.getY() + this.src.getY()) - 1.0) * 0.5;
                        double faceZ = ((double)(this.dest.getZ() + this.src.getZ()) + 1.0) * 0.5;
                        BlockPos goalLook = this.src.below();
                        Rotation backToFace = RotationUtils.calcRotationFromVec3d(
                           this.ctx.playerHead(), new Vec3(faceX, faceY, faceZ), this.ctx.playerRotations()
                        );
                        float pitch = backToFace.getPitch();
                        double dist2 = Math.max(Math.abs(this.ctx.player().position().x - faceX), Math.abs(this.ctx.player().position().z - faceZ));
                        if (dist2 < 0.29) {
                           float yaw = RotationUtils.calcRotationFromVec3d(
                                 VecUtils.getBlockPosCenter(this.dest), this.ctx.playerHead(), this.ctx.playerRotations()
                              )
                              .getYaw();
                           state.setTarget(new MovementState.MovementTarget(new Rotation(yaw, pitch), true));
                           state.setInput(Input.MOVE_BACK, true);
                        } else {
                           state.setTarget(new MovementState.MovementTarget(backToFace, true));
                        }

                        if (this.ctx.isLookingAt(goalLook)) {
                           return state.setInput(Input.CLICK_RIGHT, true);
                        } else {
                           if (this.ctx.playerRotations().isReallyCloseTo(state.getTarget().rotation)) {
                              state.setInput(Input.CLICK_LEFT, true);
                           }

                           return state;
                        }
                     } else {
                        MovementHelper.moveTowardsWithSlightRotation(this.ctx, state, this.dest);
                        return state;
                     }
               }
            }
         }
      }
   }

   @Override
   public boolean safeToCancel(MovementState state) {
      return state.getStatus() != MovementStatus.RUNNING || MovementHelper.canWalkOn(this.ctx, this.dest.below());
   }

   @Override
   protected boolean prepared(MovementState state) {
      if (this.ctx.playerFeet().equals(this.src) || this.ctx.playerFeet().equals(this.src.below())) {
         Block block = BlockStateInterface.getBlock(this.ctx, this.src.below());
         if (MovementHelper.isClimbable(block)) {
            state.setInput(Input.SNEAK, true);
         }
      }

      return super.prepared(state);
   }
}
