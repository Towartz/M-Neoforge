package meteordevelopment.meteorclient.systems.modules.player.autocraft;

public class StateTimeoutGuard {
   private int ticks = 0;
   private int retries = 0;
   private final int maxTicks;
   private final int maxRetries;

   public StateTimeoutGuard(int maxTicks, int maxRetries) {
      this.maxTicks = maxTicks;
      this.maxRetries = maxRetries;
   }

   public StateTimeoutGuard(int maxTicks) {
      this(maxTicks, 0);
   }

   public void tick() {
      this.ticks++;
   }

   public int getTicks() {
      return this.ticks;
   }

   public int getRetries() {
      return this.retries;
   }

   public boolean isTimedOut() {
      return this.ticks >= this.maxTicks;
   }

   public boolean canRetry() {
      return this.retries < this.maxRetries;
   }

   public void retry() {
      this.retries++;
      this.ticks = 0;
   }

   public void reset() {
      this.ticks = 0;
      this.retries = 0;
   }
}
