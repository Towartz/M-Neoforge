package meteordevelopment.meteorclient.systems.modules.movement.plus.nofall.modes;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.PlayerMoveC2SPacketAccessor;
import meteordevelopment.meteorclient.systems.modules.movement.plus.nofall.NoFallMode;
import meteordevelopment.meteorclient.systems.modules.movement.plus.nofall.NoFallModes;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;

public class No_Ground_Elytra extends NoFallMode {
    public No_Ground_Elytra() {
        super(NoFallModes.No_Ground_Elytra);
    }

    @Override
    public void onActivate() {
        FindItemResult elytra = InvUtils.find(Items.ELYTRA);
        if (!elytra.found()) {
            ChatUtils.error("Elytra not found, this bypass needs an elytra to work");
        }
    }

    @Override
    public void onTickEventPre(TickEvent.Pre event) {
        if (mc.player == null) return;
        if (mc.player.fallDistance > 2.0F) {
            FindItemResult elytra = InvUtils.find(Items.ELYTRA);
            if (elytra.found()) {
                int slot = elytra.slot();
                if (!mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
                    InvUtils.move().from(slot).toArmor(2);
                }
            }
        }
    }

    @Override
    public void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof ServerboundMovePlayerPacket packet && mc.player != null) {
            if (packet.isOnGround()) {
                mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
                ((PlayerMoveC2SPacketAccessor) packet).setOnGround(false);
            }
        }
    }
}
