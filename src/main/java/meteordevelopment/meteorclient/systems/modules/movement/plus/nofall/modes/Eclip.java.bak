package meteordevelopment.meteorclient.systems.modules.movement.plus.nofall.modes;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.PlayerMoveC2SPacketAccessor;
import meteordevelopment.meteorclient.mixininterface.IPlayerMoveC2SPacket;
import meteordevelopment.meteorclient.systems.modules.movement.plus.nofall.NoFallMode;
import meteordevelopment.meteorclient.systems.modules.movement.plus.nofall.NoFallModes;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.plus.ElytraUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class Eclip extends NoFallMode {
    private int ticks = 0;
    private int slot = -1;
    private int blocks = 0;
    private boolean cliped = false;
    private boolean groundcheck = false;
    private int teleports = 0;

    public Eclip() {
        super(NoFallModes.Elytra_Clip);
    }

    @Override
    public void onTickEventPre(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        FindItemResult elytra = InvUtils.find(Items.ELYTRA);
        if (!elytra.found()) {
            ChatUtils.error("Elytra not found");
            this.settings.toggle();
        } else {
            if (mc.player.onGround() && this.groundcheck) {
                this.groundcheck = false;
                this.cliped = false;
                ChatUtils.infoPrefix("No Fall Plus", "Grounded in " + this.teleports + " teleports");
                mc.player.fallDistance = 0.0F;
                this.teleports = 0;
            } else if (mc.player.fallDistance > 3.0F) {
                BlockHitResult result = mc.level.clip(
                    new ClipContext(
                        mc.player.position(),
                        mc.player.position().add(0.0, -10.0, 0.0),
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        mc.player
                    )
                );
                if (result != null && result.getType() == HitResult.Type.BLOCK) {
                    this.blocks = result.getBlockPos().above().getY();
                    this.cliped = true;
                } else if (result == null || result.getType() == HitResult.Type.MISS) {
                    this.blocks = (int) mc.player.getY() - 10;
                    this.cliped = true;
                }
            }

            if (this.cliped) {
                this.clip();
            }
        }
    }

    @Override
    public void onSendPacket(PacketEvent.Send event) {
        if (this.groundcheck && event.packet instanceof ServerboundMovePlayerPacket move) {
            if (move instanceof IPlayerMoveC2SPacket iMove && iMove.getTag() != 1337) {
                ((PlayerMoveC2SPacketAccessor) move).setOnGround(true);
            }
        }
    }

    private void clip() {
        if (this.blocks != 0 && mc.player != null) {
            LocalPlayer player = mc.player;
            switch (this.ticks) {
                case 0 -> {
                    FindItemResult elytra = InvUtils.find(Items.ELYTRA);
                    this.slot = elytra.slot();
                    InvUtils.move().from(this.slot).toArmor(2);
                    this.ticks++;
                }
                case 1 -> {
                    this.groundcheck = true;
                    player.connection.send(new ServerboundMovePlayerPacket.StatusOnly(false));
                    this.ticks++;
                }
                case 2 -> {
                    player.connection.send(new ServerboundMovePlayerPacket.StatusOnly(false));
                    this.ticks++;
                }
                case 3 -> {
                    ElytraUtils.startFly();
                    this.ticks++;
                }
                case 4 -> {
                    player.setPos(player.getX(), (double) this.blocks, player.getZ());
                    player.connection.send(new ServerboundMovePlayerPacket.Pos(player.getX(), (double) this.blocks, player.getZ(), true));
                    this.teleports++;
                    this.ticks++;
                }
                case 5 -> {
                    this.ticks = 0;
                    InvUtils.move().fromArmor(2).to(this.slot);
                }
            }
        }
    }
}
