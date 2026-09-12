package com.cope.meteormcp.minecraft;

import com.cope.meteormcp.minecraft.tools.BuildingTools;
import com.cope.meteormcp.minecraft.tools.CombatTools;
import com.cope.meteormcp.minecraft.tools.CraftingTools;
import com.cope.meteormcp.minecraft.tools.InventoryTools;
import com.cope.meteormcp.minecraft.tools.MeteorTools;
import com.cope.meteormcp.minecraft.tools.MiningTools;
import com.cope.meteormcp.minecraft.tools.MovementTools;
import com.cope.meteormcp.minecraft.tools.PerceptionTools;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MinecraftToolRegistry {
   private static final List<MinecraftToolHandler> HANDLERS = List.of(
      new MovementTools(),
      new MiningTools(),
      new BuildingTools(),
      new CraftingTools(),
      new InventoryTools(),
      new PerceptionTools(),
      new CombatTools(),
      new MeteorTools()
   );

   private static final List<Tool> ALL_TOOLS;
   private static final Map<String, MinecraftToolHandler> TOOL_TO_HANDLER;

   static {
      List<Tool> tools = new ArrayList<>();
      Map<String, MinecraftToolHandler> handlerMap = new LinkedHashMap<>();

      for (MinecraftToolHandler handler : HANDLERS) {
         for (Tool tool : handler.getTools()) {
            tools.add(tool);
            handlerMap.put(tool.name(), handler);
         }
      }

      ALL_TOOLS = Collections.unmodifiableList(tools);
      TOOL_TO_HANDLER = Collections.unmodifiableMap(handlerMap);
   }

   public static List<Tool> getTools() {
      return ALL_TOOLS;
   }

   public static Tool getTool(String name) {
      if (name == null) return null;
      for (Tool tool : ALL_TOOLS) {
         if (tool.name().equals(name)) return tool;
      }
      return null;
   }

   public static CallToolResult execute(String toolName, Map<String, Object> arguments) {
      MinecraftToolHandler handler = TOOL_TO_HANDLER.get(toolName);
      if (handler == null) {
         return MinecraftToolContext.error("Tool '" + toolName + "' not found in Minecraft server.");
      }
      try {
         return handler.execute(toolName, arguments != null ? arguments : Map.of());
      } catch (Throwable t) {
         return MinecraftToolContext.error("Execution error in '" + toolName + "': " + t.getMessage());
      }
   }
}
