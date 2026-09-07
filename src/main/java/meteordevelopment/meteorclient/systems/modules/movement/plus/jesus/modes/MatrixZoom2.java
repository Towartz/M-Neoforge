package meteordevelopment.meteorclient.systems.modules.movement.plus.jesus.modes;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.systems.modules.movement.plus.jesus.JesusMode;
import meteordevelopment.meteorclient.systems.modules.movement.plus.jesus.JesusModes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class MatrixZoom2 extends JesusMode {
    private int tick = 0;

    public MatrixZoom2() {
        super(JesusModes.Matrix_Zoom_2);
    }

    @Override
    public void onTickEventPre(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
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

        BlockPos pos = new BlockPos(mc.player.getBlockX(), (int) (mc.player.getY() + 0.005F), mc.player.getBlockZ());
        if (this.isFluid(pos) && !mc.player.horizontalCollision) {
            if (this.tick == 0) {
                ((IVec3d) mc.player.getDeltaMovement()).set(velX, 0.030091, velZ);
                this.tick = 1;
            } else {
                ((IVec3d) mc.player.getDeltaMovement()).set(velX, -0.030091, velZ);
                this.tick = 0;
            }
        }
    }
}
