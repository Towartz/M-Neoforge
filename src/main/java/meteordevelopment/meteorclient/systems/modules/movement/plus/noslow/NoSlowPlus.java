package meteordevelopment.meteorclient.systems.modules.movement.plus.noslow;

import meteordevelopment.meteorclient.events.entity.player.PlayerUseMultiplierEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.systems.modules.movement.plus.noslow.modes.*;

public class NoSlowPlus extends Module {
    public SettingGroup defaultGroup = this.settings.getDefaultGroup();
    public SettingGroup usingItemGroup = this.settings.createGroup("Using item");
    public SettingGroup sneakGroup = this.settings.createGroup("Sneak");
    public SettingGroup otherGroup = this.settings.createGroup("Other");

    private NoSlowMode currentMode;

    public final Setting<NoSlowModes> mode = defaultGroup.add(new EnumSetting.Builder<NoSlowModes>()
        .name("mode")
        .description("The method of applying no slow.")
        .defaultValue(NoSlowModes.Vanila)
        .onModuleActivated(s -> onModeChanged(s.get()))
        .onChanged(this::onModeChanged)
        .build()
    );

    public final Setting<Double> usingForward = usingItemGroup.add(new DoubleSetting.Builder()
        .name("forward-multiplier")
        .defaultValue(1.0)
        .min(0.2)
        .sliderRange(0.2, 1.0)
        .visible(() -> this.mode.get() != NoSlowModes.Matrix)
        .build()
    );

    public final Setting<Double> usingSideways = usingItemGroup.add(new DoubleSetting.Builder()
        .name("sideways-multiplier")
        .defaultValue(1.0)
        .min(0.2)
        .sliderRange(0.2, 1.0)
        .visible(() -> this.mode.get() != NoSlowModes.Matrix)
        .build()
    );

    public final Setting<Double> sneakForward = sneakGroup.add(new DoubleSetting.Builder()
        .name("forward-multiplier")
        .defaultValue(1.0)
        .min(0.2)
        .sliderRange(0.2, 1.0)
        .visible(() -> this.mode.get() != NoSlowModes.Matrix)
        .build()
    );

    public final Setting<Double> sneakSideways = sneakGroup.add(new DoubleSetting.Builder()
        .name("sideways-multiplier")
        .defaultValue(1.0)
        .min(0.2)
        .sliderRange(0.2, 1.0)
        .visible(() -> this.mode.get() != NoSlowModes.Matrix)
        .build()
    );

    public final Setting<Double> otherForward = otherGroup.add(new DoubleSetting.Builder()
        .name("forward-multiplier")
        .defaultValue(1.0)
        .min(0.2)
        .sliderRange(0.2, 1.0)
        .visible(() -> this.mode.get() != NoSlowModes.Matrix)
        .build()
    );

    public final Setting<Double> otherSideways = otherGroup.add(new DoubleSetting.Builder()
        .name("sideways-multiplier")
        .defaultValue(1.0)
        .min(0.2)
        .sliderRange(0.2, 1.0)
        .visible(() -> this.mode.get() != NoSlowModes.Matrix)
        .build()
    );

    public NoSlowPlus() {
        super(Categories.PlusMovement, "no-slow+", "Remove or increase slowness.");
        this.onModeChanged(this.mode.get());
    }

    private void onModeChanged(NoSlowModes mode) {
        switch (mode) {
            case Vanila -> this.currentMode = new Vanila();
            case NCP_Strict -> this.currentMode = new NCPStrict();
            case Grim_1dot8 -> this.currentMode = new Grim();
            case Grim_New -> this.currentMode = new GrimNew();
            case Matrix -> this.currentMode = new Matrix();
        }
    }

    @EventHandler
    private void onUse(PlayerUseMultiplierEvent event) {
        if (this.currentMode != null) this.currentMode.onUse(event);
    }

    @EventHandler
    private void onTickEventPre(TickEvent.Pre event) {
        if (this.currentMode != null) this.currentMode.onTickEventPre(event);
    }

    @Override
    public void onActivate() {
        if (this.currentMode != null) this.currentMode.onActivate();
    }

    @Override
    public void onDeactivate() {
        if (this.currentMode != null) this.currentMode.onDeactivate();
    }
}
