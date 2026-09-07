package meteordevelopment.meteorclient.systems.modules.movement.plus.fastladder;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.systems.modules.movement.plus.fastladder.modes.Spartan;

public class FastLadderPlus extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    public final Setting<FastLadderModes> spiderMode = sgGeneral.add(new EnumSetting.Builder<FastLadderModes>()
        .name("mode")
        .description("The method of applying fast climb.")
        .defaultValue(FastLadderModes.Spartan)
        .onModuleActivated(s -> onSpiderModeChanged(s.get()))
        .onChanged(this::onSpiderModeChanged)
        .build()
    );

    private FastLadderMode currentMode;

    public FastLadderPlus() {
        super(Categories.PlusMovement, "fast-climb+", "Bypass fast-ladder");
        onSpiderModeChanged(this.spiderMode.get());
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

    private void onSpiderModeChanged(FastLadderModes mode) {
        if (mode == FastLadderModes.Spartan) {
            this.currentMode = new Spartan();
        }
    }
}
