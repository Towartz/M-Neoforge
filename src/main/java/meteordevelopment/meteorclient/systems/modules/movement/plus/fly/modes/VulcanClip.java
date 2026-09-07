package meteordevelopment.meteorclient.systems.modules.movement.plus.fly.modes;

import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.systems.modules.movement.plus.fly.FlyMode;
import meteordevelopment.meteorclient.systems.modules.movement.plus.fly.FlyModes;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;

public class VulcanClip extends FlyMode {
    private boolean waitFlag = false;
    private boolean canGlide = false;
    private int ticks = 0;
    private Timer timer;

    public VulcanClip() {
        super(FlyModes.Vulcan_Clip);
    }

    @Override
    public void onDeactivate() {
        if (this.timer != null) this.timer.setOverride(1.0);
    }

    @Override
    public void onActivate() {
        this.timer = (Timer) Modules.get().get(Timer.class);
        if (mc.player != null && mc.player.onGround() && this.settings.canClip.get()) {
            this.clip(0.0F, -0.1F);
            this.waitFlag = true;
            this.canGlide = false;
            this.ticks = 0;
            if (this.timer != null) this.timer.setOverride(0.1F);
        } else {
            this.waitFlag = false;
            this.canGlide = true;
        }
    }

    @Override
    public void onPlayerMoveSendPre(SendMovementPacketsEvent.Pre event) {
        if (this.canGlide && mc.player != null) {
            if (this.timer != null) this.timer.setOverride(1.0);
            Vec3 velocity = mc.player.getDeltaMovement();
            velocity = velocity.add(0.0, -(this.ticks % 2 == 0 ? 0.17 : 0.1), 0.0);
            if (this.ticks == 0) {
                velocity = velocity.add(0.0, -0.07, 0.0);
            }
            mc.player.setDeltaMovement(velocity);
            this.ticks++;
        }
    }

    @Override
    public void onRecivePacket(PacketEvent.Receive event) {
        if (event.packet instanceof ClientboundPlayerPositionPacket packet && this.waitFlag && mc.player != null) {
            Vec3 playerPos = mc.player.position();
            this.waitFlag = false;
            mc.player.setPos(packet.getX(), packet.getY(), packet.getZ());
            mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(playerPos.x, playerPos.y, playerPos.z, false));
            event.cancel();
            mc.player.resetFallDistance();
            this.clip(0.127318F, 0.0F);
            this.clip(3.425559F, 3.7F);
            this.clip(3.14285F, 3.54F);
            this.clip(2.88522F, 3.4F);
            this.canGlide = true;
        }
    }

    private void clip(float dist, float y) {
        if (mc.player == null) return;
        double yaw = Math.toRadians(mc.player.getYRot());
        double x = -Math.sin(yaw) * (double) dist;
        double z = Math.cos(yaw) * (double) dist;
        mc.player.setPos(mc.player.getX() + x, mc.player.getY() + (double) y, mc.player.getZ() + z);
        mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY(), mc.player.getZ(), false));
    }
}
