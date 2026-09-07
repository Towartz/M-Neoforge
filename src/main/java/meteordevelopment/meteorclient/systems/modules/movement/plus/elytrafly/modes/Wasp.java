package meteordevelopment.meteorclient.systems.modules.movement.plus.elytrafly.modes;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.systems.modules.movement.plus.elytrafly.ElytraFlyMode;
import meteordevelopment.meteorclient.systems.modules.movement.plus.elytrafly.ElytraFlyModes;

public class Wasp extends ElytraFlyMode {
    private boolean moving;
    private float yaw;
    private float pitch;

    public Wasp() {
        super(ElytraFlyModes.Wasp);
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event) {
        if (mc.player != null && mc.player.isFallFlying()) {
            this.updateWaspMovement();
            this.pitch = mc.player.getXRot();
            double cos = Math.cos(Math.toRadians(this.yaw + 90.0F));
            double sin = Math.sin(Math.toRadians(this.yaw + 90.0F));
            double x = this.moving ? cos * elytraFly.horizontal_wasp.get() : 0.0;
            double y = -elytraFly.fallSpeed_wasp.get();
            double z = this.moving ? sin * elytraFly.horizontal_wasp.get() : 0.0;

            if (elytraFly.smartFall_wasp.get()) {
                y *= Math.abs(Math.sin(Math.toRadians(this.pitch)));
            }

            if (mc.options.keyShift.isDown() && !mc.options.keyJump.isDown()) {
                y = -elytraFly.down_wasp.get();
            }

            if (!mc.options.keyShift.isDown() && mc.options.keyJump.isDown()) {
                y = elytraFly.up_wasp.get();
            }

            this.velY = y;
            ((IVec3d) event.movement).set(x, y, z);
            if (elytraFly.resetSpeed.get()) {
                mc.player.setDeltaMovement(0.0, 0.0, 0.0);
            }
        }
    }

    private void updateWaspMovement() {
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
