package meteordevelopment.meteorclient.systems.modules.combat.plus.velocity;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.systems.modules.combat.plus.velocity.modes.GrimCancel;
import meteordevelopment.meteorclient.systems.modules.combat.plus.velocity.modes.GrimCancel_v2;
import meteordevelopment.meteorclient.systems.modules.combat.plus.velocity.modes.GrimSkip;

public class VelocityPlus extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    public final Setting<VelocityModes> mode = this.sgGeneral.add(new EnumSetting.Builder<VelocityModes>()
        .name("mode")
        .description("Velocity bypass mode.")
        .defaultValue(VelocityModes.Grim_Cancel)
        .onChanged(this::onModeChanged)
        .build()
    );

    private VelocityMode currentMode;

    public VelocityPlus() {
        super(Categories.PlusCombat, "velocity+", "Anticheat bypass velocity module.");
        this.onModeChanged(this.mode.get());
    }

    private void onModeChanged(VelocityModes mode) {
        switch (mode) {
            case Grim_Cancel -> this.currentMode = new GrimCancel();
            case Grim_Cancel_v2 -> this.currentMode = new GrimCancel_v2();
            case Grim_Skip -> this.currentMode = new GrimSkip();
        }
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
    private void onSendPacket(PacketEvent.Send event) {
        if (this.currentMode != null) this.currentMode.onSendPacket(event);
    }

    @EventHandler
    private void onSentPacket(PacketEvent.Sent event) {
        if (this.currentMode != null) this.currentMode.onSentPacket(event);
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (this.currentMode != null) this.currentMode.onReceivePacket(event);
    }
}
