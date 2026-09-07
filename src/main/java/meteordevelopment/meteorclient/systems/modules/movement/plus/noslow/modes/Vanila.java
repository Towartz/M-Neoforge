package meteordevelopment.meteorclient.systems.modules.movement.plus.noslow.modes;

import meteordevelopment.meteorclient.events.entity.player.PlayerUseMultiplierEvent;
import meteordevelopment.meteorclient.systems.modules.movement.plus.noslow.NoSlowMode;
import meteordevelopment.meteorclient.systems.modules.movement.plus.noslow.NoSlowModes;

public class Vanila extends NoSlowMode {
    public Vanila() {
        super(NoSlowModes.Vanila);
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
    }
}
