package baritone.pathing.calc;

import baritone.api.utils.BetterBlockPos;
import java.util.ArrayList;
import java.util.List;

public final class ScannedNodeTracker {
   private static final ScannedNodeTracker INSTANCE = new ScannedNodeTracker();

   public static ScannedNodeTracker getInstance() {
      return INSTANCE;
   }

   private static final int DEFAULT_CAPACITY = 1000;
   private final BetterBlockPos[] buffer = new BetterBlockPos[DEFAULT_CAPACITY];
   private int head = 0;
   private int size = 0;
   private volatile long lastScanTime = 0L;
   private volatile boolean isCalculating = false;

   private ScannedNodeTracker() {}

   public synchronized void onCalculationStarted() {
      this.isCalculating = true;
      this.lastScanTime = System.currentTimeMillis();
   }

   public synchronized void onCalculationFinished() {
      this.isCalculating = false;
      this.lastScanTime = System.currentTimeMillis();
   }

   public synchronized void addNode(int x, int y, int z) {
      this.buffer[this.head] = new BetterBlockPos(x, y, z);
      this.head = (this.head + 1) % DEFAULT_CAPACITY;
      if (this.size < DEFAULT_CAPACITY) {
         this.size++;
      }
      this.lastScanTime = System.currentTimeMillis();
   }

   public synchronized void clear() {
      this.head = 0;
      this.size = 0;
      this.isCalculating = false;
   }

   public synchronized List<BetterBlockPos> getSnapshot(int maxLimit) {
      int limit = Math.min(this.size, maxLimit);
      List<BetterBlockPos> list = new ArrayList<>(limit);
      int start = (this.head - limit + DEFAULT_CAPACITY) % DEFAULT_CAPACITY;
      for (int i = 0; i < limit; i++) {
         BetterBlockPos pos = this.buffer[(start + i) % DEFAULT_CAPACITY];
         if (pos != null) {
            list.add(pos);
         }
      }
      return list;
   }

   public boolean isCalculating() {
      return this.isCalculating;
   }

   public float getAlpha(long fadeTimeMs) {
      if (this.size == 0) return 0.0F;
      if (this.isCalculating) return 1.0F;
      long elapsed = System.currentTimeMillis() - this.lastScanTime;
      if (elapsed >= fadeTimeMs) return 0.0F;
      return 1.0F - (float)elapsed / (float)fadeTimeMs;
   }

   public boolean isVisible(long fadeTimeMs) {
      return this.size > 0 && (this.isCalculating || (System.currentTimeMillis() - this.lastScanTime) < fadeTimeMs);
   }
}
