package meteordevelopment.meteorclient.systems.modules.movement.speed.modes;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.Anchor;
import meteordevelopment.meteorclient.systems.modules.movement.speed.SpeedMode;
import meteordevelopment.meteorclient.systems.modules.movement.speed.SpeedModes;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import org.joml.Vector2d;

public class Strafe extends SpeedMode {
   private long timer = 0L;

   public Strafe() {
      super(SpeedModes.Strafe);
   }

   @Override
   public void onMove(PlayerMoveEvent event) {
      switch (this.stage) {
         case 0:
            if (PlayerUtils.isMoving()) {
               this.stage++;
               this.speed = 1.18F * this.getDefaultSpeed() - 0.01;
            }
         case 1:
            if (PlayerUtils.isMoving() && this.mc.player.onGround()) {
               ((IVec3d)event.movement).setY(this.getHop(0.40123128));
               this.speed = this.speed * this.settings.ncpSpeed.get();
               this.stage++;
            }
            break;
         case 2:
            this.speed = this.distance - 0.76 * (this.distance - this.getDefaultSpeed());
            this.stage++;
            break;
         case 3:
            if (!this.mc.level.noCollision(this.mc.player.getBoundingBox().move(0.0, this.mc.player.getDeltaMovement().y, 0.0))
               || this.mc.player.verticalCollision && this.stage > 0) {
               this.stage = 0;
            }

            this.speed = this.distance - this.distance / 159.0;
      }

      this.speed = Math.max(this.speed, this.getDefaultSpeed());
      if (this.settings.ncpSpeedLimit.get()) {
         if (System.currentTimeMillis() - this.timer > 2500L) {
            this.timer = System.currentTimeMillis();
         }

         this.speed = Math.min(this.speed, System.currentTimeMillis() - this.timer > 1250L ? 0.44 : 0.43);
      }

      Vector2d change = this.transformStrafe(this.speed);
      double velX = change.x;
      double velZ = change.y;
      Anchor anchor = Modules.get().get(Anchor.class);
      if (anchor.isActive() && anchor.controlMovement) {
         velX = anchor.deltaX;
         velZ = anchor.deltaZ;
      }

      ((IVec3d)event.movement).setXZ(velX, velZ);
   }

   private Vector2d transformStrafe(double speed) {
      float forward = this.mc.player.input.forwardImpulse;
      float side = this.mc.player.input.leftImpulse;
      float yaw = this.mc.player.yRotO + (this.mc.player.getYRot() - this.mc.player.yRotO) * this.mc.getTimer().getGameTimeDeltaPartialTick(true);
      if (forward == 0.0F && side == 0.0F) {
         return new Vector2d(0.0, 0.0);
      } else {
         if (forward != 0.0F) {
            if (side >= 1.0F) {
               yaw += (float)(forward > 0.0F ? -45 : 45);
               side = 0.0F;
            } else if (side <= -1.0F) {
               yaw += (float)(forward > 0.0F ? 45 : -45);
               side = 0.0F;
            }

            if (forward > 0.0F) {
               forward = 1.0F;
            } else if (forward < 0.0F) {
               forward = -1.0F;
            }
         }

         double mx = Math.cos(Math.toRadians((double)(yaw + 90.0F)));
         double mz = Math.sin(Math.toRadians((double)(yaw + 90.0F)));
         double velX = (double)forward * speed * mx + (double)side * speed * mz;
         double velZ = (double)forward * speed * mz - (double)side * speed * mx;
         return new Vector2d(velX, velZ);
      }
   }

   @Override
   public void onTick() {
      this.distance = Math.sqrt(
         (this.mc.player.getX() - this.mc.player.xo) * (this.mc.player.getX() - this.mc.player.xo)
            + (this.mc.player.getZ() - this.mc.player.zo) * (this.mc.player.getZ() - this.mc.player.zo)
      );
   }
}
