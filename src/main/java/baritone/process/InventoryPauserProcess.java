package baritone.process;

import baritone.Baritone;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.utils.BaritoneProcessHelper;

public class InventoryPauserProcess extends BaritoneProcessHelper {
   boolean pauseRequestedLastTick;
   boolean safeToCancelLastTick;
   int ticksOfStationary;

   public InventoryPauserProcess(Baritone baritone) {
      super(baritone);
   }

   @Override
   public boolean isActive() {
      return this.ctx.player() != null && this.ctx.world() != null;
   }

   private double motion() {
      return this.ctx.player().getDeltaMovement().multiply(1.0, 0.0, 1.0).length();
   }

   private boolean stationaryNow() {
      return this.motion() < 1.0E-5;
   }

   public boolean stationaryForInventoryMove() {
      this.pauseRequestedLastTick = true;
      return this.safeToCancelLastTick && this.ticksOfStationary > 1;
   }

   @Override
   public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
      this.safeToCancelLastTick = isSafeToCancel;
      if (this.pauseRequestedLastTick) {
         this.pauseRequestedLastTick = false;
         if (this.stationaryNow()) {
            this.ticksOfStationary++;
         }

         return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
      } else {
         this.ticksOfStationary = 0;
         return new PathingCommand(null, PathingCommandType.DEFER);
      }
   }

   @Override
   public void onLostControl() {
   }

   @Override
   public String displayName0() {
      return "inventory pauser";
   }

   @Override
   public double priority() {
      return 5.1;
   }

   @Override
   public boolean isTemporary() {
      return true;
   }
}
