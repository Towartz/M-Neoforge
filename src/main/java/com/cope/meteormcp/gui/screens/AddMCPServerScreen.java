package com.cope.meteormcp.gui.screens;

import com.cope.meteormcp.systems.MCPServerConfig;
import com.cope.meteormcp.systems.MCPServers;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;

public class AddMCPServerScreen extends WindowScreen {
   private final WidgetScreen parent;
   private WTextBox nameInput;
   private WDropdown<MCPServerConfig.TransportType> transportInput;
   private WTextBox commandInput;
   private WTextBox argsInput;
   private WTextBox workingDirInput;
   private WTextBox urlInput;
   private WTextBox envInput;
   private WCheckbox autoConnectInput;
   private WIntEdit timeoutInput;

   public AddMCPServerScreen(GuiTheme theme, WidgetScreen parent) {
      super(theme, "Add MCP Server");
      this.parent = parent;
   }

   public void initWidgets() {
      this.add(this.theme.label("Server Name:"));
      this.nameInput = (WTextBox)this.add(this.theme.textBox("")).expandX().widget();
      this.nameInput.setFocused(true);
      this.add(this.theme.label("Transport Type:"));
      this.transportInput = (WDropdown<MCPServerConfig.TransportType>)this.add(this.theme.dropdown(MCPServerConfig.TransportType.STDIO)).expandX().widget();
      this.add(this.theme.label("Command:"));
      this.commandInput = (WTextBox)this.add(this.theme.textBox("python")).expandX().widget();
      this.add(this.theme.label("Arguments (comma separated):"));
      this.argsInput = (WTextBox)this.add(this.theme.textBox("-m,weather_server")).expandX().widget();
      this.add(this.theme.label("Working Directory (optional):"));
      this.workingDirInput = (WTextBox)this.add(this.theme.textBox("")).expandX().widget();
      this.add(this.theme.label("URL:"));
      this.urlInput = (WTextBox)this.add(this.theme.textBox("http://localhost:3000/mcp")).expandX().widget();
      this.add(this.theme.label("Environment Variables (KEY=value, comma separated):"));
      this.envInput = (WTextBox)this.add(this.theme.textBox("")).expandX().widget();
      this.add(this.theme.label("Auto-connect on startup:"));
      this.autoConnectInput = (WCheckbox)this.add(this.theme.checkbox(false)).widget();
      this.add(this.theme.label("Timeout (ms):"));
      this.timeoutInput = (WIntEdit)this.add(this.theme.intEdit(5000, 1000, 30000, false)).widget();
      WHorizontalList buttons = (WHorizontalList)this.add(this.theme.horizontalList()).expandX().widget();
      WButton saveBtn = (WButton)buttons.add(this.theme.button("Save")).expandX().widget();
      saveBtn.action = this::save;
      WButton cancelBtn = (WButton)buttons.add(this.theme.button("Cancel")).expandX().widget();
      cancelBtn.action = () -> MeteorClient.mc.setScreen(this.parent);
   }

   private void save() {
      String name = this.nameInput.get().trim();
      if (!name.isEmpty()) {
         if (MCPServers.get().getConfig(name) == null) {
            MCPServerConfig.TransportType transport = (MCPServerConfig.TransportType)this.transportInput.get();
            MCPServerConfig config = new MCPServerConfig(name, transport);
            if (transport == MCPServerConfig.TransportType.STDIO) {
               String command = this.commandInput.get().trim();
               if (command.isEmpty()) {
                  return;
               }

               config.setCommand(command);
               String argsStr = this.argsInput.get().trim();
               if (!argsStr.isEmpty()) {
                  String[] args = argsStr.split(",");

                  for (int i = 0; i < args.length; i++) {
                     args[i] = args[i].trim();
                  }

                  config.setArgs(Arrays.asList(args));
               }

               String workingDir = this.workingDirInput.get().trim();
               if (!workingDir.isEmpty()) {
                  config.setWorkingDirectory(workingDir);
               }
            } else {
               String url = this.urlInput.get().trim();
               if (url.isEmpty()) {
                  return;
               }

               config.setUrl(url);
            }

            String envStr = this.envInput.get().trim();
            if (!envStr.isEmpty()) {
               String[] pairs = envStr.split(",");
               Map<String, String> env = new HashMap<>();

               for (String pair : pairs) {
                  String[] kv = pair.split("=", 2);
                  if (kv.length == 2) {
                     env.put(kv[0].trim(), kv[1].trim());
                  }
               }

               config.setEnv(env);
            }

            config.setAutoConnect(this.autoConnectInput.checked);
            config.setTimeout(this.timeoutInput.get());
            if (MCPServers.get().add(config)) {
               MeteorClient.mc.setScreen(this.parent);
               if (this.parent != null) {
                  this.parent.reload();
               }
            }
         }
      }
   }
}
