package baritone.api.utils;

import javax.annotation.Nonnull;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;

public final class BetterBlockPos extends BlockPos {
   private static final int NUM_X_BITS = 26;
   private static final int NUM_Z_BITS = 26;
   private static final int NUM_Y_BITS = 12;
   private static final int Y_SHIFT = 26;
   private static final int X_SHIFT = 38;
   private static final long X_MASK = 67108863L;
   private static final long Y_MASK = 4095L;
   private static final long Z_MASK = 67108863L;
   public static final BetterBlockPos ORIGIN = new BetterBlockPos(0, 0, 0);
   public final int x;
   public final int y;
   public final int z;

   public BetterBlockPos(int x, int y, int z) {
      super(x, y, z);
      this.x = x;
      this.y = y;
      this.z = z;
   }

   public BetterBlockPos(double x, double y, double z) {
      this(Mth.floor(x), Mth.floor(y), Mth.floor(z));
   }

   public BetterBlockPos(BlockPos pos) {
      this(pos.getX(), pos.getY(), pos.getZ());
   }

   public static BetterBlockPos from(BlockPos pos) {
      return pos == null ? null : new BetterBlockPos(pos);
   }

   public static long longHash(BetterBlockPos pos) {
      return longHash(pos.x, pos.y, pos.z);
   }

   public static long longHash(int x, int y, int z) {
      long hash = 3241L;
      hash = 3457689L * hash + (long)x;
      hash = 8734625L * hash + (long)y;
      return 2873465L * hash + (long)z;
   }

   public boolean equals(Object o) {
      if (o == null) {
         return false;
      } else if (o instanceof BetterBlockPos oth) {
         return oth.x == this.x && oth.y == this.y && oth.z == this.z;
      } else {
         BlockPos oth = (BlockPos)o;
         return oth.getX() == this.x && oth.getY() == this.y && oth.getZ() == this.z;
      }
   }

   public BetterBlockPos above() {
      return new BetterBlockPos(this.x, this.y + 1, this.z);
   }

   public BetterBlockPos above(int amt) {
      return amt == 0 ? this : new BetterBlockPos(this.x, this.y + amt, this.z);
   }

   public BetterBlockPos below() {
      return new BetterBlockPos(this.x, this.y - 1, this.z);
   }

   public BetterBlockPos below(int amt) {
      return amt == 0 ? this : new BetterBlockPos(this.x, this.y - amt, this.z);
   }

   public BetterBlockPos relative(Direction dir) {
      Vec3i vec = dir.getNormal();
      return new BetterBlockPos(this.x + vec.getX(), this.y + vec.getY(), this.z + vec.getZ());
   }

   public BetterBlockPos relative(Direction dir, int dist) {
      if (dist == 0) {
         return this;
      } else {
         Vec3i vec = dir.getNormal();
         return new BetterBlockPos(this.x + vec.getX() * dist, this.y + vec.getY() * dist, this.z + vec.getZ() * dist);
      }
   }

   public BetterBlockPos north() {
      return new BetterBlockPos(this.x, this.y, this.z - 1);
   }

   public BetterBlockPos north(int amt) {
      return amt == 0 ? this : new BetterBlockPos(this.x, this.y, this.z - amt);
   }

   public BetterBlockPos south() {
      return new BetterBlockPos(this.x, this.y, this.z + 1);
   }

   public BetterBlockPos south(int amt) {
      return amt == 0 ? this : new BetterBlockPos(this.x, this.y, this.z + amt);
   }

   public BetterBlockPos east() {
      return new BetterBlockPos(this.x + 1, this.y, this.z);
   }

   public BetterBlockPos east(int amt) {
      return amt == 0 ? this : new BetterBlockPos(this.x + amt, this.y, this.z);
   }

   public BetterBlockPos west() {
      return new BetterBlockPos(this.x - 1, this.y, this.z);
   }

   public BetterBlockPos west(int amt) {
      return amt == 0 ? this : new BetterBlockPos(this.x - amt, this.y, this.z);
   }

   public double distanceSq(BetterBlockPos to) {
      double dx = (double)this.x - (double)to.x;
      double dy = (double)this.y - (double)to.y;
      double dz = (double)this.z - (double)to.z;
      return dx * dx + dy * dy + dz * dz;
   }

   public double distanceTo(BetterBlockPos to) {
      double dx = (double)this.x - (double)to.x;
      double dy = (double)this.y - (double)to.y;
      double dz = (double)this.z - (double)to.z;
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   @Nonnull
   public String toString() {
      return String.format(
         "BetterBlockPos{x=%s,y=%s,z=%s}", SettingsUtil.maybeCensor(this.x), SettingsUtil.maybeCensor(this.y), SettingsUtil.maybeCensor(this.z)
      );
   }

   public static long serializeToLong(int x, int y, int z) {
      return ((long)x & 67108863L) << 38 | ((long)y & 4095L) << 26 | (long)z & 67108863L;
   }

   public static BetterBlockPos deserializeFromLong(long serialized) {
      int x = (int)(serialized << 0 >> 38);
      int y = (int)(serialized << 26 >> 52);
      int z = (int)(serialized << 38 >> 38);
      return new BetterBlockPos(x, y, z);
   }
}
