package meteordevelopment.meteorclient.systems.modules.movement.plus.speed.modes.matrix;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.systems.modules.movement.plus.speed.SpeedMode;
import meteordevelopment.meteorclient.systems.modules.movement.plus.speed.SpeedModes;
import meteordevelopment.meteorclient.utils.plus.PlusMovementUtils;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;

public class Matrix6_7_0 extends SpeedMode {
    private int noVelocityY = 0;

    public Matrix6_7_0() {
        super(SpeedModes.Matrix_6dot7dot0);
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null) mc.player.getAbilities().setFlyingSpeed(0.02F);
    }

    @Override
    public void onTickEventPre(TickEvent.Pre event) {
        work();
    }

    @Override
    public void onReceivePacket(PacketEvent.Receive event) {
        if (event.packet instanceof ClientboundSetEntityMotionPacket velocity
            && mc.player != null
            && mc.level != null
            && mc.level.getEntity(velocity.getId()) == mc.player) {
            this.noVelocityY = 10;
        }
    }

    private void work() {
        if (mc.player == null) return;
        if (!mc.player.onGround() && this.noVelocityY <= 0) {
            if (mc.player.getDeltaMovement().y > 0.0) {
                mc.player.setDeltaMovement(mc.player.getDeltaMovement().add(0.0, -5.0E-4, 0.0));
            }
            mc.player.setDeltaMovement(mc.player.getDeltaMovement().add(0.0, -0.009400114514191982, 0.0));
        }

        if (!mc.player.onGround() && this.noVelocityY < 8 && PlusMovementUtils.getSpeed() < 0.2177) {
            PlusMovementUtils.strafe(0.2177F);
        }

        if (Math.abs(mc.player.getAbilities().getFlyingSpeed()) < 0.1F) {
            mc.player.getAbilities().setFlyingSpeed(0.026F);
        } else {
            mc.player.getAbilities().setFlyingSpeed(0.0247F);
        }

        if (mc.player.onGround() && PlayerUtils.isMoving()) {
            mc.options.keyJump.setDown(false);
            mc.player.resetFallDistance();
            ((IVec3d) mc.player.getDeltaMovement()).setY(0.4105000114514192);
            if (Math.abs(mc.player.getAbilities().getFlyingSpeed()) < 0.1F) {
                PlusMovementUtils.strafe((float) PlusMovementUtils.getSpeed());
            }
        }

        if (!PlayerUtils.isMoving()) {
            ((IVec3d) mc.player.getDeltaMovement()).setXZ(0.0, 0.0);
        }
    }
}
