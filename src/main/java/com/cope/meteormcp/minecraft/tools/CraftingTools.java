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
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.AutoCraft;
import meteordevelopment.meteorclient.systems.modules.player.autocraft.CraftItemResolver;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class CraftingTools implements MinecraftToolHandler {

   @Override
   public List<Tool> getTools() {
      List<Tool> list = new ArrayList<>();

      Map<String, ToolParam> craftParams = new LinkedHashMap<>();
      craftParams.put("item_name", new ToolParam("string", "Registry name of item to craft (e.g. 'wooden_pickaxe', 'chest')", true));
      craftParams.put("count", new ToolParam("integer", "Number of items to craft (default 1)", false));
      list.add(MinecraftToolContext.defineTool("craft_item", "Queues crafting of an item via AutoCraft.", craftParams));

      Map<String, ToolParam> setParams = new LinkedHashMap<>();
      setParams.put("set_type", new ToolParam("string", "Type of set: 'armor' or 'tools'", true));
      setParams.put("material", new ToolParam("string", "Material name: 'wood', 'stone', 'iron', 'diamond', 'netherite', 'gold', 'leather'", true));
      setParams.put("count", new ToolParam("integer", "Number of complete sets to craft (default 1)", false));
      list.add(MinecraftToolContext.defineTool("craft_set", "Crafts a full armor or tools set of the specified material.", setParams));

      list.add(MinecraftToolContext.defineTool("cancel_crafting", "Cancels current AutoCraft tasks and clears the crafting queue.", Map.of()));

      list.add(MinecraftToolContext.defineTool("get_craft_status", "Returns current crafting progress, state, and queued tasks.", Map.of()));

      return list;
   }

   @Override
   public CallToolResult execute(String toolName, Map<String, Object> arguments) {
      switch (toolName) {
         case "craft_item": {
            String itemName = MinecraftToolContext.getString(arguments, "item_name", "");
            if (itemName.isBlank()) return MinecraftToolContext.error("item_name is required.");
            int count = MinecraftToolContext.getInt(arguments, "count", 1);
            return MinecraftToolContext.runOnClientSync(() -> {
               AutoCraft autoCraft = Modules.get().get(AutoCraft.class);
               if (autoCraft == null) return MinecraftToolContext.error("AutoCraft module not found.");
               ResourceLocation rl = itemName.contains(":") ? ResourceLocation.parse(itemName) : ResourceLocation.withDefaultNamespace(itemName);
               Item item = BuiltInRegistries.ITEM.get(rl);
               if (item == Items.AIR) return MinecraftToolContext.error("Unknown item: " + itemName);
               if (!autoCraft.isActive()) autoCraft.toggle();
               autoCraft.queueCraft(item, count);
               return MinecraftToolContext.result("Queued crafting " + count + "x " + item.getDescription().getString());
            });
         }
         case "craft_set": {
            String setType = MinecraftToolContext.getString(arguments, "set_type", "").toLowerCase();
            String material = MinecraftToolContext.getString(arguments, "material", "").toLowerCase();
            int count = MinecraftToolContext.getInt(arguments, "count", 1);
            return MinecraftToolContext.runOnClientSync(() -> {
               AutoCraft autoCraft = Modules.get().get(AutoCraft.class);
               if (autoCraft == null) return MinecraftToolContext.error("AutoCraft module not found.");
               List<Item> items;
               if (setType.equals("armor")) {
                  items = CraftItemResolver.getArmorSet(material);
               } else if (setType.equals("tools") || setType.equals("tool")) {
                  items = CraftItemResolver.getToolsSet(material);
               } else {
                  return MinecraftToolContext.error("Invalid set_type. Must be 'armor' or 'tools'.");
               }
               if (items.isEmpty()) return MinecraftToolContext.error("Could not resolve " + setType + " set for material: " + material);
               if (!autoCraft.isActive()) autoCraft.toggle();
               autoCraft.queueBundle(items, count);
               return MinecraftToolContext.result("Queued " + count + "x " + material + " " + setType + " set (" + items.size() + " items).");
            });
         }
         case "cancel_crafting": {
            return MinecraftToolContext.runOnClientSync(() -> {
               AutoCraft autoCraft = Modules.get().get(AutoCraft.class);
               if (autoCraft == null) return MinecraftToolContext.error("AutoCraft module not found.");
               autoCraft.cancelTask();
               return MinecraftToolContext.result("AutoCraft tasks cancelled and queue cleared.");
            });
         }
         case "get_craft_status": {
            return MinecraftToolContext.runOnClientSync(() -> {
               AutoCraft autoCraft = Modules.get().get(AutoCraft.class);
               if (autoCraft == null) return MinecraftToolContext.error("AutoCraft module not found.");
               Map<String, Object> status = new LinkedHashMap<>();
               status.put("active", autoCraft.isActive());
               status.put("state", autoCraft.getCraftState() != null ? autoCraft.getCraftState().name() : "IDLE");
               status.put("queueSize", autoCraft.getQueueSize());
               AutoCraft.CraftTask task = autoCraft.getCurrentTask();
               if (task != null) {
                  Map<String, Object> taskInfo = new LinkedHashMap<>();
                  taskInfo.put("item", task.item.getDescription().getString());
                  taskInfo.put("remaining", task.remainingCount);
                  status.put("currentTask", taskInfo);
               } else {
                  status.put("currentTask", null);
               }
               return MinecraftToolContext.json(status);
            });
         }
         default:
            return MinecraftToolContext.error("Unknown crafting tool: " + toolName);
      }
   }
}
