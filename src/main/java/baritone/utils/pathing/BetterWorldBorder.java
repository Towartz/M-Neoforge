package baritone.utils.pathing;

import net.minecraft.world.level.border.WorldBorder;

public class BetterWorldBorder {
   private final double minX;
   private final double maxX;
   private final double minZ;
   private final double maxZ;

   public BetterWorldBorder(WorldBorder border) {
      this.minX = border.getMinX();
      this.maxX = border.getMaxX();
      this.minZ = border.getMinZ();
      this.maxZ = border.getMaxZ();
   }

   public boolean entirelyContains(int x, int z) {
      return (double)(x + 1) > this.minX && (double)x < this.maxX && (double)(z + 1) > this.minZ && (double)z < this.maxZ;
   }

   public boolean canPlaceAt(int x, int z) {
      return (double)x > this.minX && (double)(x + 1) < this.maxX && (double)z > this.minZ && (double)(z + 1) < this.maxZ;
   }
}
