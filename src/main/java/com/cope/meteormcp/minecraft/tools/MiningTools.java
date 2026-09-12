package com.cope.meteormcp.minecraft.tools;

import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import com.cope.meteormcp.minecraft.MinecraftToolContext;
import com.cope.meteormcp.minecraft.MinecraftToolContext.ToolParam;
import com.cope.meteormcp.minecraft.MinecraftToolHandler;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class MiningTools implements MinecraftToolHandler {

   @Override
   public List<Tool> getTools() {
      List<Tool> list = new ArrayList<>();

      Map<String, ToolParam> mineBlockParams = new LinkedHashMap<>();
      mineBlockParams.put("block_name", new ToolParam("string", "Block registry name (e.g. 'iron_ore', 'diamond_ore', 'oak_log')", true));
      mineBlockParams.put("count", new ToolParam("integer", "Number of blocks to mine (default 1)", false));
      list.add(MinecraftToolContext.defineTool("mine_block", "Mines a specified block type using Baritone Mine process.", mineBlockParams));

      Map<String, ToolParam> mineAtParams = new LinkedHashMap<>();
      mineAtParams.put("x", new ToolParam("number", "Block X coordinate", true));
      mineAtParams.put("y", new ToolParam("number", "Block Y coordinate", true));
      mineAtParams.put("z", new ToolParam("number", "Block Z coordinate", true));
      list.add(MinecraftToolContext.defineTool("mine_at", "Mines the block at specific coordinates.", mineAtParams));

      return list;
   }

   @Override
   public CallToolResult execute(String toolName, Map<String, Object> arguments) {
      IBaritone baritone = MinecraftToolContext.getBaritone();

      switch (toolName) {
         case "mine_block": {
            if (baritone == null) return MinecraftToolContext.error("Baritone is not available.");
            String blockName = MinecraftToolContext.getString(arguments, "block_name", "");
            if (blockName.isBlank()) return MinecraftToolContext.error("block_name is required.");
            int count = MinecraftToolContext.getInt(arguments, "count", 1);
            MinecraftToolContext.runOnClient(() -> {
               baritone.getMineProcess().mineByName(count, blockName);
            });
            return MinecraftToolContext.result("Mining " + count + "x " + blockName + " initiated.");
         }
         case "mine_at": {
            int x = (int) Math.floor(MinecraftToolContext.getDouble(arguments, "x", 0));
            int y = (int) Math.floor(MinecraftToolContext.getDouble(arguments, "y", 0));
            int z = (int) Math.floor(MinecraftToolContext.getDouble(arguments, "z", 0));
            BlockPos pos = new BlockPos(x, y, z);
            return MinecraftToolContext.runOnClientSync(() -> {
               Minecraft mc = MinecraftToolContext.mc();
               if (mc.level == null || mc.player == null || mc.gameMode == null) {
                  return MinecraftToolContext.error("World/player not available.");
               }
               BlockState state = mc.level.getBlockState(pos);
               if (state.isAir()) {
                  return MinecraftToolContext.error("Block at " + pos.toShortString() + " is air.");
               }
               if (baritone != null) {
                  baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(pos));
               }
               mc.gameMode.startDestroyBlock(pos, Direction.UP);
               return MinecraftToolContext.result("Started mining " + state.getBlock().getName().getString() + " at " + pos.toShortString());
            });
         }
         default:
            return MinecraftToolContext.error("Unknown mining tool: " + toolName);
      }
   }
}
