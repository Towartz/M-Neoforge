package meteordevelopment.meteorclient.utils.world;

import java.util.Iterator;
import java.util.NoSuchElementException;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.mixin.ClientChunkManagerAccessor;
import meteordevelopment.meteorclient.mixin.ClientChunkMapAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

public class ChunkIterator implements Iterator<ChunkAccess> {
   private final ClientChunkMapAccessor map;
   private final boolean onlyWithLoadedNeighbours;
   private int i = 0;
   private ChunkAccess chunk;

   public ChunkIterator(boolean onlyWithLoadedNeighbours) {
      this.onlyWithLoadedNeighbours = onlyWithLoadedNeighbours;
      ClientLevel level = MeteorClient.mc.level;
      if (level != null && level.getChunkSource() instanceof ClientChunkManagerAccessor chunkManager) {
         this.map = (ClientChunkMapAccessor)(Object)chunkManager.getChunks();
      } else {
         this.map = null;
      }
      this.getNext();
   }

   private ChunkAccess getNext() {
      ChunkAccess prev = this.chunk;
      this.chunk = null;

      if (this.map != null && this.map.meteor$getChunks() != null) {
         while (this.i < this.map.meteor$getChunks().length()) {
            this.chunk = (ChunkAccess)this.map.meteor$getChunks().get(this.i++);
            if (this.chunk != null && (!this.onlyWithLoadedNeighbours || this.isInRadius(this.chunk))) {
               break;
            }
         }
      }

      return prev;
   }

   private boolean isInRadius(ChunkAccess chunk) {
      ClientLevel level = MeteorClient.mc.level;
      if (level == null || level.getChunkSource() == null) return false;
      int x = chunk.getPos().x;
      int z = chunk.getPos().z;
      return level.getChunkSource().hasChunk(x + 1, z)
         && level.getChunkSource().hasChunk(x - 1, z)
         && level.getChunkSource().hasChunk(x, z + 1)
         && level.getChunkSource().hasChunk(x, z - 1);
   }

   @Override
   public boolean hasNext() {
      return this.chunk != null;
   }

   @Override
   public ChunkAccess next() {
      if (!hasNext()) {
         throw new NoSuchElementException();
      }
      return this.getNext();
   }
}
