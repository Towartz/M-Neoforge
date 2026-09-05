package meteordevelopment.meteorclient.gui.screens.settings;

import java.util.List;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

public class SoundEventListSettingScreen extends RegistryListSettingScreen<SoundEvent> {
   public SoundEventListSettingScreen(GuiTheme theme, Setting<List<SoundEvent>> setting) {
      super(theme, "Select Sounds", setting, setting.get(), BuiltInRegistries.SOUND_EVENT);
   }

   protected WWidget getValueWidget(SoundEvent value) {
      return this.theme.label(this.getValueName(value));
   }

   protected String getValueName(SoundEvent value) {
      return value.getLocation().getPath();
   }
}
