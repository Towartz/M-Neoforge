package meteordevelopment.meteorclient.systems.modules.movement.plus.jesus.modes;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.systems.modules.movement.plus.jesus.JesusMode;
import meteordevelopment.meteorclient.systems.modules.movement.plus.jesus.JesusModes;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.Vec3;

public class NCP extends JesusMode {
    float newSpeed = 0.0F;

    public NCP() {
        super(JesusModes.NCP);
    }

    @Override
    public void onPlayerMoveEvent(PlayerMoveEvent event) {
        if (mc.player == null || mc.level == null) return;
        mc.player.setSprinting(false);
        if (mc.player.isInWater() || mc.player.isInLava()) {
            Vec3 velocity = mc.player.getDeltaMovement();
            boolean isLiquidAbove = mc.level.getBlockState(mc.player.blockPosition().above()).getBlock() instanceof LiquidBlock;

            if (mc.options.keyJump.isDown() && !mc.player.isShiftKeyDown() && !isLiquidAbove) {
                mc.player.setDeltaMovement(velocity.x, 0.12, velocity.z);
            }

            velocity = mc.player.getDeltaMovement();
            if (mc.options.keyShift.isDown()) {
                mc.player.setDeltaMovement(velocity.x, -0.12, velocity.z);
            }

            velocity = mc.player.getDeltaMovement();
            if (isLiquidAbove && mc.options.keyJump.isDown()) {
                mc.player.setOnGround(true);
                mc.player.setDeltaMovement(velocity.x, 0.12, velocity.z);
            }

            float yaw = mc.player.getYRot();
            Vec3 forward = Vec3.directionFromRotation(0.0F, yaw);
            Vec3 right = Vec3.directionFromRotation(0.0F, yaw + 90.0F);
            double velX = 0.0;
            double velZ = 0.0;
            double s = 0.5;
            double speedValue = this.settings.speed.get();

            if (mc.options.keyUp.isDown()) {
                velX += forward.x * s * speedValue;
                velZ += forward.z * s * speedValue;
            }
            if (mc.options.keyDown.isDown()) {
                velX -= forward.x * s * speedValue;
                velZ -= forward.z * s * speedValue;
            }
            if (mc.options.keyRight.isDown()) {
                velX += right.x * s * speedValue;
                velZ += right.z * s * speedValue;
            }
            if (mc.options.keyLeft.isDown()) {
                velX -= right.x * s * speedValue;
                velZ -= right.z * s * speedValue;
            }

            double limit = this.settings.limit_speed.get();
            if (limit > 0) {
                if (velX >= limit) velX = limit;
                if (velZ >= limit) velZ = limit;
            }

            ((IVec3d) mc.player.getDeltaMovement()).set(velX, 0.0, velZ);
            mc.player.setOnGround(true);
        }
    }

    @Override
    public void onDeactivate() {
        this.newSpeed = 0.6F;
        super.onDeactivate();
    }

    @Override
    public void onActivate() {
        this.newSpeed = 0.6F + this.settings.speed.get().floatValue();
        super.onActivate();
    }
}
