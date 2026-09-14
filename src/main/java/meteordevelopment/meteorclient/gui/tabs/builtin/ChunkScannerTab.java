package meteordevelopment.meteorclient.gui.tabs.builtin;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.screens.ChunkScannerScreen;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import net.minecraft.client.gui.screens.Screen;

public class ChunkScannerTab extends Tab {
   public ChunkScannerTab() {
      super("Chunk Scanner");
   }

   @Override
   public TabScreen createScreen(GuiTheme theme) {
      return new ChunkScannerScreen(theme, this);
   }

   @Override
   public boolean isScreen(Screen screen) {
      return screen instanceof ChunkScannerScreen;
   }
}
