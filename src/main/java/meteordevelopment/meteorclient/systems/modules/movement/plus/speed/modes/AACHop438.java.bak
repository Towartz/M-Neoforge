package meteordevelopment.meteorclient.systems.modules.movement.plus.speed.modes;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.systems.modules.movement.plus.speed.SpeedMode;
import meteordevelopment.meteorclient.systems.modules.movement.plus.speed.SpeedModes;
import net.minecraft.tags.FluidTags;

public class AACHop438 extends SpeedMode {
    public AACHop438() {
        super(SpeedModes.AAC_Hop_4dot3dot8);
    }

    @Override
    public void onDeactivate() {
        Timer timer = (Timer) Modules.get().get(Timer.class);
        if (timer != null) timer.setOverride(1.0);
    }

    @Override
    public void onTickEventPre(TickEvent.Pre event) {
        if (mc.player == null) return;
        Timer timer = (Timer) Modules.get().get(Timer.class);
        if (timer != null) timer.setOverride(1.0);

        if (PlayerUtils.isMoving()
            && !mc.player.isEyeInFluid(FluidTags.WATER)
            && !mc.player.isInLava()
            && !mc.player.onClimbable()
            && !mc.player.isPassenger()) {
            if (mc.player.onGround()) {
                mc.player.resetFallDistance();
            } else if (mc.player.fallDistance <= 0.1F) {
                if (timer != null) timer.setOverride(1.5);
            } else if (mc.player.fallDistance < 1.3F) {
                if (timer != null) timer.setOverride(0.7);
            } else {
                if (timer != null) timer.setOverride(1.0);
            }
        }
    }
}
