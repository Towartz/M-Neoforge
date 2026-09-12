package com.cope.meteormcp.minecraft.tools;

import baritone.api.IBaritone;
import com.cope.meteormcp.minecraft.MinecraftToolContext;
import com.cope.meteormcp.minecraft.MinecraftToolContext.ToolParam;
import com.cope.meteormcp.minecraft.MinecraftToolHandler;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class BuildingTools implements MinecraftToolHandler {

   @Override
   public List<Tool> getTools() {
      List<Tool> list = new ArrayList<>();

      Map<String, ToolParam> placeParams = new LinkedHashMap<>();
      placeParams.put("block_name", new ToolParam("string", "Block/item registry name to place (e.g. 'cobblestone', 'dirt')", true));
      placeParams.put("x", new ToolParam("number", "Target X coordinate", true));
      placeParams.put("y", new ToolParam("number", "Target Y coordinate", true));
      placeParams.put("z", new ToolParam("number", "Target Z coordinate", true));
      list.add(MinecraftToolContext.defineTool("place_block", "Places a specific block from inventory at coordinates.", placeParams));

      Map<String, ToolParam> buildParams = new LinkedHashMap<>();
      buildParams.put("schematic_name", new ToolParam("string", "Schematic file name to build via Baritone", true));
      list.add(MinecraftToolContext.defineTool("build_schematic", "Loads and executes a Baritone schematic build.", buildParams));

      Map<String, ToolParam> clearParams = new LinkedHashMap<>();
      clearParams.put("x1", new ToolParam("number", "First corner X coordinate", true));
      clearParams.put("y1", new ToolParam("number", "First corner Y coordinate", true));
      clearParams.put("z1", new ToolParam("number", "First corner Z coordinate", true));
      clearParams.put("x2", new ToolParam("number", "Second corner X coordinate", true));
      clearParams.put("y2", new ToolParam("number", "Second corner Y coordinate", true));
      clearParams.put("z2", new ToolParam("number", "Second corner Z coordinate", true));
      list.add(MinecraftToolContext.defineTool("clear_area", "Clears blocks within a 3D bounding box using Baritone.", clearParams));

      return list;
   }

   @Override
   public CallToolResult execute(String toolName, Map<String, Object> arguments) {
      IBaritone baritone = MinecraftToolContext.getBaritone();

      switch (toolName) {
         case "place_block": {
            String blockName = MinecraftToolContext.getString(arguments, "block_name", "");
            if (blockName.isBlank()) return MinecraftToolContext.error("block_name is required.");
            int x = (int) Math.floor(MinecraftToolContext.getDouble(arguments, "x", 0));
            int y = (int) Math.floor(MinecraftToolContext.getDouble(arguments, "y", 0));
            int z = (int) Math.floor(MinecraftToolContext.getDouble(arguments, "z", 0));
            BlockPos pos = new BlockPos(x, y, z);
            return MinecraftToolContext.runOnClientSync(() -> {
               Minecraft mc = MinecraftToolContext.mc();
               if (mc.level == null || mc.player == null) return MinecraftToolContext.error("World/player not available.");
               ResourceLocation rl = blockName.contains(":") ? ResourceLocation.parse(blockName) : ResourceLocation.withDefaultNamespace(blockName);
               Item item = BuiltInRegistries.ITEM.get(rl);
               if (item == Items.AIR) return MinecraftToolContext.error("Unknown block/item: " + blockName);
               FindItemResult findResult = InvUtils.find(item);
               if (!findResult.found()) return MinecraftToolContext.error("Item " + blockName + " not found in inventory.");
               boolean placed = BlockUtils.place(pos, findResult, true, 50, true, true);
               if (placed) {
                  return MinecraftToolContext.result("Successfully placed " + blockName + " at " + pos.toShortString());
               } else {
                  return MinecraftToolContext.error("Could not place " + blockName + " at " + pos.toShortString() + " (position blocked or unreachable).");
               }
            });
         }
         case "build_schematic": {
            if (baritone == null) return MinecraftToolContext.error("Baritone is not available.");
            String name = MinecraftToolContext.getString(arguments, "schematic_name", "");
            if (name.isBlank()) return MinecraftToolContext.error("schematic_name is required.");
            MinecraftToolContext.runOnClient(() -> {
               baritone.getCommandManager().execute("build " + name);
            });
            return MinecraftToolContext.result("Executed Baritone build schematic command: " + name);
         }
         case "clear_area": {
            if (baritone == null) return MinecraftToolContext.error("Baritone is not available.");
            int x1 = (int) Math.floor(MinecraftToolContext.getDouble(arguments, "x1", 0));
            int y1 = (int) Math.floor(MinecraftToolContext.getDouble(arguments, "y1", 0));
            int z1 = (int) Math.floor(MinecraftToolContext.getDouble(arguments, "z1", 0));
            int x2 = (int) Math.floor(MinecraftToolContext.getDouble(arguments, "x2", 0));
            int y2 = (int) Math.floor(MinecraftToolContext.getDouble(arguments, "y2", 0));
            int z2 = (int) Math.floor(MinecraftToolContext.getDouble(arguments, "z2", 0));
            MinecraftToolContext.runOnClient(() -> {
               baritone.getBuilderProcess().clearArea(new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2));
            });
            return MinecraftToolContext.result("Clearing area between (" + x1 + "," + y1 + "," + z1 + ") and (" + x2 + "," + y2 + "," + z2 + ")");
         }
         default:
            return MinecraftToolContext.error("Unknown building tool: " + toolName);
      }
   }
}
