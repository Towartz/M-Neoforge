package meteordevelopment.meteorclient.systems.modules.movement.plus.noslow.modes;

import meteordevelopment.meteorclient.events.entity.player.PlayerUseMultiplierEvent;
import meteordevelopment.meteorclient.systems.modules.movement.plus.noslow.NoSlowMode;
import meteordevelopment.meteorclient.systems.modules.movement.plus.noslow.NoSlowModes;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;

public class GrimNew extends NoSlowMode {
    public GrimNew() {
        super(NoSlowModes.Grim_New);
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

        InteractionHand hand = mc.player.getUsedItemHand();
        ClientPacketListener network = mc.getConnection();
        if (network != null) {
            if (hand == InteractionHand.MAIN_HAND) {
                network.send(new ServerboundUseItemPacket(InteractionHand.OFF_HAND, 0, 0.0F, 0.0F));
            } else if (hand == InteractionHand.OFF_HAND) {
                int selected = mc.player.getInventory().selected;
                network.send(new ServerboundSetCarriedItemPacket(selected % 8 + 1));
                network.send(new ServerboundSetCarriedItemPacket(selected));
            }
        }
    }
}
