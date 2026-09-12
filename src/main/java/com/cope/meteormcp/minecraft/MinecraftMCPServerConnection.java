package com.cope.meteormcp.minecraft;

import com.cope.meteormcp.MeteorMCPAddon;
import com.cope.meteormcp.systems.MCPServerConfig;
import com.cope.meteormcp.systems.MCPServerConnection;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.List;
import java.util.Map;

public class MinecraftMCPServerConnection extends MCPServerConnection {
   private boolean isConnected = true;

   public MinecraftMCPServerConnection() {
      super(createConfig());
   }

   private static MCPServerConfig createConfig() {
      MCPServerConfig cfg = new MCPServerConfig("minecraft", MCPServerConfig.TransportType.STDIO);
      cfg.setCommand("builtin");
      cfg.setAutoConnect(true);
      return cfg;
   }

   @Override
   public boolean connect() {
      this.isConnected = true;
      MeteorMCPAddon.LOG.info("Built-in Minecraft MCP Server connected with {} tools.", MinecraftToolRegistry.getTools().size());
      return true;
   }

   @Override
   public void disconnect() {
      this.isConnected = false;
      MeteorMCPAddon.LOG.info("Built-in Minecraft MCP Server disconnected.");
   }

   @Override
   public boolean reconnect() {
      return this.connect();
   }

   @Override
   public boolean isConnected() {
      return this.isConnected;
   }

   @Override
   public List<Tool> getTools() {
      return MinecraftToolRegistry.getTools();
   }

   @Override
   public Tool getTool(String name) {
      return MinecraftToolRegistry.getTool(name);
   }

   @Override
   public CallToolResult callTool(String toolName, Map<String, Object> arguments) {
      if (!this.isConnected) {
         throw new IllegalStateException("Minecraft MCP server is not connected.");
      }
      return MinecraftToolRegistry.execute(toolName, arguments);
   }
}
