package meteordevelopment.meteorclient.utils.world;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import meteordevelopment.meteorclient.mixin.ChunkAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;

public class BlockEntityIterator implements Iterator<BlockEntity> {
   private final Iterator<ChunkAccess> chunks = new ChunkIterator(false);
   private Iterator<BlockEntity> blockEntities;

   public BlockEntityIterator() {
      this.nextChunk();
   }

   private void nextChunk() {
      this.blockEntities = null;
      while (this.chunks.hasNext()) {
         ChunkAccess chunk = this.chunks.next();
         if (chunk instanceof ChunkAccessor accessor) {
            Map<BlockPos, BlockEntity> blockEntityMap = accessor.getBlockEntities();
            if (blockEntityMap != null && !blockEntityMap.isEmpty()) {
               this.blockEntities = new ArrayList<>(blockEntityMap.values()).iterator();
               return;
            }
         }
      }
   }

   @Override
   public boolean hasNext() {
      if (this.blockEntities != null && this.blockEntities.hasNext()) {
         return true;
      }
      this.nextChunk();
      return this.blockEntities != null && this.blockEntities.hasNext();
   }

   @Override
   public BlockEntity next() {
      if (!hasNext()) {
         throw new NoSuchElementException();
      }
      return this.blockEntities.next();
   }
}
