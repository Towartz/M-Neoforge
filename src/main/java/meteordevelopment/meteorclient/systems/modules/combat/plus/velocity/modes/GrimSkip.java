package meteordevelopment.meteorclient.systems.modules.combat.plus.velocity.modes;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.systems.modules.combat.plus.velocity.VelocityMode;
import meteordevelopment.meteorclient.systems.modules.combat.plus.velocity.VelocityModes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;

public class GrimSkip extends VelocityMode {
    private int skipTicks = 0;

    public GrimSkip() {
        super(VelocityModes.Grim_Skip);
    }

    @Override
    public void onActivate() {
        this.skipTicks = 0;
    }

    @Override
    public void onReceivePacket(PacketEvent.Receive event) {
        if (this.mc.player == null) return;
        Packet<?> packet = event.packet;

        if (packet instanceof ClientboundHurtAnimationPacket tilt && tilt.id() == this.mc.player.getId()) {
            this.skipTicks = 3;
        }

        if ((packet instanceof ClientboundSetEntityMotionPacket motion && motion.getId() == this.mc.player.getId()) || packet instanceof ClientboundExplodePacket) {
            if (this.skipTicks > 0) {
                event.cancel();
                this.skipTicks--;
            }
        }
    }
}
