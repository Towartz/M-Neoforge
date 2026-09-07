package meteordevelopment.meteorclient.systems.modules.render.plus;

import java.util.List;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ItemHighlightPlus extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    public final Setting<List<Item>> items = this.sgGeneral.add(new ItemListSetting.Builder()
        .name("items")
        .description("Items to highlight in inventory screens.")
        .defaultValue(List.of(Items.TOTEM_OF_UNDYING, Items.ENCHANTED_GOLDEN_APPLE, Items.ELYTRA, Items.END_CRYSTAL, Items.MACE))
        .build()
    );

    public final Setting<SettingColor> color = this.sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Highlight border color.")
        .defaultValue(new SettingColor(255, 215, 0, 180))
        .build()
    );

    public ItemHighlightPlus() {
        super(Categories.PlusRender, "Item-Highlight+", "Highlights high-value items in inventories and containers.");
    }

    public boolean shouldHighlight(ItemStack stack) {
        return this.isActive() && stack != null && !stack.isEmpty() && this.items.get().contains(stack.getItem());
    }
}
