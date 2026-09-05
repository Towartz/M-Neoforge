package meteordevelopment.meteorclient.gui.screens.settings;

import java.util.List;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class ModuleListSettingScreen extends RegistryListSettingScreen<Module> {
   public ModuleListSettingScreen(GuiTheme theme, Setting<List<Module>> setting) {
      super(theme, "Select Modules", setting, setting.get(), Modules.REGISTRY);
   }

   protected WWidget getValueWidget(Module value) {
      return this.theme.label(this.getValueName(value));
   }

   protected String getValueName(Module value) {
      return value.title;
   }
}
