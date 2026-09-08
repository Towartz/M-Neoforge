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
import baritone.utils.pathing.MutableMoveResult;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import baritone.api.utils.VecUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;

public class MovementDiagonal extends Movement {
   private static final double SQRT_2 = Math.sqrt(2.0);

   public MovementDiagonal(IBaritone baritone, BetterBlockPos start, Direction dir1, Direction dir2, int dy) {
      this(baritone, start, start.relative(dir1), start.relative(dir2), dir2, dy);
   }

   private MovementDiagonal(IBaritone baritone, BetterBlockPos start, BetterBlockPos dir1, BetterBlockPos dir2, Direction drr2, int dy) {
      this(baritone, start, dir1.relative(drr2).above(dy), dir1, dir2);
   }

   private MovementDiagonal(IBaritone baritone, BetterBlockPos start, BetterBlockPos end, BetterBlockPos dir1, BetterBlockPos dir2) {
      super(baritone, start, end, new BetterBlockPos[]{dir1, dir1.above(), dir2, dir2.above(), end, end.above()});
   }

   @Override
   protected boolean safeToCancel(MovementState state) {
      LocalPlayer player = this.ctx.player();
      double offset = 0.25;
      double x = player.position().x;
      double y = player.position().y - 1.0;
      double z = player.position().z;
      if (this.ctx.playerFeet().equals(this.src)) {
         return true;
      } else if (MovementHelper.canWalkOn(this.ctx, new BlockPos(this.src.x, this.src.y - 1, this.dest.z))
         && MovementHelper.canWalkOn(this.ctx, new BlockPos(this.dest.x, this.src.y - 1, this.src.z))) {
         return true;
      } else {
         return !this.ctx.playerFeet().equals(new BetterBlockPos(this.src.x, this.src.y, this.dest.z))
               && !this.ctx.playerFeet().equals(new BetterBlockPos(this.dest.x, this.src.y, this.src.z))
            ? true
            : MovementHelper.canWalkOn(this.ctx, new BetterBlockPos(x + offset, y, z + offset))
               || MovementHelper.canWalkOn(this.ctx, new BetterBlockPos(x + offset, y, z - offset))
               || MovementHelper.canWalkOn(this.ctx, new BetterBlockPos(x - offset, y, z + offset))
               || MovementHelper.canWalkOn(this.ctx, new BetterBlockPos(x - offset, y, z - offset));
      }
   }

   @Override
   public double calculateCost(CalculationContext context) {
      MutableMoveResult result = new MutableMoveResult();
      cost(context, this.src.x, this.src.y, this.src.z, this.dest.x, this.dest.z, result);
      return result.y != this.dest.y ? 1000000.0 : result.cost;
   }

   @Override
   protected Set<BetterBlockPos> calculateValidPositions() {
      BetterBlockPos diagA = new BetterBlockPos(this.src.x, this.src.y, this.dest.z);
      BetterBlockPos diagB = new BetterBlockPos(this.dest.x, this.src.y, this.src.z);
      int dx = this.dest.x - this.src.x;
      int dz = this.dest.z - this.src.z;
      BetterBlockPos overshoot = new BetterBlockPos(this.dest.x + dx, this.dest.y, this.dest.z + dz);
      Set<BetterBlockPos> set = new HashSet<>();
      set.add(this.src);
      set.add(this.src.above());
      set.add(this.dest);
      set.add(this.dest.above());
      set.add(diagA);
      set.add(diagA.above());
      set.add(diagB);
      set.add(diagB.above());
      set.add(overshoot);
      set.add(overshoot.above());
      if (this.dest.y < this.src.y) {
         set.add(diagA.below());
         set.add(diagB.below());
         set.add(this.dest.below());
         set.add(overshoot.below());
      } else if (this.dest.y > this.src.y) {
         set.add(this.src.above(2));
         set.add(this.dest.above(2));
         set.add(overshoot.above(2));
      }
      return set;
   }

   public static void cost(CalculationContext context, int x, int y, int z, int destX, int destZ, MutableMoveResult res) {
      BlockState destIntoUpper = context.get(destX, y + 1, destZ);
      if (MovementHelper.canWalkThrough(context, destX, y + 1, destZ, destIntoUpper)) {
         BlockState destInto = context.get(destX, y, destZ);
         boolean ascend = false;
         boolean descend = false;
         boolean frostWalker = false;
         boolean sneaking = false;
         BlockState fromDown;
         BlockState destWalkOn;
         if (!MovementHelper.canWalkThrough(context, destX, y, destZ, destInto)) {
            ascend = true;
            if (!context.allowDiagonalAscend
               || !MovementHelper.canWalkThrough(context, x, y + 2, z)
               || !MovementHelper.canWalkOn(context, destX, y, destZ, destInto)
               || !MovementHelper.canWalkThrough(context, destX, y + 2, destZ)) {
               return;
            }

            destWalkOn = destInto;
            fromDown = context.get(x, y - 1, z);
         } else {
            destWalkOn = context.get(destX, y - 1, destZ);
            fromDown = context.get(x, y - 1, z);
            boolean standingOnABlock = MovementHelper.mustBeSolidToWalkOn(context, x, y - 1, z, fromDown);
            frostWalker = standingOnABlock && MovementHelper.canUseFrostWalker(context, destWalkOn);
            if (!frostWalker && !MovementHelper.canWalkOn(context, destX, y - 1, destZ, destWalkOn)) {
               descend = true;
               if (!context.allowDiagonalDescend
                  || !MovementHelper.canWalkOn(context, destX, y - 2, destZ)
                  || !MovementHelper.canWalkThrough(context, destX, y - 1, destZ, destWalkOn)) {
                  return;
               }
            }

            frostWalker &= !context.assumeWalkOnWater;
         }

         BlockState startState = context.get(x, y, z);
         if (!isBlockingDoor(startState, x, z, destX, destZ) && !isBlockingDoor(ascend ? destIntoUpper : destInto, destX, destZ, x, y)) {
            double multiplier = 4.63284688441047;
            if (destWalkOn.is(Blocks.SOUL_SAND)) {
               multiplier += 2.316423442205235;
            } else if (context.allowWalkOnMagmaBlocks && destWalkOn.is(Blocks.MAGMA_BLOCK)) {
               multiplier += 5.375884250102457;
               sneaking = true;
            } else if (!frostWalker && destWalkOn.getBlock() == Blocks.WATER) {
               multiplier += context.walkOnWaterOnePenalty * SQRT_2;
            }

            Block fromDownBlock = fromDown.getBlock();
            if (!MovementHelper.isClimbable(fromDownBlock)) {
               if (fromDownBlock == Blocks.SOUL_SAND) {
                  multiplier += 2.316423442205235;
               } else if (context.allowWalkOnMagmaBlocks && fromDownBlock.equals(Blocks.MAGMA_BLOCK)) {
                  multiplier += 5.375884250102457;
                  sneaking = true;
               }

               BlockState cuttingOver1 = context.get(x, y - 1, destZ);
               if ((context.allowWalkOnMagmaBlocks || !cuttingOver1.is(Blocks.MAGMA_BLOCK)) && !MovementHelper.isLava(cuttingOver1)) {
                  BlockState cuttingOver2 = context.get(destX, y - 1, z);
                  if ((context.allowWalkOnMagmaBlocks || !cuttingOver2.is(Blocks.MAGMA_BLOCK)) && !MovementHelper.isLava(cuttingOver2)) {
                     boolean water = false;
                     Block startIn = startState.getBlock();
                     if (MovementHelper.isWater(startState) || MovementHelper.isWater(destInto)) {
                        if (ascend) {
                           return;
                        }

                        multiplier = context.waterWalkSpeed;
                        water = true;
                     }

                     BlockState pb0 = context.get(x, y, destZ);
                     BlockState pb2 = context.get(destX, y, z);
                     if (ascend) {
                        boolean ATop = MovementHelper.canWalkThrough(context, x, y + 2, destZ);
                        boolean AMid = MovementHelper.canWalkThrough(context, x, y + 1, destZ);
                        boolean ALow = MovementHelper.canWalkThrough(context, x, y, destZ, pb0);
                        boolean BTop = MovementHelper.canWalkThrough(context, destX, y + 2, z);
                        boolean BMid = MovementHelper.canWalkThrough(context, destX, y + 1, z);
                        boolean BLow = MovementHelper.canWalkThrough(context, destX, y, z, pb2);
                        ALow &= !isBlockingDoor(pb0, x, destZ, destX, z);
                        BLow &= !isBlockingDoor(pb2, destX, z, x, destZ);
                        if ((ATop && AMid && ALow || BTop && BMid && BLow)
                           && !MovementHelper.avoidWalkingInto(pb0)
                           && !MovementHelper.avoidWalkingInto(pb2)
                           && (!ATop || !AMid || !MovementHelper.canWalkOn(context, x, y, destZ, pb0))
                           && (!BTop || !BMid || !MovementHelper.canWalkOn(context, destX, y, z, pb2))
                           && (ATop || !AMid || !ALow)
                           && (BTop || !BMid || !BLow)) {
                           res.cost = multiplier * SQRT_2 + JUMP_ONE_BLOCK_COST;
                           res.x = destX;
                           res.z = destZ;
                           res.y = y + 1;
                        }
                     } else {
                        double optionA = MovementHelper.getMiningDurationTicks(context, x, y, destZ, pb0, false);
                        double optionB = MovementHelper.getMiningDurationTicks(context, destX, y, z, pb2, false);
                        optionA += isBlockingDoor(pb0, x, destZ, destX, z) ? 1.0 : 0.0;
                        optionB += isBlockingDoor(pb2, destX, z, x, destZ) ? 1.0 : 0.0;
                        if (optionA == 0.0 || optionB == 0.0) {
                           BlockState pb1 = context.get(x, y + 1, destZ);
                           optionA += MovementHelper.getMiningDurationTicks(context, x, y + 1, destZ, pb1, true);
                           if (optionA == 0.0 || optionB == 0.0) {
                              BlockState pb3 = context.get(destX, y + 1, z);
                              if (optionA != 0.0
                                 || (!MovementHelper.avoidWalkingInto(pb2) || pb2.getBlock() == Blocks.WATER) && !MovementHelper.avoidWalkingInto(pb3)) {
                                 optionB += MovementHelper.getMiningDurationTicks(context, destX, y + 1, z, pb3, true);
                                 if (optionA == 0.0 || optionB == 0.0) {
                                    if (optionB != 0.0
                                       || (!MovementHelper.avoidWalkingInto(pb0) || pb0.getBlock() == Blocks.WATER) && !MovementHelper.avoidWalkingInto(pb1)) {
                                       if (optionA != 0.0 || optionB != 0.0) {
                                          multiplier *= SQRT_2 - 0.001;
                                          if (MovementHelper.isClimbable(startIn)) {
                                             return;
                                          }
                                       } else if (context.canSprint && !water && !sneaking) {
                                          multiplier *= 0.7692444761225944;
                                       }

                                       res.cost = multiplier * SQRT_2;
                                       if (descend) {
                                          res.cost = res.cost + Math.max(FALL_N_BLOCKS_COST[1], 0.9265693768820937);
                                          res.y = y - 1;
                                       } else {
                                          res.y = y;
                                       }

                                       res.x = destX;
                                       res.z = destZ;
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @Override
   public MovementState updateState(MovementState state) {
      super.updateState(state);
      if (state.getStatus() != MovementStatus.RUNNING) {
         return state;
      } else if (this.ctx.playerFeet().equals(this.dest)
            || MovementHelper.hasArrivedHorizontally(this.ctx, this.dest, 0.25)
            || MovementHelper.isCrossingDestination(this.ctx, this.dest)) {
         return state.setStatus(MovementStatus.SUCCESS);
      } else if (this.playerInValidPosition()
         || MovementHelper.isLiquid(this.ctx, this.src) && this.getValidPositions().contains(this.ctx.playerFeet().above())) {
         if (isBlockingDoor(
               BlockStateInterface.get(this.ctx, new BetterBlockPos(this.src.x, this.src.y, this.dest.z)), this.src.x, this.dest.z, this.dest.x, this.src.z
            )
            || MovementHelper.openDoors(this.ctx, state, this.src, new BetterBlockPos(this.src.x, this.src.y, this.dest.z))
               && MovementHelper.openDoors(this.ctx, state, new BetterBlockPos(this.src.x, this.dest.y, this.dest.z), this.dest)) {
            if (isBlockingDoor(
                  BlockStateInterface.get(this.ctx, new BetterBlockPos(this.dest.x, this.src.y, this.src.z)), this.dest.x, this.src.z, this.src.x, this.dest.z
               )
               || MovementHelper.openDoors(this.ctx, state, this.src, new BetterBlockPos(this.dest.x, this.src.y, this.src.z))
                  && MovementHelper.openDoors(this.ctx, state, new BetterBlockPos(this.dest.x, this.dest.y, this.src.z), this.dest)) {
               if (this.dest.y > this.src.y) {
                  double var10001 = (double)this.src.y;
                  if (this.ctx.player().position().y < var10001 + 0.1 && this.ctx.player().horizontalCollision) {
                     state.setInput(Input.JUMP, true);
                  }
               }

               if (this.sprint()) {
                  state.setInput(Input.SPRINT, true);
               }

               state.setInput(
                  Input.SNEAK,
                  Baritone.settings().allowWalkOnMagmaBlocks.value
                     && MovementHelper.steppingOnBlocks(this.ctx).stream().anyMatch(block -> this.ctx.world().getBlockState(block).is(Blocks.MAGMA_BLOCK))
               );
               MovementHelper.moveTowards(this.ctx, state, this.dest);
               return state;
            } else {
               return state;
            }
         } else {
            return state;
         }
      } else if (this.ctx.player().position().distanceToSqr(VecUtils.getBlockPosCenter(this.dest)) < 2.5) {
         MovementHelper.moveTowards(this.ctx, state, this.dest);
         return state;
      } else {
         return state.setStatus(MovementStatus.UNREACHABLE);
      }
   }

   private boolean sprint() {
      if (MovementHelper.isLiquid(this.ctx, this.ctx.playerFeet()) && !Baritone.settings().sprintInWater.value) {
         return false;
      } else {
         for (int i = 0; i < 4; i++) {
            if (!MovementHelper.canWalkThrough(this.ctx, this.positionsToBreak[i])) {
               return false;
            }
         }

         return true;
      }
   }

   @Override
   protected boolean prepared(MovementState state) {
      if (state.getStatus() == MovementStatus.WAITING) {
         return true;
      }

      // Check destination clearance (end and end.above())
      for (int i = 4; i < 6; i++) {
         BetterBlockPos destBlock = this.positionsToBreak[i];
         if (!MovementHelper.canWalkThrough(this.ctx, destBlock)) {
            return super.prepared(state);
         }
      }

      // Check corner clearance: at least one side (dir1 or dir2) must be passable
      boolean side1Passable = MovementHelper.canWalkThrough(this.ctx, this.positionsToBreak[0]) && MovementHelper.canWalkThrough(this.ctx, this.positionsToBreak[1]);
      boolean side2Passable = MovementHelper.canWalkThrough(this.ctx, this.positionsToBreak[2]) && MovementHelper.canWalkThrough(this.ctx, this.positionsToBreak[3]);

      if (!side1Passable && !side2Passable) {
         // Both corner sides are solid walls! Must break obstacles to allow diagonal passage
         return super.prepared(state);
      }

      return true;
   }

   @Override
   public List<BlockPos> toBreak(BlockStateInterface bsi) {
      if (this.toBreakCached != null) {
         return this.toBreakCached;
      } else {
         List<BlockPos> result = new ArrayList<>();

         for (int i = 4; i < 6; i++) {
            if (!MovementHelper.canWalkThrough(bsi, this.positionsToBreak[i].x, this.positionsToBreak[i].y, this.positionsToBreak[i].z)) {
               result.add(this.positionsToBreak[i]);
            }
         }

         boolean side1Passable = MovementHelper.canWalkThrough(bsi, this.positionsToBreak[0].x, this.positionsToBreak[0].y, this.positionsToBreak[0].z)
            && MovementHelper.canWalkThrough(bsi, this.positionsToBreak[1].x, this.positionsToBreak[1].y, this.positionsToBreak[1].z);
         boolean side2Passable = MovementHelper.canWalkThrough(bsi, this.positionsToBreak[2].x, this.positionsToBreak[2].y, this.positionsToBreak[2].z)
            && MovementHelper.canWalkThrough(bsi, this.positionsToBreak[3].x, this.positionsToBreak[3].y, this.positionsToBreak[3].z);

         if (!side1Passable && !side2Passable) {
            for (int i = 0; i < 2; i++) {
               if (!MovementHelper.canWalkThrough(bsi, this.positionsToBreak[i].x, this.positionsToBreak[i].y, this.positionsToBreak[i].z)) {
                  result.add(this.positionsToBreak[i]);
               }
            }
         }

         this.toBreakCached = result;
         return result;
      }
   }

   @Override
   public List<BlockPos> toWalkInto(BlockStateInterface bsi) {
      if (this.toWalkIntoCached == null) {
         this.toWalkIntoCached = new ArrayList<>();
      }

      List<BlockPos> result = new ArrayList<>();

      for (int i = 0; i < 4; i++) {
         if (!MovementHelper.canWalkThrough(bsi, this.positionsToBreak[i].x, this.positionsToBreak[i].y, this.positionsToBreak[i].z)) {
            result.add(this.positionsToBreak[i]);
         }
      }

      this.toWalkIntoCached = result;
      return this.toWalkIntoCached;
   }

   private static boolean isBlockingDoor(BlockState state, int doorX, int doorZ, int otherX, int otherZ) {
      if (!(state.getBlock() instanceof DoorBlock)) {
         return false;
      } else {
         Vec3i offset = ((Direction)state.getValue(HorizontalDirectionalBlock.FACING)).getNormal();
         int ox = offset.getX();
         int oz = offset.getZ();
         int nbrX;
         int nbrZ;
         if (state.getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT) {
            nbrX = doorX - ox + oz;
            nbrZ = doorZ - oz - ox;
         } else {
            nbrX = doorX - ox - oz;
            nbrZ = doorZ - oz + ox;
         }

         return nbrX == otherX && nbrZ == otherZ;
      }
   }
}
