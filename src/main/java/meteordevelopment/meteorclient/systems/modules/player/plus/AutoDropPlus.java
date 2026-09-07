package meteordevelopment.meteorclient.systems.modules.player.plus;

import java.util.List;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class AutoDropPlus extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<List<Item>> items = this.sgGeneral.add(new ItemListSetting.Builder()
        .name("items")
        .description("Items to automatically throw away.")
        .defaultValue(List.of())
        .build()
    );

    private final Setting<Integer> delay = this.sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay in ticks between drops.")
        .defaultValue(2)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> excludeHotbar = this.sgGeneral.add(new BoolSetting.Builder()
        .name("exclude-hotbar")
        .description("Do not drop items in the hotbar.")
        .defaultValue(true)
        .build()
    );

    private int timer = 0;

    public AutoDropPlus() {
        super(Categories.PlusPlayer, "auto-drop", "Automatically drops junk items from inventory.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (this.mc.player == null || this.mc.gameMode == null) return;

        if (this.timer > 0) {
            this.timer--;
            return;
        }

        var inv = this.mc.player.getInventory();
        int startIndex = this.excludeHotbar.get() ? 9 : 0;
        int endIndex = 36;

        for (int i = startIndex; i < endIndex; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && this.items.get().contains(stack.getItem())) {
                int slotId = i < 9 ? i + 36 : i;
                this.mc.gameMode.handleInventoryMouseClick(0, slotId, 1, ClickType.THROW, this.mc.player);
                this.timer = this.delay.get();
                return;
            }
        }
    }
}
