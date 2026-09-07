package meteordevelopment.meteorclient.systems.modules.movement.plus.speed;

import meteordevelopment.meteorclient.events.entity.player.JumpVelocityMultiplierEvent;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.systems.modules.movement.plus.speed.modes.AACHop438;
import meteordevelopment.meteorclient.systems.modules.movement.plus.speed.modes.NCPHop;
import meteordevelopment.meteorclient.systems.modules.movement.plus.speed.modes.matrix.Matrix;
import meteordevelopment.meteorclient.systems.modules.movement.plus.speed.modes.matrix.Matrix6_7_0;
import meteordevelopment.meteorclient.systems.modules.movement.plus.speed.modes.matrix.MatrixExploit;
import meteordevelopment.meteorclient.systems.modules.movement.plus.speed.modes.matrix.MatrixExploit2;
import meteordevelopment.meteorclient.systems.modules.movement.plus.speed.modes.vulcan.Vulcan;
import meteordevelopment.meteorclient.systems.modules.movement.plus.speed.modes.vulcan.Vulcan_2_8_6;

public class SpeedPlus extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    public final Setting<SpeedModes> speedMode = sgGeneral.add(new EnumSetting.Builder<SpeedModes>()
        .name("mode")
        .description("The method of applying speed.")
        .defaultValue(SpeedModes.Matrix_Exploit)
        .onModuleActivated(s -> onSpeedModeChanged(s.get()))
        .onChanged(this::onSpeedModeChanged)
        .build()
    );

    public final Setting<Double> speedMatrix = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Speed.")
        .defaultValue(4.0)
        .visible(() -> this.speedMode.get() == SpeedModes.Matrix_Exploit || this.speedMode.get() == SpeedModes.Matrix_Exploit_2)
        .build()
    );

    public final Setting<Double> speedVulcanef2 = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed-effect-2")
        .description("Speed 2 effect.")
        .defaultValue(45.0)
        .max(75.0)
        .sliderRange(0.0, 75.0)
        .visible(() -> this.speedMode.get() == SpeedModes.Vulcan)
        .build()
    );

    public final Setting<Double> speedVulcanef1 = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed-effect-1")
        .description("Speed 1 effect.")
        .defaultValue(45.0)
        .max(75.0)
        .sliderRange(0.0, 75.0)
        .visible(() -> this.speedMode.get() == SpeedModes.Vulcan)
        .build()
    );

    public final Setting<Double> speedVulcanef0 = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed-effect-0")
        .description("Speed 0 effect.")
        .defaultValue(35.0)
        .max(75.0)
        .sliderRange(0.0, 75.0)
        .visible(() -> this.speedMode.get() == SpeedModes.Vulcan)
        .build()
    );

    public final Setting<Boolean> autoSwapVulcan = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-swap")
        .description("Auto swap.")
        .defaultValue(true)
        .visible(() -> this.speedMode.get() == SpeedModes.Vulcan)
        .build()
    );

    private SpeedMode currentMode;

    public SpeedPlus() {
        super(Categories.PlusMovement, "speed+", "Bypass speed");
        this.onSpeedModeChanged(this.speedMode.get());
    }

    @Override
    public void onActivate() {
        if (this.currentMode != null) this.currentMode.onActivate();
    }

    @Override
    public void onDeactivate() {
        if (this.currentMode != null) this.currentMode.onDeactivate();
    }

    @EventHandler
    private void onPreTick(TickEvent.Pre event) {
        if (this.currentMode != null) this.currentMode.onTickEventPre(event);
    }

    @EventHandler
    private void onPostTick(TickEvent.Post event) {
        if (this.currentMode != null) this.currentMode.onTickEventPost(event);
    }

    @EventHandler
    public void onSendPacket(PacketEvent.Send event) {
        if (this.currentMode != null) this.currentMode.onSendPacket(event);
    }

    @EventHandler
    public void onSentPacket(PacketEvent.Sent event) {
        if (this.currentMode != null) this.currentMode.onSentPacket(event);
    }

    @EventHandler
    private void onPlayerMoveEvent(PlayerMoveEvent event) {
        if (this.currentMode != null) this.currentMode.onPlayerMoveEvent(event);
    }

    @EventHandler
    public void onJump(JumpVelocityMultiplierEvent event) {
        if (this.currentMode != null) this.currentMode.onJump(event);
    }

    private void onSpeedModeChanged(SpeedModes mode) {
        switch (mode) {
            case Matrix_Exploit_2 -> this.currentMode = new MatrixExploit2();
            case Matrix_Exploit -> this.currentMode = new MatrixExploit();
            case Matrix_6dot7dot0 -> this.currentMode = new Matrix6_7_0();
            case Matrix -> this.currentMode = new Matrix();
            case AAC_Hop_4dot3dot8 -> this.currentMode = new AACHop438();
            case Vulcan -> this.currentMode = new Vulcan();
            case Vulcan_2dot8dot6 -> this.currentMode = new Vulcan_2_8_6();
            case NCP_Hop -> this.currentMode = new NCPHop();
        }
    }
}
