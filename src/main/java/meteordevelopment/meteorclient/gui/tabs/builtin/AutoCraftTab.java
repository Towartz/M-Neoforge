package meteordevelopment.meteorclient.gui.tabs.builtin;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.screens.AutoCraftScreen;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import net.minecraft.client.gui.screens.Screen;

public class AutoCraftTab extends Tab {
   public AutoCraftTab() {
      super("AutoCraft");
   }

   @Override
   public TabScreen createScreen(GuiTheme theme) {
      return new AutoCraftScreen(theme, this);
   }

   @Override
   public boolean isScreen(Screen screen) {
      return screen instanceof AutoCraftScreen;
   }
}
