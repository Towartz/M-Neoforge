package meteordevelopment.meteorclient.systems.modules.movement.plus.spider;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.phys.Vec3;

public class SpiderPlus extends Module {
    public enum Mode {
        Matrix,
        Vulcan,
        NCP
    }

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Mode> mode = this.sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Climbing bypass mode.")
        .defaultValue(Mode.Matrix)
        .build()
    );

    private int tick = 0;

    public SpiderPlus() {
        super(Categories.PlusMovement, "spider+", "Climb walls with anti-cheat bypasses.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (this.mc.player == null) return;

        if (this.mc.player.horizontalCollision) {
            Vec3 vel = this.mc.player.getDeltaMovement();
            switch (this.mode.get()) {
                case Matrix -> {
                    this.tick++;
                    if (this.tick >= 3) {
                        this.mc.player.setDeltaMovement(vel.x, 0.38, vel.z);
                        this.tick = 0;
                    }
                }
                case Vulcan -> {
                    this.mc.player.setDeltaMovement(vel.x, 0.28, vel.z);
                    this.mc.player.setOnGround(true);
                }
                case NCP -> {
                    this.mc.player.setDeltaMovement(vel.x, 0.20, vel.z);
                }
            }
        } else {
            this.tick = 0;
        }
    }
}
