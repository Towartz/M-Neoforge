package meteordevelopment.meteorclient.systems.modules.movement.plus.elytrafly;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public class ElytraFlyMode {
    protected final Minecraft mc;
    protected final ElytraFlyPlus elytraFly = (ElytraFlyPlus) Modules.get().get(ElytraFlyPlus.class);
    private final ElytraFlyModes type;
    protected boolean lastJumpPressed;
    protected boolean incrementJumpTimer;
    protected boolean lastForwardPressed;
    protected int jumpTimer;
    public double velX;
    public double velY;
    public double velZ;
    protected double ticksLeft;
    protected Vec3 forward;
    protected Vec3 right;
    protected double acceleration;

    public ElytraFlyMode(ElytraFlyModes type) {
        this.mc = Minecraft.getInstance();
        this.type = type;
    }

    public void onTick() {}
    public void onPreTick() {}
    public void onPacketSend(PacketEvent.Send event) {}
    public void onPacketReceive(PacketEvent.Receive event) {}
    public void onPlayerMove(PlayerMoveEvent event) {}

    public void onActivate() {
        this.lastJumpPressed = false;
        this.jumpTimer = 0;
        this.ticksLeft = 0.0;
        this.acceleration = 0.0;
    }

    public void onDeactivate() {}

    public String getHudString() {
        return this.type.name();
    }
}
