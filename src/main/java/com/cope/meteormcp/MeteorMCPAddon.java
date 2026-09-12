package com.cope.meteormcp;

import com.cope.meteormcp.agent.AIAgent;
import com.cope.meteormcp.commands.AITaskCommand;
import com.cope.meteormcp.server.EmbeddedMCPServer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import com.cope.meteormcp.commands.AICommand;
import com.cope.meteormcp.commands.AIMCPCommand;
import com.cope.meteormcp.gui.tabs.MCPTab;
import com.cope.meteormcp.starscript.AIStarScriptIntegration;
import com.cope.meteormcp.starscript.MCPToolExecutor;
import com.cope.meteormcp.systems.MCPServerConnection;
import com.cope.meteormcp.systems.MCPServers;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.utils.misc.MeteorStarscript;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.starscript.value.Value;
import meteordevelopment.starscript.value.ValueMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MeteorMCPAddon extends MeteorAddon {
   public static final Logger LOG = LoggerFactory.getLogger("Meteor MCP");

   public void onInitialize() {
      LOG.info("Initializing Meteor MCP Addon");
      Systems.add(new MCPServers());
      LOG.info("MCPServers system initialized");
      Tabs.add(new MCPTab());
      LOG.info("MCP tab registered");
      AIStarScriptIntegration.register();
      LOG.info("AI StarScript functions registered");
      AICommand aiCommand = new AICommand();
      Commands.add(aiCommand);
      aiCommand.registerTo(Commands.DISPATCHER);
      AIMCPCommand aiMCPCommand = new AIMCPCommand();
      Commands.add(aiMCPCommand);
      aiMCPCommand.registerTo(Commands.DISPATCHER);
      LOG.info("AI chat commands registered");

      AITaskCommand aiTaskCommand = new AITaskCommand();
      Commands.add(aiTaskCommand);
      aiTaskCommand.registerTo(Commands.DISPATCHER);
      LOG.info(".ai-task command registered");

      Modules.get().add(new AIAgent());
      LOG.info("AIAgent module registered");

      EmbeddedMCPServer.start();

      MeteorExecutor.execute(() -> MCPServers.get().connectAutoConnect());
      LOG.info("Meteor MCP Addon initialized successfully");
   }

   public void onRegisterCategories() {
   }

   public String getPackage() {
      return "com.cope.meteormcp";
   }

   public GithubRepo getRepo() {
      return new GithubRepo("cope", "meteor-mcp");
   }

   public static void registerServerToStarScript(String serverName, MCPServerConnection connection) {
      try {
         ValueMap serverMap = new ValueMap();

         for (Tool tool : connection.getTools()) {
            serverMap.set(tool.name(), MCPToolExecutor.createToolFunction(connection, tool));
         }

         MeteorStarscript.ss.set(serverName, serverMap);
         LOG.info("Registered {} tools from MCP server '{}' to StarScript", connection.getTools().size(), serverName);
      } catch (Exception var5) {
         LOG.error("Failed to register MCP server '{}' to StarScript: {}", serverName, var5.getMessage());
      }
   }

   public static void unregisterServerFromStarScript(String serverName) {
      try {
         MeteorStarscript.ss.set(serverName, Value.null_());
         LOG.info("Unregistered MCP server '{}' from StarScript", serverName);
      } catch (Exception var2) {
         LOG.error("Failed to unregister MCP server '{}' from StarScript: {}", serverName, var2.getMessage());
      }
   }
}
