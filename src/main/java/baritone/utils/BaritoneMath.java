package baritone.utils;

public final class BaritoneMath {
   private static final double FLOOR_DOUBLE_D = 1.0737418E9F;
   private static final int FLOOR_DOUBLE_I = 1073741824;

   private BaritoneMath() {
   }

   public static int fastFloor(double v) {
      return (int)(v + 1.0737418E9F) - 1073741824;
   }

   public static int fastCeil(double v) {
      return 1073741824 - (int)(1.0737418E9F - v);
   }
}
