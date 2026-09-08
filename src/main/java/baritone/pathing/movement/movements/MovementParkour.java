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
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.WaterFluid;

public class MovementParkour extends Movement {
   private static final BetterBlockPos[] EMPTY = new BetterBlockPos[0];
   private final Direction direction;
   private final int dist;
   private final boolean ascend;

   private MovementParkour(IBaritone baritone, BetterBlockPos src, int dist, Direction dir, boolean ascend) {
      super(baritone, src, src.relative(dir, dist).above(ascend ? 1 : 0), EMPTY, src.relative(dir, dist).below(ascend ? 0 : 1));
      this.direction = dir;
      this.dist = dist;
      this.ascend = ascend;
   }

   public static MovementParkour cost(CalculationContext context, BetterBlockPos src, Direction direction) {
      MutableMoveResult res = new MutableMoveResult();
      cost(context, src.x, src.y, src.z, direction, res);
      int dist = Math.abs(res.x - src.x) + Math.abs(res.z - src.z);
      return new MovementParkour(context.getBaritone(), src, dist, direction, res.y > src.y);
   }

   public static void cost(CalculationContext context, int x, int y, int z, Direction dir, MutableMoveResult res) {
      if (context.allowParkour) {
         if (context.allowJumpAtBuildLimit || y < context.world.getMaxBuildHeight()) {
            int xDiff = dir.getStepX();
            int zDiff = dir.getStepZ();
            if (MovementHelper.fullyPassable(context, x + xDiff, y, z + zDiff)) {
               BlockState adj = context.get(x + xDiff, y - 1, z + zDiff);
               if (!MovementHelper.canWalkOn(context, x + xDiff, y - 1, z + zDiff, adj)) {
                  if (!MovementHelper.avoidWalkingInto(adj) || adj.getFluidState().getType() instanceof WaterFluid) {
                     if (MovementHelper.fullyPassable(context, x + xDiff, y + 1, z + zDiff)) {
                        if (MovementHelper.fullyPassable(context, x + xDiff, y + 2, z + zDiff)) {
                           if (MovementHelper.fullyPassable(context, x, y + 2, z)) {
                              BlockState standingOn = context.get(x, y - 1, z);
                              if (!MovementHelper.isClimbable(standingOn.getBlock())
                                 && !(standingOn.getBlock() instanceof StairBlock)
                                 && !MovementHelper.isBottomSlab(standingOn)) {
                                 if (!context.assumeWalkOnWater || standingOn.getFluidState().isEmpty()) {
                                    if (context.get(x, y, z).getFluidState().isEmpty()) {
                                       int maxJump;
                                       if (context.allowWalkOnMagmaBlocks && standingOn.is(Blocks.MAGMA_BLOCK)) {
                                          maxJump = 2;
                                       } else if (standingOn.getBlock() == Blocks.SOUL_SAND) {
                                          maxJump = 2;
                                       } else if (context.canSprint) {
                                          maxJump = 4;
                                       } else {
                                          maxJump = 3;
                                       }

                                       int verifiedMaxJump = 1;

                                       for (int i = 2; i <= maxJump; verifiedMaxJump = i++) {
                                          int destX = x + xDiff * i;
                                          int destZ = z + zDiff * i;
                                          if (!MovementHelper.fullyPassable(context, destX, y + 1, destZ)
                                             || !MovementHelper.fullyPassable(context, destX, y + 2, destZ)) {
                                             break;
                                          }

                                          BlockState destInto = context.bsi.get0(destX, y, destZ);
                                          if (!MovementHelper.fullyPassable(context, destX, y, destZ, destInto)) {
                                             if (i <= 3
                                                && context.allowParkourAscend
                                                && context.canSprint
                                                && MovementHelper.canWalkOn(context, destX, y, destZ, destInto)
                                                && checkOvershootSafety(context.bsi, destX + xDiff, y + 1, destZ + zDiff)) {
                                                res.x = destX;
                                                res.y = y + 1;
                                                res.z = destZ;
                                                res.cost = (double)i * 3.563791874554526 + context.jumpPenalty;
                                                return;
                                             }
                                             break;
                                          }

                                          BlockState landingOn = context.bsi.get0(destX, y - 1, destZ);
                                          if (landingOn.getBlock() != Blocks.FARMLAND && MovementHelper.canWalkOn(context, destX, y - 1, destZ, landingOn)
                                             || Math.min(16, context.frostWalker + 2) >= i && MovementHelper.canUseFrostWalker(context, landingOn)) {
                                             if (checkOvershootSafety(context.bsi, destX + xDiff, y, destZ + zDiff)) {
                                                res.x = destX;
                                                res.y = y;
                                                res.z = destZ;
                                                res.cost = costFromJumpDistance(i) + context.jumpPenalty;
                                                return;
                                             }
                                             break;
                                          }

                                          if (!MovementHelper.fullyPassable(context, destX, y + 3, destZ)) {
                                             break;
                                          }
                                       }

                                       if (context.allowParkourPlace) {
                                          for (int i = verifiedMaxJump; i > 1; i--) {
                                             int destXx = x + i * xDiff;
                                             int destZx = z + i * zDiff;
                                             BlockState toReplace = context.get(destXx, y - 1, destZx);
                                             double placeCost = context.costOfPlacingAt(destXx, y - 1, destZx, toReplace);
                                             if (!(placeCost >= 1000000.0)
                                                && MovementHelper.isReplaceable(destXx, y - 1, destZx, toReplace, context.bsi)
                                                && checkOvershootSafety(context.bsi, destXx + xDiff, y, destZx + zDiff)) {
                                                for (int j = 0; j < 5; j++) {
                                                   int againstX = destXx + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[j].getStepX();
                                                   int againstY = y - 1 + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[j].getStepY();
                                                   int againstZ = destZx + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[j].getStepZ();
                                                   if ((againstX != destXx - xDiff || againstZ != destZx - zDiff)
                                                      && MovementHelper.canPlaceAgainst(context.bsi, againstX, againstY, againstZ)) {
                                                      res.x = destXx;
                                                      res.y = y;
                                                      res.z = destZx;
                                                      res.cost = costFromJumpDistance(i) + placeCost + context.jumpPenalty;
                                                      return;
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
               }
            }
         }
      }
   }

   private static boolean checkOvershootSafety(BlockStateInterface bsi, int x, int y, int z) {
      return !MovementHelper.avoidWalkingInto(bsi.get0(x, y, z)) && !MovementHelper.avoidWalkingInto(bsi.get0(x, y + 1, z));
   }

   private static double costFromJumpDistance(int dist) {
      switch (dist) {
         case 2:
            return 9.26569376882094;
         case 3:
            return 13.89854065323141;
         case 4:
            return 14.255167498218103;
         default:
            throw new IllegalStateException("LOL " + dist);
      }
   }

   @Override
   public double calculateCost(CalculationContext context) {
      MutableMoveResult res = new MutableMoveResult();
      cost(context, this.src.x, this.src.y, this.src.z, this.direction, res);
      return res.x == this.dest.x && res.y == this.dest.y && res.z == this.dest.z ? res.cost : 1000000.0;
   }

   @Override
   protected Set<BetterBlockPos> calculateValidPositions() {
      Set<BetterBlockPos> set = new HashSet<>();

      for (int i = 0; i <= this.dist; i++) {
         for (int y = 0; y < 2; y++) {
            set.add(this.src.relative(this.direction, i).above(y));
         }
      }

      return set;
   }

   @Override
   public boolean safeToCancel(MovementState state) {
      return state.getStatus() != MovementStatus.RUNNING;
   }

   @Override
   public MovementState updateState(MovementState state) {
      super.updateState(state);
      if (state.getStatus() != MovementStatus.RUNNING) {
         return state;
      } else if (this.ctx.playerFeet().y < this.src.y) {
         this.logDebug("sorry");
         return state.setStatus(MovementStatus.UNREACHABLE);
      } else if (!MovementHelper.openDoors(this.ctx, state, this.src, this.src.relative(this.direction))) {
         return state;
      } else {
         if (this.dist >= 4 || this.ascend) {
            state.setInput(Input.SPRINT, true);
         }

         if (Baritone.settings().allowWalkOnMagmaBlocks.value && this.ctx.world().getBlockState(this.ctx.playerFeet().below()).is(Blocks.MAGMA_BLOCK)) {
            state.setInput(Input.SNEAK, true);
         }

         MovementHelper.moveTowards(this.ctx, state, this.dest);
         if (this.ctx.playerFeet().equals(this.dest)) {
            Block d = BlockStateInterface.getBlock(this.ctx, this.dest);
            if (d == Blocks.VINE || d == Blocks.LADDER) {
               return state.setStatus(MovementStatus.SUCCESS);
            }

            if (this.ctx.player().position().y - (double)this.ctx.playerFeet().getY() < 0.094) {
               state.setStatus(MovementStatus.SUCCESS);
            }
         } else if (!this.ctx.playerFeet().equals(this.src)) {
            if (!this.ctx.playerFeet().equals(this.src.relative(this.direction)) && !(this.ctx.player().position().y - (double)this.src.y > 1.0E-4)) {
               if (!this.ctx.playerFeet().equals(this.dest.relative(this.direction, -1))) {
                  state.setInput(Input.SPRINT, false);
                  if (this.ctx.playerFeet().equals(this.src.relative(this.direction, -1))) {
                     MovementHelper.moveTowards(this.ctx, state, this.src);
                  } else {
                     MovementHelper.moveTowards(this.ctx, state, this.src.relative(this.direction, -1));
                  }
               }
            } else {
               if (Baritone.settings().allowPlace.value
                  && ((Baritone)this.baritone).getInventoryBehavior().hasGenericThrowaway()
                  && !MovementHelper.canWalkOn(this.ctx, this.dest.below())
                  && !this.ctx.player().onGround()
                  && MovementHelper.attemptToPlaceABlock(state, this.baritone, this.dest.below(), true, false) == MovementHelper.PlaceResult.READY_TO_PLACE) {
                  state.setInput(Input.CLICK_RIGHT, true);
               }

               if (this.dist == 3 && !this.ascend) {
                  double xDiff = (double)this.src.x + 0.5 - this.ctx.player().position().x;
                  double zDiff = (double)this.src.z + 0.5 - this.ctx.player().position().z;
                  double distFromStart = Math.max(Math.abs(xDiff), Math.abs(zDiff));
                  if (distFromStart < 0.7) {
                     return state;
                  }
               }

               state.setInput(Input.JUMP, true);
            }
         }

         return state;
      }
   }
}
