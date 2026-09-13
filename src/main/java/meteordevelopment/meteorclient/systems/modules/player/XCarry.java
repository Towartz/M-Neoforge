package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.mixin.CloseHandledScreenC2SPacketAccessor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.inventory.ClickType;

public class XCarry extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Boolean> suppressClose = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("suppress-close")
            .description("Suppresses inventory close packet to keep crafting slots filled.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Keybind> takeOutKey = this.sgGeneral.add(
        new KeybindSetting.Builder()
            .name("take-out-key")
            .description("Keybind to pull all items out of crafting grid into inventory.")
            .action(this::takeOutItems)
            .build()
    );

    private final Setting<Keybind> dropKey = this.sgGeneral.add(
        new KeybindSetting.Builder()
            .name("drop-key")
            .description("Keybind to instantly drop all items stored in crafting grid.")
            .action(this::dropItems)
            .build()
    );

    public XCarry() {
        super(Categories.Player, "x-carry", "Allows carrying items in your 2x2 crafting grid without dropping them upon closing.");
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!isActive() || !suppressClose.get()) return;

        if (event.packet instanceof ServerboundContainerClosePacket close) {
            int syncId = ((CloseHandledScreenC2SPacketAccessor) close).getSyncId();
            // Container ID 0 is player inventory / crafting grid
            if (syncId == 0 || (mc.player != null && syncId == mc.player.inventoryMenu.containerId)) {
                event.cancel();
            }
        }
    }

    public void takeOutItems() {
        if (mc.player == null || mc.gameMode == null) return;

        mc.execute(() -> {
            // Crafting slots are slots 1, 2, 3, 4 in player inventoryMenu
            for (int slot = 1; slot <= 4; slot++) {
                if (!mc.player.inventoryMenu.getSlot(slot).getItem().isEmpty()) {
                    mc.gameMode.handleInventoryMouseClick(
                        mc.player.inventoryMenu.containerId,
                        slot,
                        0,
                        ClickType.QUICK_MOVE,
                        mc.player
                    );
                }
            }
            info("Pulled XCarry items into inventory.");
        });
    }

    public void dropItems() {
        if (mc.player == null || mc.gameMode == null) return;

        mc.execute(() -> {
            for (int slot = 1; slot <= 4; slot++) {
                if (!mc.player.inventoryMenu.getSlot(slot).getItem().isEmpty()) {
                    mc.gameMode.handleInventoryMouseClick(
                        mc.player.inventoryMenu.containerId,
                        slot,
                        1,
                        ClickType.THROW,
                        mc.player
                    );
                }
            }
            info("Dropped XCarry items.");
        });
    }
}
