package meteordevelopment.meteorclient.systems.modules.movement.plus.nofall.modes;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.mixin.PlayerMoveC2SPacketAccessor;
import meteordevelopment.meteorclient.systems.modules.movement.plus.nofall.NoFallMode;
import meteordevelopment.meteorclient.systems.modules.movement.plus.nofall.NoFallModes;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class No_Ground extends NoFallMode {
    public No_Ground() {
        super(NoFallModes.No_Ground);
    }

    @Override
    public void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof ServerboundMovePlayerPacket packet) {
            if (packet.isOnGround()) {
                ((PlayerMoveC2SPacketAccessor) packet).setOnGround(false);
            }
        }
    }
}
