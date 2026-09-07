package meteordevelopment.meteorclient.pathing;

import baritone.api.pathing.goals.Goal;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

public class GoalDynamicSurface implements Goal {
   private final Integer targetX;
   private final Integer targetZ;
   private final int minSurfaceY;
   private final boolean isTargeted;

   // Thread-safe chunk elevation cache: (chunkX << 32 | (chunkZ & 0xFFFFFFFFL)) -> surfaceY
   private final Long2IntOpenHashMap heightCache = new Long2IntOpenHashMap();
   private final Object cacheLock = new Object();

   public GoalDynamicSurface(int minSurfaceY) {
      this.targetX = null;
      this.targetZ = null;
      this.minSurfaceY = minSurfaceY;
      this.isTargeted = false;
      this.heightCache.defaultReturnValue(Integer.MIN_VALUE);
   }

   public GoalDynamicSurface() {
      this(62);
   }

   public GoalDynamicSurface(int targetX, int targetZ, int minSurfaceY) {
      this.targetX = targetX;
      this.targetZ = targetZ;
      this.minSurfaceY = minSurfaceY;
      this.isTargeted = true;
      this.heightCache.defaultReturnValue(Integer.MIN_VALUE);
   }

   public GoalDynamicSurface(int targetX, int targetZ) {
      this(targetX, targetZ, 62);
   }

   public boolean isTargeted() {
      return this.isTargeted;
   }

   public Integer getTargetX() {
      return this.targetX;
   }

   public Integer getTargetZ() {
      return this.targetZ;
   }

   public int getSurfaceHeight(int x, int z) {
      int cx = x >> 4;
      int cz = z >> 4;
      long key = ((long)cx << 32) | ((long)cz & 0xFFFFFFFFL);

      synchronized (this.cacheLock) {
         int cached = this.heightCache.get(key);
         if (cached != Integer.MIN_VALUE) {
            return cached;
         }
      }

      int h = this.minSurfaceY;
      ClientLevel level = MeteorClient.mc.level;
      if (level != null && level.getChunkSource().hasChunk(cx, cz)) {
         LevelChunk chunk = level.getChunk(cx, cz);
         if (chunk != null) {
            h = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
            if (h < this.minSurfaceY) {
               h = this.minSurfaceY;
            }
         }
      }

      synchronized (this.cacheLock) {
         this.heightCache.put(key, h);
      }
      return h;
   }

   @Override
   public boolean isInGoal(int x, int y, int z) {
      if (this.isTargeted) {
         if (x == this.targetX && z == this.targetZ) {
            int targetY = this.getSurfaceHeight(this.targetX, this.targetZ);
            return y >= targetY && y <= targetY + 2;
         }
         return false;
      }

      if (y < this.minSurfaceY) {
         return false;
      }

      int surfaceY = this.getSurfaceHeight(x, z);
      return y >= surfaceY;
   }

   @Override
   public double heuristic(int x, int y, int z) {
      if (this.isTargeted) {
         int targetY = this.getSurfaceHeight(this.targetX, this.targetZ);
         double dx = x - this.targetX;
         double dy = y - targetY;
         double dz = z - this.targetZ;
         return Math.sqrt(dx * dx + dy * dy + dz * dz);
      }

      int surfaceY = this.getSurfaceHeight(x, z);
      if (y >= surfaceY) {
         return 0.0;
      }
      return (double)(surfaceY - y) * 1.5;
   }

   @Override
   public String toString() {
      if (this.isTargeted) {
         return String.format("GoalDynamicSurface{target=[%d, %d], surfaceY=%d}", this.targetX, this.targetZ, this.getSurfaceHeight(this.targetX, this.targetZ));
      } else {
         return String.format("GoalDynamicSurface{escape, minY=%d}", this.minSurfaceY);
      }
   }
}
