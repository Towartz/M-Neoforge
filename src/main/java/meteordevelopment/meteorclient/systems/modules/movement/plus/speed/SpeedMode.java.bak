package meteordevelopment.meteorclient.systems.modules.movement.plus.speed;

import meteordevelopment.meteorclient.events.entity.player.JumpVelocityMultiplierEvent;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;

public class SpeedMode {
    protected final Minecraft mc;
    protected final SpeedPlus settings = (SpeedPlus) Modules.get().get(SpeedPlus.class);
    private final SpeedModes type;

    public SpeedMode(SpeedModes type) {
        this.mc = Minecraft.getInstance();
        this.type = type;
    }

    public void onReceivePacket(PacketEvent.Receive event) {}
    public void onSendPacket(PacketEvent.Send event) {}
    public void onSentPacket(PacketEvent.Sent event) {}
    public void onPlayerMoveEvent(PlayerMoveEvent event) {}
    public void onTickEventPre(TickEvent.Pre event) {}
    public void onTickEventPost(TickEvent.Post event) {}
    public void onJump(JumpVelocityMultiplierEvent event) {}
    public void onActivate() {}
    public void onDeactivate() {}

    protected double getDefaultSpeed() {
        double defaultSpeed = 0.2873;
        if (mc.player != null) {
            if (mc.player.hasEffect(MobEffects.MOVEMENT_SPEED)) {
                int amplifier = mc.player.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier();
                defaultSpeed *= 1.0 + 0.2 * (double) (amplifier + 1);
            }
            if (mc.player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                int amplifier = mc.player.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier();
                defaultSpeed /= 1.0 + 0.2 * (double) (amplifier + 1);
            }
        }
        return defaultSpeed;
    }
}
