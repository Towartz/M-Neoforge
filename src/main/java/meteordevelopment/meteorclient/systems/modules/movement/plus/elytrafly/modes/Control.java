package meteordevelopment.meteorclient.systems.modules.movement.plus.elytrafly.modes;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.systems.modules.movement.plus.elytrafly.ElytraFlyMode;
import meteordevelopment.meteorclient.systems.modules.movement.plus.elytrafly.ElytraFlyModes;

public class Control extends ElytraFlyMode {
    private boolean moving;
    private float yaw;
    private float pitch;
    private float p;
    private double velocity;

    public Control() {
        super(ElytraFlyModes.Control);
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event) {
        if (mc.player != null && mc.player.isFallFlying()) {
            this.updateControlMovement();
            this.pitch = 0.0F;
            boolean movingUp = false;
            if (!mc.options.keyShift.isDown() && mc.options.keyJump.isDown() && this.velocity > elytraFly.speed_control.get() * 0.4) {
                this.p = (float) Math.min((double) this.p + 0.1 * (1.0F - this.p) * (1.0F - this.p) * (1.0F - this.p), 1.0);
                this.pitch = Math.max(Math.max(this.p, 0.0F) * -90.0F, -90.0F);
                movingUp = true;
                this.moving = false;
            } else {
                this.velocity = elytraFly.speed_control.get();
                this.p = -0.2F;
            }

            this.velocity = this.moving
                ? elytraFly.speed_control.get()
                : Math.min(this.velocity + Math.sin(Math.toRadians(this.pitch)) * 0.08, elytraFly.speed_control.get());

            double cos = Math.cos(Math.toRadians(this.yaw + 90.0F));
            double sin = Math.sin(Math.toRadians(this.yaw + 90.0F));
            double x = this.moving && !movingUp
                ? cos * elytraFly.speed_control.get()
                : (movingUp ? this.velocity * Math.cos(Math.toRadians(this.pitch)) * cos : 0.0);
            double y = this.pitch < 0.0F
                ? this.velocity * elytraFly.upMultiplier_control.get() * -Math.sin(Math.toRadians(this.pitch)) * this.velocity
                : -elytraFly.fallSpeed_control.get();
            double z = this.moving && !movingUp
                ? sin * elytraFly.speed_control.get()
                : (movingUp ? this.velocity * Math.cos(Math.toRadians(this.pitch)) * sin : 0.0);

            y *= Math.abs(Math.sin(Math.toRadians(movingUp ? this.pitch : mc.player.getXRot())));
            if (mc.options.keyShift.isDown() && !mc.options.keyJump.isDown()) {
                y = -elytraFly.downSpeed_control.get();
            }

            this.velY = y;
            ((IVec3d) event.movement).set(x, y, z);
            if (elytraFly.resetSpeed.get()) {
                mc.player.setDeltaMovement(0.0, 0.0, 0.0);
            }
        }
    }

    private void updateControlMovement() {
        if (mc.player == null) return;
        float y = mc.player.getYRot();
        float f = mc.player.input.forwardImpulse;
        float s = mc.player.input.leftImpulse;
        if (f > 0.0F) {
            this.moving = true;
            y += s > 0.0F ? -45.0F : (s < 0.0F ? 45.0F : 0.0F);
        } else if (f < 0.0F) {
            this.moving = true;
            y += s > 0.0F ? -135.0F : (s < 0.0F ? 135.0F : 180.0F);
        } else {
            this.moving = s != 0.0F;
            y += s > 0.0F ? -90.0F : (s < 0.0F ? 90.0F : 0.0F);
        }
        this.yaw = y;
    }
}
