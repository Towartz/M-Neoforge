package meteordevelopment.meteorclient.systems.modules.movement.plus.nofall.modes;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.movement.plus.nofall.NoFallMode;
import meteordevelopment.meteorclient.systems.modules.movement.plus.nofall.NoFallModes;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class ElytraFly extends NoFallMode {
    public ElytraFly() {
        super(NoFallModes.Elytra_Fly);
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

            if (mc.player.fallDistance > 2.7F) {
                mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
                mc.player.connection.send(new ServerboundMovePlayerPacket.StatusOnly(true));
                Vec3 vel = mc.player.getDeltaMovement();
                mc.player.setDeltaMovement(vel.x, 0.0, vel.z);
                mc.player.fallDistance = 0.0F;
                mc.player.setOnGround(true);
            }
        }
    }
}
