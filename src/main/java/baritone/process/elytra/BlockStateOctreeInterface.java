package baritone.process.elytra;

import dev.babbaj.pathfinder.NetherPathfinder;
import dev.babbaj.pathfinder.Octree;

public final class BlockStateOctreeInterface {
   private final NetherPathfinderContext context;
   private final long contextPtr;
   private final int minY;
   transient long chunkPtr;
   private int prevChunkX = Integer.MAX_VALUE;
   private int prevChunkZ = Integer.MAX_VALUE;

   public BlockStateOctreeInterface(NetherPathfinderContext context) {
      this.context = context;
      this.contextPtr = context.context;
      this.minY = context.minY;
   }

   public boolean get0(int x, int y, int z) {
      int adjustedY = y - this.minY;
      if (adjustedY >= 0 && adjustedY <= 383) {
         int chunkX = x >> 4;
         int chunkZ = z >> 4;
         if (this.chunkPtr == 0L | (chunkX ^ this.prevChunkX | chunkZ ^ this.prevChunkZ) != 0) {
            this.prevChunkX = chunkX;
            this.prevChunkZ = chunkZ;
            this.chunkPtr = NetherPathfinder.getChunkOrDefault(this.contextPtr, chunkX, chunkZ, true);
         }

         return Octree.getBlock(this.chunkPtr, x & 15, adjustedY, z & 15);
      } else {
         return false;
      }
   }
}
