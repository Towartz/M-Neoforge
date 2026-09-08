package baritone.pathing.precompute;

import baritone.pathing.movement.MovementHelper;
import baritone.utils.BlockStateInterface;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class PrecomputedData {
   private final byte[] data = new byte[Block.BLOCK_STATE_REGISTRY.size()];
   private static final byte COMPLETED_MASK = 1;
   private static final byte FULLY_PASSABLE_MAYBE_MASK = 2;
   private static final byte FULLY_PASSABLE_MASK = 4;
   private static final byte CAN_WALK_THROUGH_MAYBE_MASK = 8;
   private static final byte CAN_WALK_THROUGH_MASK = 16;
   private static final byte CAN_WALK_ON_MAYBE_MASK = 32;
   private static final byte CAN_WALK_ON_MASK = 64;

   private int fillData(int id, BlockState state) {
      byte blockData = 0;
      Ternary canWalkOnState = MovementHelper.canWalkOnBlockState(state);
      switch (canWalkOnState) {
         case YES:
            blockData = (byte)(blockData | 64);
            break;
         case MAYBE:
            blockData = (byte)(blockData | 32);
      }

      Ternary canWalkThroughState = MovementHelper.canWalkThroughBlockState(state);
      switch (canWalkThroughState) {
         case YES:
            blockData = (byte)(blockData | 16);
            break;
         case MAYBE:
            blockData = (byte)(blockData | 8);
      }

      Ternary fullyPassableState = MovementHelper.fullyPassableBlockState(state);
      switch (fullyPassableState) {
         case YES:
            blockData = (byte)(blockData | 4);
            break;
         case MAYBE:
            blockData = (byte)(blockData | 2);
      }

      blockData = (byte)(blockData | 1);
      this.data[id] = blockData;
      return blockData;
   }

   public boolean canWalkOn(BlockStateInterface bsi, int x, int y, int z, BlockState state) {
      int id = Block.BLOCK_STATE_REGISTRY.getId(state);
      int blockData = this.data[id];
      if ((blockData & 1) == 0) {
         blockData = this.fillData(id, state);
      }

      return (blockData & 32) != 0 ? MovementHelper.canWalkOnPosition(bsi, x, y, z, state) : (blockData & 64) != 0;
   }

   public boolean canWalkThrough(BlockStateInterface bsi, int x, int y, int z, BlockState state) {
      int id = Block.BLOCK_STATE_REGISTRY.getId(state);
      int blockData = this.data[id];
      if ((blockData & 1) == 0) {
         blockData = this.fillData(id, state);
      }

      return (blockData & 8) != 0 ? MovementHelper.canWalkThroughPosition(bsi, x, y, z, state) : (blockData & 16) != 0;
   }

   public boolean fullyPassable(BlockStateInterface bsi, int x, int y, int z, BlockState state) {
      int id = Block.BLOCK_STATE_REGISTRY.getId(state);
      int blockData = this.data[id];
      if ((blockData & 1) == 0) {
         blockData = this.fillData(id, state);
      }

      return (blockData & 2) != 0 ? MovementHelper.fullyPassablePosition(bsi, x, y, z, state) : (blockData & 4) != 0;
   }
}
