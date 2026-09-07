package meteordevelopment.meteorclient.systems.modules.movement.plus.speed.modes.vulcan;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.systems.modules.movement.plus.speed.SpeedMode;
import meteordevelopment.meteorclient.systems.modules.movement.plus.speed.SpeedModes;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.meteorclient.utils.plus.CustomSpeedUtils;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class Vulcan extends SpeedMode {
    public Item chestPlate;

    public Vulcan() {
        super(SpeedModes.Vulcan);
    }

    @Override
    public void onDeactivate() {
        if (this.chestPlate != null && mc.player != null) {
            FindItemResult chest = InvUtils.find(this.chestPlate);
            if (chest.found() && mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA) && this.settings.autoSwapVulcan.get()) {
                InvUtils.move().from(chest.slot()).toArmor(2);
            }
        }
    }

    @Override
    public void onActivate() {
        FindItemResult elytra = InvUtils.find(Items.ELYTRA);
        if (!elytra.found()) {
            this.settings.error("Elytra not found");
            this.settings.toggle();
        } else if (mc.player != null && !SlotUtils.isArmor(elytra.slot()) && this.settings.autoSwapVulcan.get() && !mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
            this.chestPlate = mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem();
            InvUtils.move().from(elytra.slot()).toArmor(2);
        }
    }

    @Override
    public void onPlayerMoveEvent(PlayerMoveEvent event) {
        if (mc.player != null && mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
            if (mc.player.hasEffect(MobEffects.MOVEMENT_SPEED) && mc.player.getEffect(MobEffects.MOVEMENT_SPEED) != null) {
                if (mc.player.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() == 1) {
                    CustomSpeedUtils.applySpeed(event, this.settings.speedVulcanef2.get());
                } else if (mc.player.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() == 0) {
                    CustomSpeedUtils.applySpeed(event, this.settings.speedVulcanef1.get());
                }
            } else {
                CustomSpeedUtils.applySpeed(event, this.settings.speedVulcanef0.get());
            }
        }
    }
}
