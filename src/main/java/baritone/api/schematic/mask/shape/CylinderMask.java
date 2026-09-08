package baritone.api.schematic.mask.shape;

import baritone.api.schematic.mask.AbstractMask;
import baritone.api.schematic.mask.StaticMask;
import net.minecraft.core.Direction.Axis;

public final class CylinderMask extends AbstractMask implements StaticMask {
   private final double centerA;
   private final double centerB;
   private final double radiusSqA;
   private final double radiusSqB;
   private final boolean filled;
   private final Axis alignment;

   public CylinderMask(int widthX, int heightY, int lengthZ, boolean filled, Axis alignment) {
      super(widthX, heightY, lengthZ);
      this.centerA = (double)getA(widthX, heightY, alignment) / 2.0;
      this.centerB = (double)getB(heightY, lengthZ, alignment) / 2.0;
      this.radiusSqA = (this.centerA - 1.0) * (this.centerA - 1.0);
      this.radiusSqB = (this.centerB - 1.0) * (this.centerB - 1.0);
      this.filled = filled;
      this.alignment = alignment;
   }

   @Override
   public boolean partOfMask(int x, int y, int z) {
      double da = Math.abs((double)getA(x, y, this.alignment) + 0.5 - this.centerA);
      double db = Math.abs((double)getB(y, z, this.alignment) + 0.5 - this.centerB);
      return this.outside(da, db) ? false : this.filled || this.outside(da + 1.0, db) || this.outside(da, db + 1.0);
   }

   private boolean outside(double da, double db) {
      return da * da / this.radiusSqA + db * db / this.radiusSqB > 1.0;
   }

   private static int getA(int x, int y, Axis alignment) {
      return alignment == Axis.X ? y : x;
   }

   private static int getB(int y, int z, Axis alignment) {
      return alignment == Axis.Z ? y : z;
   }
}
