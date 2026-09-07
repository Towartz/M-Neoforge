package meteordevelopment.meteorclient.systems.modules.movement.plus.fastladder.modes;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.PlayerMoveC2SPacketAccessor;
import meteordevelopment.meteorclient.systems.modules.movement.plus.fastladder.FastLadderMode;
import meteordevelopment.meteorclient.systems.modules.movement.plus.fastladder.FastLadderModes;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;

public class Spartan extends FastLadderMode {
    private int tick = 0;
    private boolean modify = false;
    private boolean start = false;
    private double startY = 0.0;
    private double lastY = 0.0;
    private double coff = 3.26E-11;
    private boolean block = false;

    public Spartan() {
        super(FastLadderModes.Spartan);
    }

    @Override
    public void onActivate() {
        this.tick = 0;
        this.start = false;
        this.modify = false;
        if (mc.player != null) {
            this.startY = mc.player.getY();
        }
    }

    private boolean YGround(double height, double min, double max) {
        String yString = String.valueOf(height);
        int idx = yString.indexOf(".");
        if (idx < 0) return false;
        yString = yString.substring(idx);
        double y = Double.parseDouble(yString);
        return y >= min && y <= max;
    }

    private double RGround(double height) {
        String yString = String.valueOf(height);
        int idx = yString.indexOf(".");
        if (idx < 0) return 0.0;
        yString = yString.substring(idx);
        return Double.parseDouble(yString);
    }

    @Override
    public void onSendPacket(PacketEvent.Send event) {
        work(event.packet);
    }

    @Override
    public void onSentPacket(PacketEvent.Sent event) {
        work(event.packet);
    }

    private void work(Packet<?> packet) {
        if (mc.player == null) return;
        if (this.modify) {
            if (packet instanceof ServerboundMovePlayerPacket move) {
                double y = move.getY(mc.player.getY());
                if (this.YGround(y, this.RGround(this.startY) - 0.1, this.RGround(this.startY) + 0.1)) {
                    ((PlayerMoveC2SPacketAccessor) packet).setOnGround(true);
                }

                if (mc.player.onGround() && this.block) {
                    this.block = false;
                    this.startY = mc.player.getY();
                    this.start = false;
                }
            }
        } else {
            if (mc.player.onGround() && this.block) {
                this.block = false;
                this.startY = mc.player.getY();
                this.start = false;
            }
        }
    }

    @Override
    public void onTickEventPre(TickEvent.Pre event) {
        if (mc.player != null && this.modify) {
            double y = mc.player.getY();
            if (this.lastY == y && this.tick > 1) {
                this.block = true;
            } else {
                this.lastY = y;
            }
        }
    }

    @Override
    public void onTickEventPost(TickEvent.Post event) {
        LocalPlayer player = mc.player;
        if (player == null) return;

        Vec3 pl_velocity = player.getDeltaMovement();
        if (player.onClimbable()) {
            this.modify = player.horizontalCollision && player.isSuppressingSlidingDownLadder();
            if (player.onGround()) {
                this.block = false;
                this.startY = player.getY();
                this.start = false;
            }

            if (player.horizontalCollision) {
                if (!this.start) {
                    this.start = true;
                    this.startY = player.getY();
                    this.lastY = player.getY();
                }

                if (!this.block) {
                    if (this.tick == 0) {
                        player.setDeltaMovement(pl_velocity.x, 0.41999998688698, pl_velocity.z);
                        this.tick = 1;
                    } else if (this.tick == 1) {
                        player.setDeltaMovement(pl_velocity.x, 0.33319999363698 - this.coff, pl_velocity.z);
                        this.tick = 2;
                    } else if (this.tick == 2) {
                        player.setDeltaMovement(pl_velocity.x, 0.24813599862698 - this.coff, pl_velocity.z);
                        this.tick = 0;
                    }
                }
            } else {
                this.modify = false;
                this.tick = 0;
            }
        }
    }
}
