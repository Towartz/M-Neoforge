package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.mixininterface.IPlayerInteractEntityC2SPacket;
import meteordevelopment.meteorclient.mixininterface.IPlayerMoveC2SPacket;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundInteractPacket.ActionType;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.MaceItem;

public class MaceDamage extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Mode> mode = this.sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Mode for calculating spoofed height.")
        .defaultValue(Mode.Optimal)
        .build()
    );

    private final Setting<Double> customHeight = this.sgGeneral.add(new DoubleSetting.Builder()
        .name("custom-height")
        .description("Amount of height to spoof.")
        .defaultValue(22.36)
        .min(1.0)
        .sliderRange(1.0, 100.0)
        .visible(() -> this.mode.get() == Mode.Custom)
        .build()
    );

    private final Setting<Boolean> resetGround = this.sgGeneral.add(new BoolSetting.Builder()
        .name("reset-ground")
        .description("Sends a ground-reset packet after the attack.")
        .defaultValue(true)
        .build()
    );

    public MaceDamage() {
        super(Categories.Combat, "mace-damage", "Spoofs fall distance when attacking with a Mace to deal maximum smash damage.");
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof IPlayerInteractEntityC2SPacket packet && packet.getType() == ActionType.ATTACK) {
            if (this.mc.player == null) return;
            if (!(this.mc.player.getMainHandItem().getItem() instanceof MaceItem)) return;
            if (this.mc.player.isFallFlying()) return;

            Entity target = packet.getEntity();
            if (!(target instanceof LivingEntity)) return;

            double height = this.mode.get() == Mode.Optimal ? Math.sqrt(500.0) : this.customHeight.get();

            for (int i = 0; i < 4; i++) {
                this.sendFakeY(0.0, false);
            }
            this.sendFakeY(height, false);
            this.sendFakeY(0.0, false);

            if (this.resetGround.get()) {
                this.sendFakeY(0.0, true);
            }
        }
    }

    private void sendFakeY(double offset, boolean onGround) {
        if (this.mc.player == null) return;
        double x = this.mc.player.getX();
        double y = this.mc.player.getY() + offset;
        double z = this.mc.player.getZ();
        ServerboundMovePlayerPacket packet = new Pos(x, y, z, onGround);
        ((IPlayerMoveC2SPacket) packet).setTag(1337);
        this.mc.player.connection.send(packet);
    }

    public static enum Mode {
        Optimal,
        Custom;
    }
}
