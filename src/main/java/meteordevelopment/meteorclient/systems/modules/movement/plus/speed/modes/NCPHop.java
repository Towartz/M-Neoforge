package meteordevelopment.meteorclient.systems.modules.movement.plus.speed.modes;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.systems.modules.movement.plus.speed.SpeedMode;
import meteordevelopment.meteorclient.systems.modules.movement.plus.speed.SpeedModes;
import net.minecraft.tags.FluidTags;

public class NCPHop extends SpeedMode {
    public NCPHop() {
        super(SpeedModes.NCP_Hop);
    }

    @Override
    public void onActivate() {
        Timer timer = (Timer) Modules.get().get(Timer.class);
        if (timer != null) timer.setOverride(1.0865F);
    }

    @Override
    public void onDeactivate() {
        Timer timer = (Timer) Modules.get().get(Timer.class);
        if (timer != null) timer.setOverride(1.0);
        if (mc.player != null) mc.player.getAbilities().setFlyingSpeed(0.02F);
    }

    @Override
    public void onTickEventPre(TickEvent.Pre event) {
        if (mc.player == null) return;
        if (!mc.player.isEyeInFluid(FluidTags.WATER) && !mc.player.isInLava() && !mc.player.onClimbable() && !mc.player.isPassenger()) {
            Timer timer = (Timer) Modules.get().get(Timer.class);
            if (PlayerUtils.isMoving() && mc.player.onGround()) {
                mc.player.resetFallDistance();
                mc.player.getAbilities().setFlyingSpeed(0.0223F);
            } else if (timer != null) {
                timer.setOverride(1.0);
            }
        }
    }
}
