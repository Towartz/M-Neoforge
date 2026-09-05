package meteordevelopment.meteorclient.gui.screens.settings;

import java.util.List;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.misc.Names;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

public class ParticleTypeListSettingScreen extends RegistryListSettingScreen<ParticleType<?>> {
   public ParticleTypeListSettingScreen(GuiTheme theme, Setting<List<ParticleType<?>>> setting) {
      super(theme, "Select Particles", setting, setting.get(), BuiltInRegistries.PARTICLE_TYPE);
   }

   protected WWidget getValueWidget(ParticleType<?> value) {
      return this.theme.label(this.getValueName(value));
   }

   protected String getValueName(ParticleType<?> value) {
      return Names.get(value);
   }

   protected boolean skipValue(ParticleType<?> value) {
      return !(value instanceof ParticleOptions);
   }
}
