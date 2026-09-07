package meteordevelopment.meteorclient.systems.modules.movement.plus.noslow.modes;

import meteordevelopment.meteorclient.events.entity.player.PlayerUseMultiplierEvent;
import meteordevelopment.meteorclient.systems.modules.movement.plus.noslow.NoSlowMode;
import meteordevelopment.meteorclient.systems.modules.movement.plus.noslow.NoSlowModes;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;

public class NCPStrict extends NoSlowMode {
    public NCPStrict() {
        super(NoSlowModes.NCP_Strict);
    }

    @Override
    public void onUse(PlayerUseMultiplierEvent event) {
        if (mc.player == null) return;
        if (mc.player.isShiftKeyDown()) {
            event.setForward(this.settings.sneakForward.get().floatValue());
            event.setSideways(this.settings.sneakSideways.get().floatValue());
        } else if (mc.player.isUsingItem()) {
            event.setForward(this.settings.usingForward.get().floatValue());
            event.setSideways(this.settings.usingSideways.get().floatValue());
        } else {
            event.setForward(this.settings.otherForward.get().floatValue());
            event.setSideways(this.settings.otherSideways.get().floatValue());
        }

        if (mc.player.isUsingItem()) {
            ClientPacketListener network = mc.getConnection();
            if (network != null) {
                network.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, mc.player.blockPosition(), Direction.DOWN));
            }
        }
    }
}
