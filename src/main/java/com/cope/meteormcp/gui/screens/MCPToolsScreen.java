package com.cope.meteormcp.gui.screens;

import com.cope.meteormcp.starscript.MCPToolExecutor;
import com.cope.meteormcp.systems.MCPServerConnection;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import net.minecraft.client.gui.screens.Screen;

public class MCPToolsScreen extends WindowScreen {
   private final Screen parent;
   private final MCPServerConnection connection;

   public MCPToolsScreen(GuiTheme theme, Screen parent, MCPServerConnection connection) {
      super(theme, "MCP Tools - " + connection.getConfig().getName());
      this.parent = parent;
      this.connection = connection;
   }

   public void initWidgets() {
      this.add(this.theme.label("Server: " + this.connection.getConfig().getName()));
      String status = this.connection.isConnected() ? "Connected" : "Disconnected";
      this.add(this.theme.label("Status: " + status));
      this.add(this.theme.horizontalSeparator()).expandX();
      WButton backBtn = (WButton)this.add(this.theme.button("Back")).expandX().widget();
      backBtn.action = () -> MeteorClient.mc.setScreen(this.parent);
      this.add(this.theme.horizontalSeparator()).expandX();
      List<Tool> tools = this.connection.getTools();
      if (tools.isEmpty()) {
         this.add(this.theme.label("No tools available."));
         if (!this.connection.isConnected()) {
            this.add(this.theme.label("Server is not connected."));
         }
      } else {
         this.add(this.theme.label("Tools (" + tools.size() + "):"));

         for (Tool tool : tools) {
            WSection section = (WSection)this.add(this.theme.section(tool.name(), true)).expandX().widget();
            section.add(this.theme.label("Name: " + tool.name()));
            if (tool.description() != null && !tool.description().isEmpty()) {
               section.add(this.theme.label("Description:"));
               section.add(this.theme.label("  " + tool.description()));
            }

            List<String> paramNames = MCPToolExecutor.getParameterNames(tool);
            if (!paramNames.isEmpty()) {
               section.add(this.theme.label("Parameters:"));
               WTable paramsTable = (WTable)section.add(this.theme.table()).expandX().widget();
               List<String> required = MCPToolExecutor.getRequiredParameters(tool);

               for (String paramName : paramNames) {
                  String type = MCPToolExecutor.getParameterType(tool, paramName);
                  boolean isRequired = required.contains(paramName);
                  String reqStr = isRequired ? " (required)" : " (optional)";
                  paramsTable.add(this.theme.label("  - " + paramName));
                  paramsTable.add(this.theme.label(type));
                  paramsTable.add(this.theme.label(reqStr));
                  paramsTable.row();
               }
            }

            String example = MCPToolExecutor.generateExampleSyntax(this.connection.getConfig().getName(), tool);
            section.add(this.theme.label("StarScript Usage:"));
            section.add(this.theme.label("  " + example));
            WButton copyBtn = (WButton)section.add(this.theme.button("Copy StarScript Syntax")).expandX().widget();
            copyBtn.action = () -> MeteorClient.mc.keyboardHandler.setClipboard(example);
         }
      }
   }
}
