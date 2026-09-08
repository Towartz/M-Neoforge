package dev.babbaj.pathfinder;

import java.lang.reflect.Field;
import sun.misc.Unsafe;

public class Octree {
   private static final Unsafe UNSAFE;
   private static final long X2_INDEX_PTR;
   public static final int SIZEOF_X2 = 1;
   public static final int SIZEOF_X4 = 8;
   public static final int SIZEOF_X8 = 64;
   public static final int SIZEOF_X16 = 512;
   public static final int SIZEOF_CHUNK = 12288;

   public static int x16Index(int var0) {
      return var0 >> 4;
   }

   public static int x8Index(int var0, int var1, int var2) {
      return (var0 & 8) >> 1 | (var1 & 8) >> 2 | (var2 & 8) >> 3;
   }

   public static int x4Index(int var0, int var1, int var2) {
      return var0 & 4 | (var1 & 4) >> 1 | (var2 & 4) >> 2;
   }

   public static int x2Index(int var0, int var1, int var2) {
      return (var0 & 2) << 1 | var1 & 2 | (var2 & 2) >> 1;
   }

   public static int bitIndex(int var0, int var1, int var2) {
      return (var0 & 1) << 2 | (var1 & 1) << 1 | var2 & 1;
   }

   private static long getX2Ptr(long var0, int var2, int var3, int var4) {
      var2 = 3072 * (var2 / 2) + 384 * (var4 / 2) + 2 * (var3 / 2);
      short var6 = UNSAFE.getShort(X2_INDEX_PTR + (long)var2);
      return var0 + (long)var6;
   }

   public static void setBlock(long var0, int var2, int var3, int var4, boolean var5) {
      long var6 = getX2Ptr(var0, var2, var3, var4);
      int var8 = bitIndex(var2, var3, var4);
      byte var1 = UNSAFE.getByte(var6);
      if (var5) {
         var1 = (byte)(var1 | 1 << var8);
      } else {
         var1 = (byte)(var1 & ~(1 << var8));
      }

      UNSAFE.putByte(var6, var1);
   }

   public static void initBlock(long var0, int var2, int var3, int var4, boolean var5) {
      long var6 = getX2Ptr(var0, var2, var3, var4);
      int var8 = bitIndex(var2, var3, var4);
      byte var1 = UNSAFE.getByte(var6);
      var2 = var5 ? 1 : 0;
      var1 = (byte)(var1 | var2 << var8);
      UNSAFE.putByte(var6, var1);
   }

   public static boolean getBlock(long var0, int var2, int var3, int var4) {
      long var5 = getX2Ptr(var0, var2, var3, var4);
      int var7 = bitIndex(var2, var3, var4);
      return (UNSAFE.getByte(var5) >> var7 & 1) != 0;
   }

   static {
      try {
         Field var0;
         (var0 = Unsafe.class.getDeclaredField("theUnsafe")).setAccessible(true);
         UNSAFE = (Unsafe)var0.get(null);
      } catch (ReflectiveOperationException var1) {
         throw new RuntimeException(var1);
      }

      X2_INDEX_PTR = NetherPathfinder.getX2Index();
   }
}
