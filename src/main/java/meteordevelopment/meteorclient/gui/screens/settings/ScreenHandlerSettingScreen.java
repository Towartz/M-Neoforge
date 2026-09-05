package meteordevelopment.meteorclient.gui.screens.settings;

import java.util.List;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;

public class ScreenHandlerSettingScreen extends RegistryListSettingScreen<MenuType<?>> {
   public ScreenHandlerSettingScreen(GuiTheme theme, Setting<List<MenuType<?>>> setting) {
      super(theme, "Select Screen Handlers", setting, setting.get(), BuiltInRegistries.MENU);
   }

   protected WWidget getValueWidget(MenuType<?> value) {
      return this.theme.label(this.getValueName(value));
   }

   protected String getValueName(MenuType<?> type) {
      return BuiltInRegistries.MENU.getKey(type).toString();
   }
}
