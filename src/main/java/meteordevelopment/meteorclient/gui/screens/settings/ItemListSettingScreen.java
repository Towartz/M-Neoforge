package meteordevelopment.meteorclient.gui.screens.settings;

import java.util.function.Predicate;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.utils.misc.Names;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class ItemListSettingScreen extends RegistryListSettingScreen<Item> {
   public ItemListSettingScreen(GuiTheme theme, ItemListSetting setting) {
      super(theme, "Select Items", setting, setting.get(), BuiltInRegistries.ITEM);
   }

   protected boolean includeValue(Item value) {
      Predicate<Item> filter = ((ItemListSetting)this.setting).filter;
      return filter != null && !filter.test(value) ? false : value != Items.AIR;
   }

   protected WWidget getValueWidget(Item value) {
      return this.theme.itemWithLabel(value.getDefaultInstance());
   }

   protected String getValueName(Item value) {
      return Names.get(value);
   }
}
