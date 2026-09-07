package meteordevelopment.meteorclient.systems.modules.movement.plus.nofall;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.systems.modules.movement.plus.nofall.modes.*;

public class NoFallPlus extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private NoFallMode currentMode;

    public final Setting<NoFallModes> mode = sgGeneral.add(new EnumSetting.Builder<NoFallModes>()
        .name("mode")
        .description("The method of applying nofall.")
        .defaultValue(NoFallModes.Elytra_Clip)
        .onModuleActivated(s -> onModeChanged(s.get()))
        .onChanged(this::onModeChanged)
        .build()
    );

    public NoFallPlus() {
        super(Categories.PlusMovement, "no-fall+", "Bypass fall damage or reduce fall damage.");
        this.onModeChanged(this.mode.get());
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

    private void onModeChanged(NoFallModes mode) {
        switch (mode) {
            case No_Ground_Elytra -> this.currentMode = new No_Ground_Elytra();
            case No_Ground -> this.currentMode = new No_Ground();
            case Elytra_Fly -> this.currentMode = new ElytraFly();
            case Elytra_Clip -> this.currentMode = new Eclip();
            case Matrix_New -> this.currentMode = new MatrixNew();
            case Verus -> this.currentMode = new Verus();
            case Vulcan -> this.currentMode = new Vulcan();
            case Vulcan_2dot7dot7 -> this.currentMode = new Vulcan277();
        }
    }
}
