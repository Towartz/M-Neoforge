package meteordevelopment.meteorclient.pathing;

import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.interfaces.IGoalRenderPos;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

public class GoalDynamicSurface implements Goal, IGoalRenderPos {
   private final Integer targetX;
   private final Integer targetZ;
   private final int minSurfaceY;
   private final boolean isTargeted;
   private final boolean useSkyLightGradient;

   // Thread-safe per-block column elevation cache: (x << 32 | (z & 0xFFFFFFFFL)) -> surfaceY
   private final Long2IntOpenHashMap heightCache = new Long2IntOpenHashMap();
   private final Object cacheLock = new Object();

   public GoalDynamicSurface(int minSurfaceY, boolean useSkyLightGradient) {
      if (MeteorClient.mc.player != null) {
         this.targetX = MeteorClient.mc.player.getBlockX();
         this.targetZ = MeteorClient.mc.player.getBlockZ();
         this.isTargeted = true;
      } else {
         this.targetX = null;
         this.targetZ = null;
         this.isTargeted = false;
      }
      this.minSurfaceY = minSurfaceY;
      this.useSkyLightGradient = useSkyLightGradient;
      this.heightCache.defaultReturnValue(Integer.MIN_VALUE);
   }

   public GoalDynamicSurface(int minSurfaceY) {
      this(minSurfaceY, true);
   }

   public GoalDynamicSurface() {
      this(62, true);
   }

   public GoalDynamicSurface(int targetX, int targetZ, int minSurfaceY, boolean useSkyLightGradient) {
      this.targetX = targetX;
      this.targetZ = targetZ;
      this.minSurfaceY = minSurfaceY;
      this.isTargeted = true;
      this.useSkyLightGradient = useSkyLightGradient;
      this.heightCache.defaultReturnValue(Integer.MIN_VALUE);
   }

   public GoalDynamicSurface(int targetX, int targetZ, int minSurfaceY) {
      this(targetX, targetZ, minSurfaceY, true);
   }

   public GoalDynamicSurface(int targetX, int targetZ) {
      this(targetX, targetZ, 62, true);
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

   public boolean isUsingSkyLightGradient() {
      return this.useSkyLightGradient;
   }

   public int getSurfaceHeight(int x, int z) {
      long key = ((long)x << 32) | ((long)z & 0xFFFFFFFFL);

      synchronized (this.cacheLock) {
         int cached = this.heightCache.get(key);
         if (cached != Integer.MIN_VALUE) {
            return cached;
         }
      }

      int h = this.minSurfaceY;
      ClientLevel level = MeteorClient.mc.level;
      if (level != null) {
         if (level.dimension() == Level.NETHER) {
            // In the Nether, surface is not the Y=127 bedrock roof; target comfortable cavern floor
            h = Math.max(this.minSurfaceY, 65);
         } else {
            int cx = x >> 4;
            int cz = z >> 4;
            if (level.getChunkSource().hasChunk(cx, cz)) {
               LevelChunk chunk = level.getChunk(cx, cz);
               if (chunk != null) {
                  h = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
                  if (h < this.minSurfaceY) {
                     h = this.minSurfaceY;
                  }

                  // Liquid safety: if top block is water (ocean/lake), ascend to breathable water surface
                  BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, h, z);
                  while (h < level.getMaxBuildHeight() && level.getBlockState(m).is(Blocks.WATER)) {
                     h++;
                     m.setY(h);
                  }
               }
            }
         }
      }

      synchronized (this.cacheLock) {
         if (this.heightCache.size() > 8192) {
            this.heightCache.clear();
         }
         this.heightCache.put(key, h);
      }
      return h;
   }

   @Override
   public boolean isInGoal(int x, int y, int z) {
      if (this.isTargeted) {
         if (Math.abs(x - this.targetX) <= 1 && Math.abs(z - this.targetZ) <= 1) {
            int targetY = this.getSurfaceHeight(x, z);
            return y >= targetY && y <= targetY + 2;
         }
         return false;
      }

      ClientLevel level = MeteorClient.mc.level;
      if (level != null && level.dimension() == Level.NETHER) {
         // Nether escape condition: open cavern with safe floor and clearance
         if (y >= this.minSurfaceY && y <= 100) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
            return level.getBlockState(pos).isAir()
               && level.getBlockState(pos.set(x, y + 1, z)).isAir()
               && level.getBlockState(pos.set(x, y + 2, z)).isAir()
               && level.getBlockState(pos.set(x, y - 1, z)).isSolid();
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
      if (this.isTargeted && this.targetX != null && this.targetZ != null) {
         int targetY = this.getSurfaceHeight(this.targetX, this.targetZ);
         return GoalBlock.calculate((double)(x - this.targetX), y - targetY, (double)(z - this.targetZ));
      }

      ClientLevel level = MeteorClient.mc.level;
      if (level != null && level.dimension() == Level.NETHER) {
         int targetY = Math.max(this.minSurfaceY, 65);
         return Math.abs(targetY - y) * 1.5;
      }

      int surfaceY = this.getSurfaceHeight(x, z);
      if (y >= surfaceY) {
         return 0.0;
      }

      return (double)(surfaceY - y) * 1.5;
   }

   @Override
   public BlockPos getGoalPos() {
      if (this.targetX != null && this.targetZ != null) {
         return new BlockPos(this.targetX, this.getSurfaceHeight(this.targetX, this.targetZ), this.targetZ);
      }
      return null;
   }

   @Override
   public String toString() {
      if (this.isTargeted) {
         return String.format("GoalDynamicSurface{target=[%d, %d], surfaceY=%d, skyLightGradient=%b}",
            this.targetX, this.targetZ, this.getSurfaceHeight(this.targetX, this.targetZ), this.useSkyLightGradient);
      } else {
         return String.format("GoalDynamicSurface{escape, minY=%d, skyLightGradient=%b}", this.minSurfaceY, this.useSkyLightGradient);
      }
   }
}
