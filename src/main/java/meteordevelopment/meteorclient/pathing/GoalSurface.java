package meteordevelopment.meteorclient.pathing;

import baritone.api.pathing.goals.Goal;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;

public class GoalSurface implements Goal {
   private final int minSurfaceY;

   public GoalSurface(int minSurfaceY) {
      this.minSurfaceY = minSurfaceY;
   }

   public GoalSurface() {
      this(62);
   }

   @Override
   public boolean isInGoal(int x, int y, int z) {
      if (y < this.minSurfaceY) {
         return false;
      }
      ClientLevel level = MeteorClient.mc.level;
      if (level == null) {
         return y >= this.minSurfaceY;
      }

      // Check surrounding ground level to avoid matching inside 1x1 vertical shafts
      int surroundingGround = Math.max(
         Math.max(level.getHeight(Heightmap.Types.WORLD_SURFACE, x + 2, z), level.getHeight(Heightmap.Types.WORLD_SURFACE, x - 2, z)),
         Math.max(level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z + 2), level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z - 2))
      );
      if (y < surroundingGround - 1) {
         return false;
      }

      BlockPos pos = new BlockPos(x, y, z);
      return level.canSeeSky(pos) || y >= level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
   }

   @Override
   public double heuristic(int x, int y, int z) {
      ClientLevel level = MeteorClient.mc.level;
      int targetY = this.minSurfaceY;
      if (level != null) {
         int surfaceAtXZ = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
         targetY = Math.max(this.minSurfaceY, surfaceAtXZ);
      }
      if (y >= targetY) {
         return 0.0;
      }
      return (double)(targetY - y) * 2.0;
   }

   @Override
   public String toString() {
      return String.format("GoalSurface{minY=%d}", this.minSurfaceY);
   }
}
