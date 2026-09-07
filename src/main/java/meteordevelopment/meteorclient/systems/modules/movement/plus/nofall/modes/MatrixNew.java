package meteordevelopment.meteorclient.systems.modules.movement.plus.nofall.modes;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.mixin.PlayerMoveC2SPacketAccessor;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.systems.modules.movement.plus.nofall.NoFallMode;
import meteordevelopment.meteorclient.systems.modules.movement.plus.nofall.NoFallModes;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MatrixNew extends NoFallMode {
    private Timer timer;

    public MatrixNew() {
        super(NoFallModes.Matrix_New);
    }

    @Override
    public void onDeactivate() {
        this.timer = (Timer) Modules.get().get(Timer.class);
        if (this.timer != null) this.timer.setOverride(1.0);
    }

    @Override
    public void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof ServerboundMovePlayerPacket packet && mc.player != null && mc.level != null) {
            PlayerMoveC2SPacketAccessor accessor = (PlayerMoveC2SPacketAccessor) packet;
            this.timer = (Timer) Modules.get().get(Timer.class);

            if (!mc.player.onGround()) {
                if (mc.player.fallDistance > 2.69F) {
                    if (this.timer != null) this.timer.setOverride(0.3);
                    accessor.setOnGround(true);
                    mc.player.fallDistance = 0.0F;
                }

                if (mc.player.fallDistance > 3.5F) {
                    if (this.timer != null) this.timer.setOverride(0.3);
                } else {
                    if (this.timer != null) this.timer.setOverride(1.0);
                }
            }

            boolean isEmpty = true;
            for (VoxelShape shape : mc.level.getBlockCollisions(mc.player, mc.player.getBoundingBox().expandTowards(0.0, mc.player.getDeltaMovement().y, 0.0))) {
                isEmpty = shape.isEmpty();
            }

            if (!isEmpty && !packet.isOnGround() && mc.player.getDeltaMovement().y < -0.6) {
                accessor.setOnGround(true);
            }
        }
    }
}
