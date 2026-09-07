package meteordevelopment.meteorclient.systems.modules.movement.plus.jesus;

import meteordevelopment.meteorclient.events.entity.player.CanWalkOnFluidEvent;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.CollisionShapeEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.systems.modules.movement.plus.jesus.modes.MatrixZoom;
import meteordevelopment.meteorclient.systems.modules.movement.plus.jesus.modes.MatrixZoom2;
import meteordevelopment.meteorclient.systems.modules.movement.plus.jesus.modes.NCP;
import meteordevelopment.meteorclient.systems.modules.movement.plus.jesus.modes.VulcanExploit;

public class JesusPlus extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    public final Setting<JesusModes> jesusMode = sgGeneral.add(new EnumSetting.Builder<JesusModes>()
        .name("mode")
        .description("The method of applying jesus.")
        .defaultValue(JesusModes.Matrix_Zoom)
        .onModuleActivated(s -> onJesusModeChanged(s.get()))
        .onChanged(this::onJesusModeChanged)
        .build()
    );

    public final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Jesus speed.")
        .defaultValue(1.25)
        .max(2500.0)
        .sliderRange(0.0, 2500.0)
        .build()
    );

    public final Setting<Double> limit_speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("limit-speed")
        .description("Jesus speed limit.")
        .defaultValue(0.5)
        .visible(() -> this.jesusMode.get() == JesusModes.NCP)
        .build()
    );

    public final Setting<Boolean> autoSwapVulcan = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-swap")
        .description("Auto swap elytra.")
        .defaultValue(true)
        .visible(() -> this.jesusMode.get() == JesusModes.Vulcan_Exploit)
        .build()
    );

    public final Setting<Boolean> lava = sgGeneral.add(new BoolSetting.Builder()
        .name("lava")
        .description("Allows walking on lava as well as water.")
        .defaultValue(true)
        .build()
    );

    private JesusMode currentMode;

    public JesusPlus() {
        super(Categories.PlusMovement, "jesus+", "Bypass jesus");
        this.onJesusModeChanged(this.jesusMode.get());
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

    private void onJesusModeChanged(JesusModes mode) {
        switch (mode) {
            case Matrix_Zoom -> this.currentMode = new MatrixZoom();
            case Matrix_Zoom_2 -> this.currentMode = new MatrixZoom2();
            case Vulcan_Exploit -> this.currentMode = new VulcanExploit();
            case NCP -> this.currentMode = new NCP();
        }
    }
}
