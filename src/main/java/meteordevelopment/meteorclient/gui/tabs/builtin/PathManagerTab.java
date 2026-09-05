package meteordevelopment.meteorclient.gui.tabs.builtin;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.tabs.WindowTabScreen;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.pathing.PathManagers;
import net.minecraft.client.gui.screens.Screen;

public class PathManagerTab extends Tab {
   public PathManagerTab() {
      super(PathManagers.get().getName());
   }

   @Override
   public TabScreen createScreen(GuiTheme theme) {
      return new PathManagerTab.PathManagerScreen(theme, this);
   }

   @Override
   public boolean isScreen(Screen screen) {
      return screen instanceof PathManagerTab.PathManagerScreen;
   }

   private static class PathManagerScreen extends WindowTabScreen {
      public PathManagerScreen(GuiTheme theme, Tab tab) {
         super(theme, tab);
         PathManagers.get().getSettings().get().onActivated();
      }

      @Override
      public void initWidgets() {
         WTextBox filter = this.add(this.theme.textBox("")).minWidth(400.0).expandX().widget();
         filter.setFocused(true);
         filter.action = () -> {
            this.clear();
            this.add(filter);
            this.add(this.theme.settings(PathManagers.get().getSettings().get(), filter.get().trim())).expandX();
         };
         this.add(this.theme.settings(PathManagers.get().getSettings().get(), filter.get().trim())).expandX();
      }

      @Override
      protected void onClosed() {
         PathManagers.get().getSettings().save();
      }
   }
}
