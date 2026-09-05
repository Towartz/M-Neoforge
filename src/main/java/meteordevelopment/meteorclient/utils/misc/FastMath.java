package meteordevelopment.meteorclient.utils.misc;

import net.minecraft.util.Mth;

public final class FastMath {
   public static final double PI = Math.PI;
   public static final double PI2 = Math.PI * 2.0;
   public static final double HALF_PI = Math.PI * 0.5;
   public static final double RAD_TO_DEG = 180.0 / Math.PI;
   public static final double DEG_TO_RAD = Math.PI / 180.0;
   public static final float RAD_TO_DEG_F = (float)(180.0 / Math.PI);
   public static final float DEG_TO_RAD_F = (float)(Math.PI / 180.0);

   private FastMath() {
   }

   // Inlined Powers & Squares
   public static double sqr(double v) {
      return v * v;
   }

   public static float sqr(float v) {
      return v * v;
   }

   public static int sqr(int v) {
      return v * v;
   }

   public static double cube(double v) {
      return v * v * v;
   }

   public static float cube(float v) {
      return v * v * v;
   }

   public static int cube(int v) {
      return v * v * v;
   }

   // Fast Hypotenuse & Distances
   public static double hypotSq(double dx, double dz) {
      return dx * dx + dz * dz;
   }

   public static double hypot(double dx, double dz) {
      return Math.sqrt(dx * dx + dz * dz);
   }

   public static double distSq(double x1, double y1, double z1, double x2, double y2, double z2) {
      double dx = x2 - x1;
      double dy = y2 - y1;
      double dz = z2 - z1;
      return dx * dx + dy * dy + dz * dz;
   }

   public static double dist(double x1, double y1, double z1, double x2, double y2, double z2) {
      return Math.sqrt(distSq(x1, y1, z1, x2, y2, z2));
   }

   public static boolean isWithin(double x1, double y1, double z1, double x2, double y2, double z2, double range) {
      return distSq(x1, y1, z1, x2, y2, z2) <= sqr(range);
   }

   // Fast Trigonometry & Angle Math
   public static double atan2Deg(double y, double x) {
      return Math.atan2(y, x) * RAD_TO_DEG;
   }

   public static float atan2DegF(double y, double x) {
      return (float)(Math.atan2(y, x) * RAD_TO_DEG);
   }

   public static float sin(float rad) {
      return Mth.sin(rad);
   }

   public static float cos(float rad) {
      return Mth.cos(rad);
   }

   public static float wrapDegrees(float degrees) {
      return Mth.wrapDegrees(degrees);
   }

   public static double wrapDegrees(double degrees) {
      return Mth.wrapDegrees(degrees);
   }

   // Inlined Clamping & Interpolation
   public static double clamp(double val, double min, double max) {
      if (val < min) return min;
      return val > max ? max : val;
   }

   public static float clamp(float val, float min, float max) {
      if (val < min) return min;
      return val > max ? max : val;
   }

   public static int clamp(int val, int min, int max) {
      if (val < min) return min;
      return val > max ? max : val;
   }

   public static double lerp(double delta, double start, double end) {
      return start + delta * (end - start);
   }

   public static float lerp(float delta, float start, float end) {
      return start + delta * (end - start);
   }
}
