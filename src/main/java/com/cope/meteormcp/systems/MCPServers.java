package com.cope.meteormcp.systems;

import com.cope.meteormcp.MeteorMCPAddon;
import com.cope.meteormcp.minecraft.MinecraftMCPServerConnection;
import com.cope.meteormcp.commands.MCPToolCommand;
import com.cope.meteormcp.gemini.GeminiClientManager;
import com.cope.meteormcp.llm.LLMProviderManager;
import com.cope.meteormcp.starscript.MCPToolExecutor;
import com.mojang.brigadier.CommandDispatcher;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.System;
import meteordevelopment.meteorclient.systems.Systems;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public class MCPServers extends System<MCPServers> {
   private final Map<String, MCPServerConnection> connections = new ConcurrentHashMap<>();
   private final Map<String, MCPServerConfig> configs = new ConcurrentHashMap<>();
   private final Map<String, List<MCPToolCommand>> registeredCommands = new ConcurrentHashMap<>();
   private AIConfig aiConfig = new AIConfig();

   public MCPServers() {
      super("mcp-servers");
   }

   public static MCPServers get() {
      return (MCPServers)Systems.get(MCPServers.class);
   }

   @Override
   public void init() {
      if (!this.connections.containsKey("minecraft")) {
         MinecraftMCPServerConnection mcConn = new MinecraftMCPServerConnection();
         mcConn.connect();
         this.registerBuiltin(mcConn);
      }
   }

   public void registerBuiltin(MCPServerConnection connection) {
      String name = connection.getConfig().getName();
      this.configs.put(name, connection.getConfig());
      this.connections.put(name, connection);
      Runnable registration = () -> {
         MeteorMCPAddon.registerServerToStarScript(name, connection);
         this.registerCommandsForServer(name, connection);
      };
      if (MeteorClient.mc != null) {
         MeteorClient.mc.execute(registration);
      } else {
         registration.run();
      }
   }

   public boolean add(MCPServerConfig config) {
      if (config != null && config.isValid()) {
         if (this.configs.containsKey(config.getName())) {
            MeteorMCPAddon.LOG.warn("MCP server with name {} already exists", config.getName());
            return false;
         } else {
            this.configs.put(config.getName(), config);
            MeteorMCPAddon.LOG.info("Added MCP server configuration: {}", config.getName());
            return true;
         }
      } else {
         MeteorMCPAddon.LOG.warn("Attempted to add invalid MCP server config");
         return false;
      }
   }

   public boolean remove(String name) {
      if ("minecraft".equals(name)) {
         MeteorMCPAddon.LOG.warn("Cannot remove built-in minecraft MCP server");
         return false;
      }
      if (this.connections.containsKey(name)) {
         this.disconnect(name);
      }

      MCPServerConfig removed = this.configs.remove(name);
      if (removed != null) {
         MeteorMCPAddon.LOG.info("Removed MCP server configuration: {}", name);
         return true;
      } else {
         return false;
      }
   }

   public boolean update(String oldName, MCPServerConfig newConfig) {
      if (!this.configs.containsKey(oldName)) {
         return false;
      } else {
         if (!oldName.equals(newConfig.getName())) {
            if (this.configs.containsKey(newConfig.getName())) {
               MeteorMCPAddon.LOG.warn("Cannot rename to {}, name already exists", newConfig.getName());
               return false;
            }

            if (this.connections.containsKey(oldName)) {
               this.disconnect(oldName);
            }

            this.configs.remove(oldName);
         } else if (this.connections.containsKey(oldName)) {
            this.disconnect(oldName);
         }

         this.configs.put(newConfig.getName(), newConfig);
         MeteorMCPAddon.LOG.info("Updated MCP server configuration: {}", newConfig.getName());
         return true;
      }
   }

   public boolean connect(String name) {
      MCPServerConfig config = this.configs.get(name);
      if (config == null) {
         MeteorMCPAddon.LOG.warn("Cannot connect, server {} not found", name);
         return false;
      } else if (this.connections.containsKey(name) && this.connections.get(name).isConnected()) {
         return true;
      } else {
         MCPServerConnection connection = new MCPServerConnection(config);
         if (connection.connect()) {
            this.connections.put(name, connection);
            Runnable registration = () -> {
               MeteorMCPAddon.registerServerToStarScript(name, connection);
               this.registerCommandsForServer(name, connection);
            };
            if (MeteorClient.mc != null) {
               MeteorClient.mc.execute(registration);
            } else {
               registration.run();
            }

            return true;
         } else {
            return false;
         }
      }
   }

   public void disconnect(String name) {
      MCPServerConnection connection = this.connections.remove(name);
      if (connection != null) {
         connection.disconnect();
         MCPToolExecutor.clearAsyncResultsForServer(name);
         Runnable cleanup = () -> {
            MeteorMCPAddon.unregisterServerFromStarScript(name);
            this.unregisterCommandsForServer(name);
         };
         if (MeteorClient.mc != null) {
            MeteorClient.mc.execute(cleanup);
         } else {
            cleanup.run();
         }
      }
   }

   public MCPServerConnection getConnection(String name) {
      return this.connections.get(name);
   }

   public MCPServerConfig getConfig(String name) {
      return this.configs.get(name);
   }

   public Set<String> getServerNames() {
      return new HashSet<>(this.configs.keySet());
   }

   public Collection<MCPServerConfig> getAllConfigs() {
      return new ArrayList<>(this.configs.values());
   }

   public GeminiConfig getGeminiConfig() {
      return this.aiConfig.getGeminiConfig();
   }

   public void setGeminiConfig(GeminiConfig config) {
      GeminiConfig next = config != null ? config : new GeminiConfig();
      if (!Objects.equals(this.aiConfig.getGeminiConfig(), next)) {
         this.aiConfig.setGeminiConfig(next);
         GeminiClientManager.getInstance().invalidateClient();
      } else {
         this.aiConfig.setGeminiConfig(next);
      }
   }

   public AIConfig getAIConfig() {
      return this.aiConfig;
   }

   public void setAIConfig(AIConfig config) {
      AIConfig next = config != null ? config : new AIConfig();
      if (!Objects.equals(this.aiConfig, next)) {
         this.aiConfig = next;
         GeminiClientManager.getInstance().invalidateClient();
         LLMProviderManager.getInstance().invalidate();
      } else {
         this.aiConfig = next;
      }
   }

   public Collection<MCPServerConnection> getAllConnections() {
      return new ArrayList<>(this.connections.values());
   }

   public boolean isConnected(String name) {
      MCPServerConnection conn = this.connections.get(name);
      return conn != null && conn.isConnected();
   }

   public void forEach(BiConsumer<String, MCPServerConfig> consumer) {
      this.configs.forEach(consumer);
   }

   public void connectAutoConnect() {
      for (MCPServerConfig config : this.configs.values()) {
         if (config.isAutoConnect()) {
            this.connect(config.getName());
         }
      }
   }

   public void disconnectAll() {
      new ArrayList<>(this.connections.keySet()).forEach(this::disconnect);
   }

   private void registerCommandsForServer(String serverName, MCPServerConnection connection) {
      if (connection != null && connection.isConnected()) {
         this.unregisterCommandsForServer(serverName);
         List<Tool> tools = connection.getTools();
         if (tools != null && !tools.isEmpty()) {
            List<MCPToolCommand> commandsForServer = new ArrayList<>();

            for (Tool tool : tools) {
               try {
                  MCPToolCommand command = new MCPToolCommand(serverName, tool);
                  Commands.add(command);
                  commandsForServer.add(command);
               } catch (Exception var8) {
                  MeteorMCPAddon.LOG.error("Failed to register command for tool {}:{} - {}", new Object[]{serverName, tool.name(), var8.getMessage()});
               }
            }

            if (!commandsForServer.isEmpty()) {
               this.registeredCommands.put(serverName, commandsForServer);
               this.refreshCommandRegistry();
               MeteorMCPAddon.LOG.info("Registered {} MCP commands for server '{}'", commandsForServer.size(), serverName);
            }
         }
      }
   }

   private void unregisterCommandsForServer(String serverName) {
      List<MCPToolCommand> commands = this.registeredCommands.remove(serverName);
      boolean modified = false;
      if (commands != null && !commands.isEmpty()) {
         modified |= Commands.COMMANDS.removeAll(commands);
      }

      modified |= Commands.COMMANDS.removeIf(command -> command.getName().startsWith(serverName + ":"));
      if (modified) {
         this.refreshCommandRegistry();
         MeteorMCPAddon.LOG.info("Unregistered MCP commands for server '{}'", serverName);
      }
   }

   private void refreshCommandRegistry() {
      Commands.COMMANDS.sort(Comparator.comparing(Command::getName));
      CommandDispatcher<SharedSuggestionProvider> dispatcher = new CommandDispatcher();

      for (Command command : Commands.COMMANDS) {
         command.registerTo(dispatcher);
      }

      Commands.DISPATCHER = dispatcher;
   }

   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      ListTag serversList = new ListTag();

      for (MCPServerConfig config : this.configs.values()) {
         if ("minecraft".equals(config.getName())) continue;
         serversList.add(config.toTag());
      }

      tag.put("servers", serversList);
      tag.put("ai", this.aiConfig.toTag());
      return tag;
   }

   public MCPServers fromTag(CompoundTag tag) {
      if (tag.contains("servers")) {
         Tag element = tag.get("servers");
         if (element instanceof ListTag) {
            for (Tag serverElement : (ListTag)element) {
               try {
                  if (serverElement instanceof CompoundTag serverTag) {
                     MCPServerConfig config = MCPServerConfig.fromTag(serverTag);
                     this.configs.put(config.getName(), config);
                  }
               } catch (Exception var8) {
                  MeteorMCPAddon.LOG.error("Failed to load MCP server config: {}", var8.getMessage());
               }
            }
         }

         MeteorMCPAddon.LOG.info("Loaded {} MCP server configurations", this.configs.size());
      }

      if (tag.contains("ai")) {
         try {
            this.aiConfig = AIConfig.fromTag(tag.getCompound("ai"));
         } catch (Exception var3) {
            MeteorMCPAddon.LOG.error("Failed to load AI configuration: {}", var3.getMessage());
            this.aiConfig = new AIConfig();
         }
      } else if (tag.contains("gemini")) {
         try {
            this.aiConfig = AIConfig.migrateFromGeminiTag(tag.getCompound("gemini"));
            MeteorMCPAddon.LOG.info("Migrated legacy Gemini config to unified AI config");
         } catch (Exception var3) {
            MeteorMCPAddon.LOG.error("Failed to migrate Gemini configuration: {}", var3.getMessage());
            this.aiConfig = new AIConfig();
         }
      } else {
         this.aiConfig = new AIConfig();
      }

      if (!this.connections.containsKey("minecraft")) {
         MinecraftMCPServerConnection mcConn = new MinecraftMCPServerConnection();
         mcConn.connect();
         this.registerBuiltin(mcConn);
      }
      return this;
   }
}
