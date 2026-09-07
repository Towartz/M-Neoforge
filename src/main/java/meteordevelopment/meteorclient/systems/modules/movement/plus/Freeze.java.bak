package meteordevelopment.meteorclient.systems.modules.movement.plus;

import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;

public class Freeze extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Boolean> freezeLook = sgGeneral.add(new BoolSetting.Builder()
        .name("freeze-look")
        .description("Freezes your pitch and yaw.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> packet = sgGeneral.add(new BoolSetting.Builder()
        .name("packet-mode")
        .description("Enable packet mode, better.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> freezeLookSilent = sgGeneral.add(new BoolSetting.Builder()
        .name("freeze-look-silent")
        .description("Freezes your pitch and yaw silently.")
        .defaultValue(true)
        .visible(() -> packet.get() && freezeLook.get())
        .build()
    );

    private float yaw = 0.0F;
    private float pitch = 0.0F;
    private Vec3 position = Vec3.ZERO;

    public Freeze() {
        super(Categories.PlusMovement, "freeze", "Freezes your position for server.");
    }

    @Override
    public void onActivate() {
        if (mc.player != null) {
            this.yaw = mc.player.getYRot();
            this.pitch = mc.player.getXRot();
            this.position = mc.player.position();
        }
    }

    private void setFreezeLook(PacketEvent.Send event, ServerboundMovePlayerPacket playerMove) {
        if (playerMove.hasRotation() && freezeLook.get() && freezeLookSilent.get()) {
            event.cancel();
        } else if (mc.player != null && playerMove.hasRotation() && freezeLook.get() && !freezeLookSilent.get()) {
            event.cancel();
            mc.player.setYRot(this.yaw);
            mc.player.setXRot(this.pitch);
        }

        if (mc.player != null && playerMove.hasPosition()) {
            mc.player.setDeltaMovement(0.0, 0.0, 0.0);
            mc.player.setPos(this.position.x, this.position.y, this.position.z);
            event.cancel();
        }
    }

    @EventHandler
    private void onMovePacket(PacketEvent.Send event) {
        if (event.packet instanceof ServerboundMovePlayerPacket playerMove && packet.get()) {
            setFreezeLook(event, playerMove);
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (isActive()) toggle();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player != null) {
            mc.player.setDeltaMovement(0.0, 0.0, 0.0);
            mc.player.setPos(this.position.x, this.position.y, this.position.z);
        }
    }

    @EventHandler
    private void onEntityRemoved(EntityRemovedEvent event) {
        if (event.entity == mc.player && isActive()) {
            toggle();
        }
    }
}
