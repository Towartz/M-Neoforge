package com.cope.meteormcp.minecraft.tools;

import com.cope.meteormcp.minecraft.MinecraftToolContext;
import com.cope.meteormcp.minecraft.MinecraftToolContext.ToolParam;
import com.cope.meteormcp.minecraft.MinecraftToolHandler;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;

public class MeteorTools implements MinecraftToolHandler {

   @Override
   public List<Tool> getTools() {
      List<Tool> list = new ArrayList<>();

      Map<String, ToolParam> toggleParams = new LinkedHashMap<>();
      toggleParams.put("module_name", new ToolParam("string", "Meteor module name (e.g. 'kill-aura', 'auto-eat', 'scaffold')", true));
      toggleParams.put("state", new ToolParam("boolean", "Desired state: true=enable, false=disable (optional, toggles if omitted)", false));
      list.add(MinecraftToolContext.defineTool("toggle_module", "Toggles or sets the enabled status of any Meteor Client module.", toggleParams));

      Map<String, ToolParam> getModulesParams = new LinkedHashMap<>();
      getModulesParams.put("category", new ToolParam("string", "Filter by category: 'combat', 'player', 'movement', 'render', 'world', 'misc'", false));
      getModulesParams.put("only_active", new ToolParam("boolean", "If true, only returns currently enabled modules (default false)", false));
      list.add(MinecraftToolContext.defineTool("get_modules", "Lists available Meteor client modules with status and description.", getModulesParams));

      Map<String, ToolParam> commandParams = new LinkedHashMap<>();
      commandParams.put("command", new ToolParam("string", "Command line to execute (e.g. '.toggle kill-aura' or 'say hello')", true));
      list.add(MinecraftToolContext.defineTool("execute_command", "Executes a Meteor client command or chat command.", commandParams));

      Map<String, ToolParam> chatParams = new LinkedHashMap<>();
      chatParams.put("message", new ToolParam("string", "Chat message text to send", true));
      list.add(MinecraftToolContext.defineTool("send_chat", "Sends a chat message to the multiplayer server.", chatParams));

      return list;
   }

   @Override
   public CallToolResult execute(String toolName, Map<String, Object> arguments) {
      switch (toolName) {
         case "toggle_module": {
            String name = MinecraftToolContext.getString(arguments, "module_name", "");
            if (name.isBlank()) return MinecraftToolContext.error("module_name is required.");
            Boolean targetState = arguments.containsKey("state") ? MinecraftToolContext.getBoolean(arguments, "state", false) : null;
            return MinecraftToolContext.runOnClientSync(() -> {
               Module module = Modules.get().get(name);
               if (module == null) return MinecraftToolContext.error("Meteor module not found: " + name);
               if (targetState != null) {
                  if (module.isActive() != targetState) {
                     module.toggle();
                  }
               } else {
                  module.toggle();
               }
               return MinecraftToolContext.result("Module '" + module.title + "' is now " + (module.isActive() ? "ENABLED" : "DISABLED"));
            });
         }
         case "get_modules": {
            String catFilter = MinecraftToolContext.getString(arguments, "category", null);
            boolean onlyActive = MinecraftToolContext.getBoolean(arguments, "only_active", false);
            return MinecraftToolContext.runOnClientSync(() -> {
               List<Map<String, Object>> result = new ArrayList<>();
               for (Module module : Modules.get().getAll()) {
                  if (onlyActive && !module.isActive()) continue;
                  if (catFilter != null && !catFilter.isBlank() && !module.category.name.equalsIgnoreCase(catFilter)) continue;
                  Map<String, Object> mData = new LinkedHashMap<>();
                  mData.put("name", module.name);
                  mData.put("title", module.title);
                  mData.put("category", module.category.name);
                  mData.put("active", module.isActive());
                  mData.put("description", module.description);
                  result.add(mData);
               }
               return MinecraftToolContext.json(result);
            });
         }
         case "execute_command": {
            String cmd = MinecraftToolContext.getString(arguments, "command", "");
            if (cmd.isBlank()) return MinecraftToolContext.error("command is required.");
            if (cmd.startsWith(".")) cmd = cmd.substring(1);
            final String finalCmd = cmd;
            return MinecraftToolContext.runOnClientSync(() -> {
               try {
                  Commands.dispatch(finalCmd);
                  return MinecraftToolContext.result("Executed command: ." + finalCmd);
               } catch (Exception e) {
                  return MinecraftToolContext.error("Failed to execute command '." + finalCmd + "': " + e.getMessage());
               }
            });
         }
         case "send_chat": {
            String message = MinecraftToolContext.getString(arguments, "message", "");
            if (message.isBlank()) return MinecraftToolContext.error("message is required.");
            return MinecraftToolContext.runOnClientSync(() -> {
               ChatUtils.sendPlayerMsg(message);
               return MinecraftToolContext.result("Sent chat message: " + message);
            });
         }
         default:
            return MinecraftToolContext.error("Unknown meteor tool: " + toolName);
      }
   }
}
