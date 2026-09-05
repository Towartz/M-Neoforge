package meteordevelopment.meteorclient.gui.screens.settings;

import java.util.Set;
import java.util.function.Predicate;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.PacketListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.network.PacketUtils;
import net.minecraft.network.protocol.Packet;

public class PacketBoolSettingScreen extends RegistryListSettingScreen<Class<? extends Packet<?>>> {
   public PacketBoolSettingScreen(GuiTheme theme, Setting<Set<Class<? extends Packet<?>>>> setting) {
      super(theme, "Select Packets", setting, setting.get(), PacketUtils.REGISTRY);
   }

   protected boolean includeValue(Class<? extends Packet<?>> value) {
      Predicate<Class<? extends Packet<?>>> filter = ((PacketListSetting)this.setting).filter;
      return filter == null ? true : filter.test(value);
   }

   protected WWidget getValueWidget(Class<? extends Packet<?>> value) {
      return this.theme.label(this.getValueName(value));
   }

   protected String getValueName(Class<? extends Packet<?>> value) {
      return PacketUtils.getName(value);
   }
}
