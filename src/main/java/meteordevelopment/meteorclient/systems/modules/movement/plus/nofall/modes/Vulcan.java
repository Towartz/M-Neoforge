package meteordevelopment.meteorclient.systems.modules.movement.plus.nofall.modes;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.PlayerMoveC2SPacketAccessor;
import meteordevelopment.meteorclient.systems.modules.movement.plus.nofall.NoFallMode;
import meteordevelopment.meteorclient.systems.modules.movement.plus.nofall.NoFallModes;
import meteordevelopment.meteorclient.utils.plus.PlusMovementUtils;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class Vulcan extends NoFallMode {
    private boolean vulCanNoFall = false;
    private boolean vulCantNoFall = false;
    private boolean nextSpoof = false;
    private boolean doSpoof = false;

    public Vulcan() {
        super(NoFallModes.Vulcan);
    }

    @Override
    public void onActivate() {
        this.vulCanNoFall = false;
        this.vulCantNoFall = false;
        this.nextSpoof = false;
        this.doSpoof = false;
    }

    @Override
    public void onTickEventPre(TickEvent.Pre event) {
        if (mc.player == null) return;
        if (!this.vulCanNoFall && mc.player.fallDistance > 3.25F) {
            this.vulCanNoFall = true;
        }

        if (this.vulCanNoFall && mc.player.onGround() && this.vulCantNoFall) {
            this.vulCantNoFall = false;
        }

        if (!this.vulCantNoFall) {
            if (this.nextSpoof) {
                mc.player.setDeltaMovement(mc.player.getDeltaMovement().add(0.0, -0.1, 0.0));
                mc.player.fallDistance = -0.1F;
                PlusMovementUtils.strafe(0.3F);
                this.nextSpoof = false;
            }

            if (mc.player.fallDistance > 3.5625F) {
                mc.player.fallDistance = 0.0F;
                this.doSpoof = true;
                this.nextSpoof = true;
            }
        }
    }

    @Override
    public void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof ServerboundMovePlayerPacket packet && mc.player != null) {
            PlayerMoveC2SPacketAccessor accessor = (PlayerMoveC2SPacketAccessor) packet;
            accessor.setOnGround(true);
            this.doSpoof = false;
            accessor.setY((double) Math.round(mc.player.getY() * 2.0) / 2.0);
            mc.player.setPos(mc.player.getX(), packet.getY(mc.player.getY()), mc.player.getZ());
        }
    }
}
