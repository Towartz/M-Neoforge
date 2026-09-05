package meteordevelopment.meteorclient.gui.tabs.builtin;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.tabs.WindowTabScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.utils.misc.NbtUtils;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;

public class GuiTab extends Tab {
   public GuiTab() {
      super("GUI");
   }

   @Override
   public TabScreen createScreen(GuiTheme theme) {
      return new GuiTab.GuiScreen(theme, this);
   }

   @Override
   public boolean isScreen(Screen screen) {
      return screen instanceof GuiTab.GuiScreen;
   }

   private static class GuiScreen extends WindowTabScreen {
      public GuiScreen(GuiTheme theme, Tab tab) {
         super(theme, tab);
         theme.settings.onActivated();
      }

      @Override
      public void initWidgets() {
         WTable table = this.add(this.theme.table()).expandX().widget();
         table.add(this.theme.label("Theme:"));
         WDropdown<String> themeW = table.add(this.theme.dropdown(GuiThemes.getNames(), GuiThemes.get().name)).widget();
         themeW.action = () -> {
            GuiThemes.select(themeW.get());
            MeteorClient.mc.setScreen(null);
            this.tab.openScreen(GuiThemes.get());
         };
         WButton reset = this.add(this.theme.button("Reset GUI Layout")).widget();
         reset.action = () -> this.theme.clearWindowConfigs();
         this.add(this.theme.settings(this.theme.settings)).expandX();
      }

      @Override
      public boolean toClipboard() {
         return NbtUtils.toClipboard(this.theme.name + " GUI Theme", this.theme.toTag());
      }

      @Override
      public boolean fromClipboard() {
         CompoundTag clipboard = NbtUtils.fromClipboard(this.theme.toTag());
         if (clipboard != null) {
            this.theme.fromTag(clipboard);
            return true;
         } else {
            return false;
         }
      }
   }
}
