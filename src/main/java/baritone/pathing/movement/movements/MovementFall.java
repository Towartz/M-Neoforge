package baritone.pathing.movement.movements;

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
import baritone.utils.pathing.MutableMoveResult;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.WaterFluid;
import net.minecraft.world.phys.Vec3;

public class MovementFall extends Movement {
   private static final ItemStack STACK_BUCKET_WATER = new ItemStack(Items.WATER_BUCKET);
   private static final ItemStack STACK_BUCKET_EMPTY = new ItemStack(Items.BUCKET);

   public MovementFall(IBaritone baritone, BetterBlockPos src, BetterBlockPos dest) {
      super(baritone, src, dest, buildPositionsToBreak(src, dest));
   }

   @Override
   public double calculateCost(CalculationContext context) {
      MutableMoveResult result = new MutableMoveResult();
      MovementDescend.cost(context, this.src.x, this.src.y, this.src.z, this.dest.x, this.dest.z, result);
      return result.y != this.dest.y ? 1000000.0 : result.cost;
   }

   @Override
   protected Set<BetterBlockPos> calculateValidPositions() {
      Set<BetterBlockPos> set = new HashSet<>();
      set.add(this.src);

      for (int y = this.src.y - this.dest.y; y >= 0; y--) {
         set.add(this.dest.above(y));
      }

      return set;
   }

   private boolean willPlaceBucket() {
      CalculationContext context = new CalculationContext(this.baritone);
      MutableMoveResult result = new MutableMoveResult();
      return MovementDescend.dynamicFallCost(
         context, this.src.x, this.src.y, this.src.z, this.dest.x, this.dest.z, 0.0, context.get(this.dest.x, this.src.y - 2, this.dest.z), result
      );
   }

   @Override
   public MovementState updateState(MovementState state) {
      super.updateState(state);
      if (state.getStatus() != MovementStatus.RUNNING) {
         return state;
      } else {
         BlockPos playerFeet = this.ctx.playerFeet();
         Rotation toDest = RotationUtils.calcRotationFromVec3d(this.ctx.playerHead(), VecUtils.getBlockPosCenter(this.dest), this.ctx.playerRotations());
         Rotation targetRotation = null;
         BlockState destState = this.ctx.world().getBlockState(this.dest);
         Block destBlock = destState.getBlock();
         if (this.ctx.world().getBlockState(this.dest.below()).is(Blocks.MAGMA_BLOCK)
            && MovementHelper.steppingOnBlocks(this.ctx).stream().allMatch(block -> MovementHelper.canWalkThrough(this.ctx, block))) {
            state.setInput(Input.SNEAK, true);
         }

         boolean isWater = destState.getFluidState().getType() instanceof WaterFluid;
         if (!isWater && this.willPlaceBucket() && !playerFeet.equals(this.dest)) {
            if (!Inventory.isHotbarSlot(this.ctx.player().getInventory().findSlotMatchingItem(STACK_BUCKET_WATER))
               || this.ctx.world().dimension() == Level.NETHER) {
               return state.setStatus(MovementStatus.UNREACHABLE);
            }

            if (this.ctx.player().position().y - (double)this.dest.getY() < this.ctx.playerController().getBlockReachDistance()
               && !this.ctx.player().onGround()) {
               this.ctx.player().getInventory().selected = this.ctx.player().getInventory().findSlotMatchingItem(STACK_BUCKET_WATER);
               targetRotation = new Rotation(toDest.getYaw(), 90.0F);
               if (this.ctx.isLookingAt(this.dest) || this.ctx.isLookingAt(this.dest.below())) {
                  state.setInput(Input.CLICK_RIGHT, true);
               }
            }
         }

         if (!MovementHelper.openDoors(this.ctx, state, this.src, new BetterBlockPos(this.dest.x, this.src.y, this.dest.z))) {
            return state;
         } else {
            if (targetRotation != null) {
               state.setTarget(new MovementState.MovementTarget(targetRotation, true));
            } else {
               state.setTarget(new MovementState.MovementTarget(toDest, false));
            }

            boolean landed = this.ctx.player().onGround() || this.ctx.player().position().y - (double)playerFeet.getY() < 0.25 || isWater;
            if ((playerFeet.equals(this.dest) || MovementHelper.hasArrivedHorizontally(this.ctx, this.dest, 0.25)) && landed) {
               if (!isWater) {
                  return state.setStatus(MovementStatus.SUCCESS);
               }

               if (Inventory.isHotbarSlot(this.ctx.player().getInventory().findSlotMatchingItem(STACK_BUCKET_EMPTY))) {
                  this.ctx.player().getInventory().selected = this.ctx.player().getInventory().findSlotMatchingItem(STACK_BUCKET_EMPTY);
                  if (this.ctx.player().getDeltaMovement().y >= 0.0) {
                     return state.setInput(Input.CLICK_RIGHT, true);
                  }

                  return state;
               }

               if (this.ctx.player().getDeltaMovement().y >= 0.0) {
                  return state.setStatus(MovementStatus.SUCCESS);
               }
            }

            Vec3 destCenter = VecUtils.getBlockPosCenter(this.dest);
            if (Math.abs(this.ctx.player().position().x + this.ctx.player().getDeltaMovement().x - destCenter.x) > 0.1
               || Math.abs(this.ctx.player().position().z + this.ctx.player().getDeltaMovement().z - destCenter.z) > 0.1) {
               if (!this.ctx.player().onGround() && Math.abs(this.ctx.player().getDeltaMovement().y) > 0.4 && !MovementHelper.isNoFallActive()) {
                  state.setInput(Input.SNEAK, true);
               }

               state.setInput(Input.MOVE_FORWARD, true);
            }

            Vec3i avoid = Optional.ofNullable(this.avoid()).<Vec3i>map(Direction::getNormal).orElse(null);
            if (avoid == null) {
               avoid = this.src.subtract(this.dest);
            } else {
               double dist = Math.abs((double)avoid.getX() * (destCenter.x - (double)avoid.getX() / 2.0 - this.ctx.player().position().x))
                  + Math.abs((double)avoid.getZ() * (destCenter.z - (double)avoid.getZ() / 2.0 - this.ctx.player().position().z));
               if (dist < 0.6) {
                  state.setInput(Input.MOVE_FORWARD, true);
               } else if (!this.ctx.player().onGround()) {
                  state.setInput(Input.SNEAK, false);
               }
            }

            if (targetRotation == null) {
               Vec3 destCenterOffset = new Vec3(destCenter.x + 0.125 * (double)avoid.getX(), destCenter.y, destCenter.z + 0.125 * (double)avoid.getZ());
               state.setTarget(
                  new MovementState.MovementTarget(
                     RotationUtils.calcRotationFromVec3d(this.ctx.playerHead(), destCenterOffset, this.ctx.playerRotations()), false
                  )
               );
            }

            return state;
         }
      }
   }

   private Direction avoid() {
      for (int i = 0; i < 15; i++) {
         BlockState state = this.ctx.world().getBlockState(this.ctx.playerFeet().below(i));
         if (state.getBlock() == Blocks.LADDER) {
            return (Direction)state.getValue(LadderBlock.FACING);
         }
      }

      return null;
   }

   @Override
   public boolean safeToCancel(MovementState state) {
      return this.ctx.playerFeet().equals(this.src) || state.getStatus() != MovementStatus.RUNNING;
   }

   private static BetterBlockPos[] buildPositionsToBreak(BetterBlockPos src, BetterBlockPos dest) {
      int diffX = src.getX() - dest.getX();
      int diffZ = src.getZ() - dest.getZ();
      int diffY = Math.abs(src.getY() - dest.getY());
      BetterBlockPos[] toBreak = new BetterBlockPos[diffY + 2];

      for (int i = 0; i < toBreak.length; i++) {
         toBreak[i] = new BetterBlockPos(src.getX() - diffX, src.getY() + 1 - i, src.getZ() - diffZ);
      }

      return toBreak;
   }

   @Override
   protected boolean prepared(MovementState state) {
      if (state.getStatus() == MovementStatus.WAITING) {
         return true;
      } else {
         for (int i = 0; i < 4 && i < this.positionsToBreak.length; i++) {
            if (!MovementHelper.canWalkThrough(this.ctx, this.positionsToBreak[i])) {
               return super.prepared(state);
            }
         }

         return true;
      }
   }
}
