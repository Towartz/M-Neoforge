package meteordevelopment.meteorclient.systems.modules.movement.plus.fly;

import meteordevelopment.meteorclient.events.entity.DamageEvent;
import meteordevelopment.meteorclient.events.entity.player.CanWalkOnFluidEvent;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.CollisionShapeEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.systems.modules.movement.plus.fly.modes.Damage;
import meteordevelopment.meteorclient.systems.modules.movement.plus.fly.modes.MatrixExploit;
import meteordevelopment.meteorclient.systems.modules.movement.plus.fly.modes.MatrixExploit2;
import meteordevelopment.meteorclient.systems.modules.movement.plus.fly.modes.VulcanClip;

public class FlyPlus extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    public final Setting<FlyModes> flyMode = sgGeneral.add(new EnumSetting.Builder<FlyModes>()
        .name("mode")
        .description("The method of applying fly.")
        .defaultValue(FlyModes.Matrix_Exploit)
        .onModuleActivated(s -> onFlyModeChanged(s.get()))
        .onChanged(this::onFlyModeChanged)
        .build()
    );

    public final Setting<Double> speed_1 = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed-1")
        .description("Fly speed.")
        .defaultValue(1.25)
        .max(2500.0)
        .sliderRange(0.0, 2500.0)
        .visible(() -> this.flyMode.get() == FlyModes.Matrix_Exploit)
        .build()
    );

    public final Setting<Double> speed2 = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed-2")
        .description("Fly speed.")
        .defaultValue(0.3)
        .max(5.0)
        .sliderRange(0.0, 5.0)
        .visible(() -> this.flyMode.get() == FlyModes.Matrix_Exploit_2)
        .build()
    );

    public final Setting<Double> speedDamage = sgGeneral.add(new DoubleSetting.Builder()
        .name("damage-fly-speed")
        .description("Fly speed.")
        .defaultValue(1.25)
        .max(2500.0)
        .sliderRange(0.0, 2500.0)
        .onChanged(e -> Damage.speed = e)
        .visible(() -> this.flyMode.get() == FlyModes.Damage)
        .build()
    );

    public final Setting<Double> speedDamageY = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed-y")
        .description("Fly speed Y.")
        .defaultValue(1.25)
        .max(2500.0)
        .sliderRange(0.0, 2500.0)
        .onChanged(e -> Damage.speedUp = e)
        .visible(() -> this.flyMode.get() == FlyModes.Damage)
        .build()
    );

    public final Setting<Integer> speedDamageTicks = sgGeneral.add(new IntSetting.Builder()
        .name("max-ticks")
        .description("Max fly ticks.")
        .defaultValue(15)
        .max(2500)
        .sliderRange(0, 2500)
        .onChanged(e -> Damage.workingTicks = e)
        .visible(() -> this.flyMode.get() == FlyModes.Damage)
        .build()
    );

    public final Setting<Integer> speedUpDamageTicks = sgGeneral.add(new IntSetting.Builder()
        .name("max-up-ticks")
        .description("Max fly ticks.")
        .defaultValue(5)
        .max(2500)
        .sliderRange(0, 2500)
        .onChanged(e -> Damage.workingUpTicks = e)
        .visible(() -> this.flyMode.get() == FlyModes.Damage)
        .build()
    );

    public final Setting<Boolean> canClip = sgGeneral.add(new BoolSetting.Builder()
        .name("can-clip")
        .description("Enable clip in Vulcan mode.")
        .defaultValue(false)
        .visible(() -> this.flyMode.get() == FlyModes.Vulcan_Clip)
        .build()
    );

    public final Setting<Boolean> showInfo = sgGeneral.add(new BoolSetting.Builder()
        .name("show-info")
        .description("Displays information about whether this mode is running on the server.")
        .defaultValue(false)
        .visible(() -> this.flyMode.get() == FlyModes.Vulcan_Clip)
        .build()
    );

    private FlyMode currentMode;

    public FlyPlus() {
        super(Categories.PlusMovement, "flight+", "Bypass fly");
        this.onFlyModeChanged(this.flyMode.get());
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WWidget widget = super.getWidget(theme);
        return (this.flyMode.get() == FlyModes.Vulcan_Clip ? theme.label("This mode works only on 1.8.9 servers") : widget);
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
    public void onRecivePacket(PacketEvent.Receive event) {
        if (this.currentMode != null) this.currentMode.onRecivePacket(event);
    }

    @EventHandler
    public void onCanWalkOnFluid(CanWalkOnFluidEvent event) {
        if (this.currentMode != null) this.currentMode.onCanWalkOnFluid(event);
    }

    @EventHandler
    public void onCollisionShape(CollisionShapeEvent event) {
        if (this.currentMode != null) this.currentMode.onCollisionShape(event);
    }

    @EventHandler
    private void onPlayerMoveEvent(PlayerMoveEvent event) {
        if (this.currentMode != null) this.currentMode.onPlayerMoveEvent(event);
    }

    @EventHandler
    private void onPlayerMoveSendPre(SendMovementPacketsEvent.Pre event) {
        if (this.currentMode != null) this.currentMode.onPlayerMoveSendPre(event);
    }

    @EventHandler
    private void onDamage(DamageEvent event) {
        if (this.currentMode != null) this.currentMode.onDamage(event);
    }

    private void onFlyModeChanged(FlyModes mode) {
        switch (mode) {
            case Matrix_Exploit_2 -> this.currentMode = new MatrixExploit2();
            case Matrix_Exploit -> this.currentMode = new MatrixExploit();
            case Vulcan_Clip -> {
                if (this.showInfo.get()) {
                    this.info("Vulcan fly works on 1.8.9 servers");
                }
                this.currentMode = new VulcanClip();
            }
            case Damage -> this.currentMode = new Damage();
            case Damage_OldFag -> {
                this.currentMode = new Damage();
                Damage.workingUpTicks = 0;
                Damage.workingTicks = 15;
                Damage.speed = 0.396;
                Damage.speedUp = 0.0;
            }
        }
    }
}
