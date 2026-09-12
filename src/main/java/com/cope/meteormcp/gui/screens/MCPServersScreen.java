package com.cope.meteormcp.gui.screens;

import com.cope.meteormcp.systems.AIConfig;
import com.cope.meteormcp.systems.MCPServerConfig;
import com.cope.meteormcp.systems.MCPServerConnection;
import com.cope.meteormcp.systems.MCPServers;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.WindowTabScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;

public class MCPServersScreen extends WindowTabScreen {
   public MCPServersScreen(GuiTheme theme, Tab tab) {
      super(theme, tab);
      this.window.padding = 6.0;
      this.window.spacing = 4.0;
      this.window.id = "mcp-servers";
   }

   public void initWidgets() {
      WButton addButton = (WButton)this.add(this.theme.button("Add Server")).expandX().widget();
      addButton.action = () -> MeteorClient.mc.setScreen(new AddMCPServerScreen(this.theme, this));
      this.add(this.theme.horizontalSeparator()).expandX();
      this.add(this.theme.label("AI Settings")).expandX();
      WHorizontalList aiRow = (WHorizontalList)this.add(this.theme.horizontalList()).expandX().widget();
      WButton aiSettingsBtn = (WButton)aiRow.add(this.theme.button("Configure AI")).expandX().widget();
      aiSettingsBtn.action = () -> MeteorClient.mc.setScreen(new AISettingsScreen(this.theme, this));
      AIConfig aiConfig = MCPServers.get().getAIConfig();
      String providerName = aiConfig.getActiveProvider().name();
      boolean active = aiConfig.isActiveProviderValid();
      String aiStatus = providerName + ": " + (active ? "Enabled" : "Not Active");
      aiRow.add(this.theme.label(aiStatus));
      this.add(this.theme.horizontalSeparator()).expandX();
      WTable table = (WTable)this.add(this.theme.table()).expandX().widget();
      MCPServers mcpServers = MCPServers.get();

      for (MCPServerConfig config : mcpServers.getAllConfigs()) {
         table.add(this.theme.label(config.getName()));
         boolean connected = mcpServers.isConnected(config.getName());
         String status = connected ? "Connected" : "Disconnected";
         table.add(this.theme.label(status));
         WHorizontalList actions = (WHorizontalList)table.add(this.theme.horizontalList()).expandCellX().right().widget();
         if (connected) {
            WButton disconnectBtn = (WButton)actions.add(this.theme.button("Disconnect")).widget();
            disconnectBtn.action = () -> {
               mcpServers.disconnect(config.getName());
               this.reload();
            };
         } else {
            WButton connectBtn = (WButton)actions.add(this.theme.button("Connect")).widget();
            connectBtn.action = () -> {
               connectBtn.set("Connecting...");
               connectBtn.action = null;
               MeteorExecutor.execute(() -> {
                  mcpServers.connect(config.getName());
                  if (MeteorClient.mc != null) {
                     MeteorClient.mc.execute(this::reload);
                  } else {
                     this.reload();
                  }
               });
            };
         }

         WButton toolsBtn = (WButton)actions.add(this.theme.button("Tools")).widget();
         toolsBtn.action = () -> {
            MCPServerConnection connection = mcpServers.getConnection(config.getName());
            if (connection != null) {
               MeteorClient.mc.setScreen(new MCPToolsScreen(this.theme, this, connection));
            }
         };
         WButton editBtn = (WButton)actions.add(this.theme.button("Edit")).widget();
         editBtn.action = () -> MeteorClient.mc.setScreen(new EditMCPServerScreen(this.theme, this, config));
         WButton removeBtn = (WButton)actions.add(this.theme.button("Remove")).widget();
         removeBtn.action = () -> {
            mcpServers.remove(config.getName());
            this.reload();
         };
         table.row();
      }

      if (mcpServers.getAllConfigs().isEmpty()) {
         this.add(this.theme.label("No MCP servers configured."));
         this.add(this.theme.label("Click 'Add Server' to get started."));
      }
   }
}
