package baritone.pathing.movement.movements;

import baritone.api.IBaritone;
import baritone.api.pathing.movement.MovementStatus;
import baritone.api.utils.BetterBlockPos;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.Movement;
import baritone.pathing.movement.MovementHelper;
import baritone.pathing.movement.MovementState;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class MovementDownward extends Movement {
   private int numTicks = 0;

   public MovementDownward(IBaritone baritone, BetterBlockPos start, BetterBlockPos end) {
      super(baritone, start, end, new BetterBlockPos[]{end});
   }

   @Override
   public void reset() {
      super.reset();
      this.numTicks = 0;
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
      if (!context.allowDownward) {
         return 1000000.0;
      } else if (!MovementHelper.canWalkOn(context, x, y - 2, z)) {
         return 1000000.0;
      } else {
         BlockState down = context.get(x, y - 1, z);
         Block downBlock = down.getBlock();
         return downBlock != Blocks.LADDER && downBlock != Blocks.VINE
            ? FALL_N_BLOCKS_COST[1] + MovementHelper.getMiningDurationTicks(context, x, y - 1, z, down, false)
            : 6.666666666666667;
      }
   }

   @Override
   public MovementState updateState(MovementState state) {
      super.updateState(state);
      if (state.getStatus() != MovementStatus.RUNNING) {
         return state;
      } else if (this.ctx.playerFeet().equals(this.dest)) {
         return state.setStatus(MovementStatus.SUCCESS);
      } else if (!this.playerInValidPosition()) {
         return state.setStatus(MovementStatus.UNREACHABLE);
      } else {
         double diffX = this.ctx.player().position().x - ((double)this.dest.getX() + 0.5);
         double diffZ = this.ctx.player().position().z - ((double)this.dest.getZ() + 0.5);
         double ab = Math.sqrt(diffX * diffX + diffZ * diffZ);
         if (this.numTicks++ < 10 && ab < 0.2) {
            return state;
         } else {
            MovementHelper.moveTowards(this.ctx, state, this.positionsToBreak[0]);
            return state;
         }
      }
   }
}
