package meteordevelopment.meteorclient.systems.modules.movement.plus.nofall.modes;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.mixin.PlayerMoveC2SPacketAccessor;
import meteordevelopment.meteorclient.systems.modules.movement.plus.nofall.NoFallMode;
import meteordevelopment.meteorclient.systems.modules.movement.plus.nofall.NoFallModes;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;

public class Verus extends NoFallMode {
    public Verus() {
        super(NoFallModes.Verus);
    }

    @Override
    public void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof ServerboundMovePlayerPacket packet && mc.player != null) {
            PlayerMoveC2SPacketAccessor accessor = (PlayerMoveC2SPacketAccessor) packet;
            if (mc.player.fallDistance > 3.35F) {
                accessor.setOnGround(true);
                mc.player.fallDistance = 0.0F;
                Vec3 vel = mc.player.getDeltaMovement();
                mc.player.setDeltaMovement(vel.x, 0.0, vel.z);
            }
        }
    }
}
