package com.cope.meteormcp.gui.tabs;

import com.cope.meteormcp.gui.screens.MCPServersScreen;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import net.minecraft.client.gui.screens.Screen;

public class MCPTab extends Tab {
   public MCPTab() {
      super("MCP");
   }

   public TabScreen createScreen(GuiTheme theme) {
      return new MCPServersScreen(theme, this);
   }

   public boolean isScreen(Screen screen) {
      return screen instanceof MCPServersScreen;
   }
}
