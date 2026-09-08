package baritone.launch.mixins;

import baritone.utils.accessor.IChunkArray;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(
   targets = {"net.minecraft.client.multiplayer.ClientChunkCache$Storage"}
)
public abstract class MixinChunkArray implements IChunkArray {
   @Final
   @Shadow
   AtomicReferenceArray<LevelChunk> chunks;
   @Final
   @Shadow
   int chunkRadius;
   @Final
   @Shadow
   private int viewRange;
   @Shadow
   int viewCenterX;
   @Shadow
   int viewCenterZ;
   @Shadow
   int chunkCount;

   @Shadow
   abstract boolean inRange(int var1, int var2);

   @Shadow
   abstract int getIndex(int var1, int var2);

   @Shadow
   protected abstract void replace(int var1, LevelChunk var2);

   @Override
   public int centerX() {
      return this.viewCenterX;
   }

   @Override
   public int centerZ() {
      return this.viewCenterZ;
   }

   @Override
   public int viewDistance() {
      return this.chunkRadius;
   }

   @Override
   public AtomicReferenceArray<LevelChunk> getChunks() {
      return this.chunks;
   }

   @Override
   public void copyFrom(IChunkArray other) {
      this.viewCenterX = other.centerX();
      this.viewCenterZ = other.centerZ();
      AtomicReferenceArray<LevelChunk> copyingFrom = other.getChunks();

      for (int k = 0; k < copyingFrom.length(); k++) {
         LevelChunk chunk = copyingFrom.get(k);
         if (chunk != null) {
            ChunkPos chunkpos = chunk.getPos();
            if (this.inRange(chunkpos.x, chunkpos.z)) {
               int index = this.getIndex(chunkpos.x, chunkpos.z);
               if (this.chunks.get(index) != null) {
                  throw new IllegalStateException("Doing this would mutate the client's REAL loaded chunks?!");
               }

               this.replace(index, chunk);
            }
         }
      }
   }
}
