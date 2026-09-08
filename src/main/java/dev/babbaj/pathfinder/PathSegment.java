package dev.babbaj.pathfinder;

public class PathSegment {
   public final boolean finished;
   public final long[] packed;

   public PathSegment(boolean var1, long[] var2) {
      this.finished = var1;
      this.packed = var2;
   }
}
