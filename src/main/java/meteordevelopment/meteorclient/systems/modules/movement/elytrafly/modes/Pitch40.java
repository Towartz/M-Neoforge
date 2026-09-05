package meteordevelopment.meteorclient.systems.modules.movement.elytrafly.modes;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFlightMode;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFlightModes;

public class Pitch40 extends ElytraFlightMode {
   private boolean pitchingDown = true;
   private int pitch;

   public Pitch40() {
      super(ElytraFlightModes.Pitch40);
   }

   @Override
   public void onActivate() {
      if (this.mc.player.getY() < this.elytraFly.pitch40upperBounds.get()) {
         this.elytraFly.error("Player must be above upper bounds!", new Object[0]);
         this.elytraFly.toggle();
      }

      this.pitch = 40;
   }

   @Override
   public void onDeactivate() {
   }

   @Override
   public void onTick() {
      super.onTick();
      if (this.pitchingDown && this.mc.player.getY() <= this.elytraFly.pitch40lowerBounds.get()) {
         this.pitchingDown = false;
      } else if (!this.pitchingDown && this.mc.player.getY() >= this.elytraFly.pitch40upperBounds.get()) {
         this.pitchingDown = true;
      }

      if (!this.pitchingDown && this.mc.player.getXRot() > -40.0F) {
         this.pitch = (int)((double)this.pitch - this.elytraFly.pitch40rotationSpeed.get());
         if (this.pitch < -40) {
            this.pitch = -40;
         }
      } else if (this.pitchingDown && this.mc.player.getXRot() < 40.0F) {
         this.pitch = (int)((double)this.pitch + this.elytraFly.pitch40rotationSpeed.get());
         if (this.pitch > 40) {
            this.pitch = 40;
         }
      }

      this.mc.player.setXRot((float)this.pitch);
   }

   @Override
   public void autoTakeoff() {
   }

   @Override
   public void handleHorizontalSpeed(PlayerMoveEvent event) {
      this.velX = event.movement.x;
      this.velZ = event.movement.z;
   }

   @Override
   public void handleVerticalSpeed(PlayerMoveEvent event) {
   }

   @Override
   public void handleFallMultiplier() {
   }

   @Override
   public void handleAutopilot() {
   }
}
