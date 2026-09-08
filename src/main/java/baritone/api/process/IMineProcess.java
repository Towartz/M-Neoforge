package baritone.api.process;

import baritone.api.utils.BlockOptionalMeta;
import baritone.api.utils.BlockOptionalMetaLookup;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

public interface IMineProcess extends IBaritoneProcess {
   void mineByName(int var1, String... var2);

   void mine(int var1, BlockOptionalMetaLookup var2);

   default void mine(int quantity, BlockOptionalMetaLookup filter, List<BlockPos> initialLocations) {
      this.mine(quantity, filter);
   }

   default void mine(int quantity, BlockOptionalMeta[] boms, List<BlockPos> initialLocations) {
      this.mine(quantity, new BlockOptionalMetaLookup(boms), initialLocations);
   }

   default void mine(BlockOptionalMetaLookup filter) {
      this.mine(0, filter);
   }

   default void mineByName(String... blocks) {
      this.mineByName(0, blocks);
   }

   default void mine(int quantity, BlockOptionalMeta... boms) {
      this.mine(quantity, new BlockOptionalMetaLookup(boms));
   }

   default void mine(BlockOptionalMeta... boms) {
      this.mine(0, boms);
   }

   default void mine(int quantity, Block... blocks) {
      this.mine(quantity, new BlockOptionalMetaLookup(Stream.of(blocks).map(BlockOptionalMeta::new).toArray(BlockOptionalMeta[]::new)));
   }

   default void mine(Block... blocks) {
      this.mine(0, blocks);
   }

   default void cancel() {
      this.onLostControl();
   }
}
