package baritone.behavior.look;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public final class ForkableRandom {
   private static final double DOUBLE_UNIT = 1.110223E-16F;
   private final long[] s;

   public ForkableRandom() {
      this(System.nanoTime() ^ System.currentTimeMillis());
   }

   public ForkableRandom(long seedIn) {
      AtomicLong seed = new AtomicLong(seedIn);
      LongSupplier splitmix64 = () -> {
         long z = seed.addAndGet(-7046029254386353131L);
         z = (z ^ z >>> 30) * -4658895280553007687L;
         z = (z ^ z >>> 27) * -7723592293110705685L;
         return z ^ z >>> 31;
      };
      this.s = new long[]{splitmix64.getAsLong(), splitmix64.getAsLong(), splitmix64.getAsLong(), splitmix64.getAsLong()};
   }

   private ForkableRandom(long[] s) {
      this.s = s;
   }

   public double nextDouble() {
      return (double)(this.next() >>> 11) * 1.110223E-16F;
   }

   public long next() {
      long result = rotl(this.s[0] + this.s[3], 23) + this.s[0];
      long t = this.s[1] << 17;
      this.s[2] = this.s[2] ^ this.s[0];
      this.s[3] = this.s[3] ^ this.s[1];
      this.s[1] = this.s[1] ^ this.s[2];
      this.s[0] = this.s[0] ^ this.s[3];
      this.s[2] = this.s[2] ^ t;
      this.s[3] = rotl(this.s[3], 45);
      return result;
   }

   public ForkableRandom fork() {
      return new ForkableRandom(Arrays.copyOf(this.s, 4));
   }

   private static long rotl(long x, int k) {
      return x << k | x >>> 64 - k;
   }
}
