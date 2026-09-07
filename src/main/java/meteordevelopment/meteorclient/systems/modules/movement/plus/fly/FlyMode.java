package meteordevelopment.meteorclient.systems.modules.movement.plus.fly;

import meteordevelopment.meteorclient.events.entity.DamageEvent;
import meteordevelopment.meteorclient.events.entity.player.CanWalkOnFluidEvent;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.CollisionShapeEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;

public class FlyMode {
    protected final Minecraft mc;
    protected final FlyPlus settings = (FlyPlus) Modules.get().get(FlyPlus.class);
    private final FlyModes type;

    public FlyMode(FlyModes type) {
        this.mc = Minecraft.getInstance();
        this.type = type;
    }

    public void onSendPacket(PacketEvent.Send event) {}
    public void onSentPacket(PacketEvent.Sent event) {}
    public void onRecivePacket(PacketEvent.Receive event) {}
    public void onPlayerMoveEvent(PlayerMoveEvent event) {}
    public void onPlayerMoveSendPre(SendMovementPacketsEvent.Pre event) {}
    public void onCanWalkOnFluid(CanWalkOnFluidEvent event) {}
    public void onCollisionShape(CollisionShapeEvent event) {}
    public void onDamage(DamageEvent event) {}
    public void onTickEventPre(TickEvent.Pre event) {}
    public void onTickEventPost(TickEvent.Post event) {}
    public void onActivate() {}
    public void onDeactivate() {}
}
