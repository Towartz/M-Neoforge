package meteordevelopment.meteorclient.systems.modules.movement.plus.noslow.modes;

import meteordevelopment.meteorclient.events.entity.player.PlayerUseMultiplierEvent;
import meteordevelopment.meteorclient.systems.modules.movement.plus.noslow.NoSlowMode;
import meteordevelopment.meteorclient.systems.modules.movement.plus.noslow.NoSlowModes;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;

public class Grim extends NoSlowMode {
    public Grim() {
        super(NoSlowModes.Grim_1dot8);
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
                int selected = mc.player.getInventory().selected;
                network.send(new ServerboundSetCarriedItemPacket(selected % 8 + 1));
                network.send(new ServerboundSetCarriedItemPacket(selected));
            }
        }
    }
}
