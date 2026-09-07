package meteordevelopment.meteorclient.systems.modules.combat.plus.velocity.modes;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.systems.modules.combat.plus.velocity.VelocityMode;
import meteordevelopment.meteorclient.systems.modules.combat.plus.velocity.VelocityModes;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;

public class GrimCancel extends VelocityMode {
    private boolean canCancel = false;

    public GrimCancel() {
        super(VelocityModes.Grim_Cancel);
    }

    @Override
    public void onActivate() {
        this.canCancel = false;
    }

    @Override
    public void onDeactivate() {
        this.canCancel = false;
    }

    @Override
    public void onReceivePacket(PacketEvent.Receive event) {
        if (this.mc.player == null) return;
        Packet<?> packet = event.packet;

        if (packet instanceof ClientboundHurtAnimationPacket tilt && tilt.id() == this.mc.player.getId()) {
            this.canCancel = true;
        }

        if (((packet instanceof ClientboundSetEntityMotionPacket motion && motion.getId() == this.mc.player.getId()) || packet instanceof ClientboundExplodePacket) && this.canCancel) {
            event.cancel();
            MeteorExecutor.execute(() -> {
                try {
                    Thread.sleep(20L);
                } catch (Exception ignored) {}

                if (this.mc.getConnection() != null && this.mc.player != null) {
                    this.mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                        this.mc.player.getX(),
                        this.mc.player.getY(),
                        this.mc.player.getZ(),
                        this.mc.player.getYRot(),
                        this.mc.player.getXRot(),
                        this.mc.player.onGround()
                    ));
                    this.mc.getConnection().send(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                        this.mc.player.blockPosition(),
                        Direction.DOWN
                    ));
                }
                this.canCancel = false;
            });
        }
    }
}
