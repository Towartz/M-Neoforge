package baritone.api.schematic;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public class SubstituteSchematic extends AbstractSchematic {
   private final ISchematic schematic;
   private final Map<Block, List<Block>> substitutions;
   private final Map<BlockState, Map<Block, BlockState>> blockStateCache = new HashMap<>();

   public SubstituteSchematic(ISchematic schematic, Map<Block, List<Block>> substitutions) {
      super(schematic.widthX(), schematic.heightY(), schematic.lengthZ());
      this.schematic = schematic;
      this.substitutions = substitutions;
   }

   @Override
   public boolean inSchematic(int x, int y, int z, BlockState currentState) {
      return this.schematic.inSchematic(x, y, z, currentState);
   }

   @Override
   public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
      BlockState desired = this.schematic.desiredState(x, y, z, current, approxPlaceable);
      Block desiredBlock = desired.getBlock();
      if (!this.substitutions.containsKey(desiredBlock)) {
         return desired;
      } else {
         List<Block> substitutes = this.substitutions.get(desiredBlock);
         if (substitutes.contains(current.getBlock()) && !(current.getBlock() instanceof AirBlock)) {
            return this.withBlock(desired, current.getBlock());
         } else {
            for (Block substitute : substitutes) {
               if (substitute instanceof AirBlock) {
                  return current.getBlock() instanceof AirBlock ? current : Blocks.AIR.defaultBlockState();
               }

               for (BlockState placeable : approxPlaceable) {
                  if (substitute.equals(placeable.getBlock())) {
                     return this.withBlock(desired, placeable.getBlock());
                  }
               }
            }

            return substitutes.get(0).defaultBlockState();
         }
      }
   }

   private BlockState withBlock(BlockState state, Block block) {
      if (this.blockStateCache.containsKey(state) && this.blockStateCache.get(state).containsKey(block)) {
         return this.blockStateCache.get(state).get(block);
      } else {
         Collection<Property<?>> properties = state.getProperties();
         BlockState newState = block.defaultBlockState();

         for (Property<?> property : properties) {
            try {
               newState = this.copySingleProp(state, newState, property);
            } catch (IllegalArgumentException var8) {
            }
         }

         this.blockStateCache.computeIfAbsent(state, s -> new HashMap<>()).put(block, newState);
         return newState;
      }
   }

   private <T extends Comparable<T>> BlockState copySingleProp(BlockState fromState, BlockState toState, Property<T> prop) {
      return (BlockState)toState.setValue(prop, fromState.getValue(prop));
   }
}
