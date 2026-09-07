package meteordevelopment.meteorclient.systems.modules.world.plus.timer;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;

public class TimerPlus extends Module {
    public enum Mode {
        NCP,
        NCPv2,
        Vulcan
    }

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Mode> mode = this.sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Bypass profile for timer modification.")
        .defaultValue(Mode.NCP)
        .build()
    );

    private final Setting<Double> boostMultiplier = this.sgGeneral.add(new DoubleSetting.Builder()
        .name("boost-multiplier")
        .description("Multiplier while boosting.")
        .defaultValue(1.5)
        .min(0.1)
        .sliderRange(0.1, 5.0)
        .build()
    );

    private final Setting<Double> boostMultiplierInAir = this.sgGeneral.add(new DoubleSetting.Builder()
        .name("in-air-multiplier")
        .description("Multiplier while airborne.")
        .defaultValue(1.08)
        .min(0.1)
        .sliderRange(0.1, 3.0)
        .build()
    );

    private final Setting<Double> rechargeMultiplier = this.sgGeneral.add(new DoubleSetting.Builder()
        .name("recharge-multiplier")
        .description("Multiplier during recharge cooldown.")
        .defaultValue(0.7)
        .min(0.1)
        .sliderRange(0.1, 1.0)
        .build()
    );

    private final Setting<Integer> boostDuration = this.sgGeneral.add(new IntSetting.Builder()
        .name("boost-duration")
        .description("Boost duration in ticks.")
        .defaultValue(25)
        .min(5)
        .sliderRange(5, 100)
        .build()
    );

    private final Setting<Integer> rechargeDuration = this.sgGeneral.add(new IntSetting.Builder()
        .name("recharge-duration")
        .description("Recharge duration in ticks.")
        .defaultValue(20)
        .min(5)
        .sliderRange(5, 100)
        .build()
    );

    private final Setting<Boolean> onlyInMove = this.sgGeneral.add(new BoolSetting.Builder()
        .name("only-in-move")
        .description("Only accelerates when moving.")
        .defaultValue(true)
        .build()
    );

    public static double chargePercent = 100.0;
    private int currentBoostTicks = 0;
    private int currentRechargeTicks = 0;
    private boolean isRecharging = false;

    public TimerPlus() {
        super(Categories.PlusWorld, "timer+", "Advanced timer modification with anti-cheat bypass profiles and charging.");
    }

    @Override
    public void onActivate() {
        this.currentBoostTicks = this.boostDuration.get();
        this.currentRechargeTicks = 0;
        this.isRecharging = false;
        chargePercent = 100.0;
    }

    @Override
    public void onDeactivate() {
        Timer timer = Modules.get().get(Timer.class);
        if (timer != null) timer.setOverride(1.0);
        chargePercent = 100.0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (this.mc.player == null) return;

        Timer timer = Modules.get().get(Timer.class);
        if (timer == null) return;

        if (this.onlyInMove.get() && !PlayerUtils.isMoving()) {
            timer.setOverride(1.0);
            return;
        }

        if (!this.isRecharging) {
            if (this.currentBoostTicks > 0) {
                this.currentBoostTicks--;
                chargePercent = ((double) this.currentBoostTicks / (double) this.boostDuration.get()) * 100.0;
                double mult = this.mc.player.onGround() ? this.boostMultiplier.get() : this.boostMultiplierInAir.get();
                timer.setOverride(mult);
            } else {
                this.isRecharging = true;
                this.currentRechargeTicks = this.rechargeDuration.get();
            }
        } else {
            if (this.currentRechargeTicks > 0) {
                this.currentRechargeTicks--;
                chargePercent = (1.0 - ((double) this.currentRechargeTicks / (double) this.rechargeDuration.get())) * 100.0;
                timer.setOverride(this.rechargeMultiplier.get());
            } else {
                this.isRecharging = false;
                this.currentBoostTicks = this.boostDuration.get();
                chargePercent = 100.0;
            }
        }
    }
}
