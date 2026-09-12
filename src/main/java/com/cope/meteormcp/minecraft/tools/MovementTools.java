package com.cope.meteormcp.minecraft.tools;

import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
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

public class MovementTools implements MinecraftToolHandler {

   @Override
   public List<Tool> getTools() {
      List<Tool> list = new ArrayList<>();

      Map<String, ToolParam> moveParams = new LinkedHashMap<>();
      moveParams.put("x", new ToolParam("number", "Target X coordinate", true));
      moveParams.put("y", new ToolParam("number", "Target Y coordinate", true));
      moveParams.put("z", new ToolParam("number", "Target Z coordinate", true));
      list.add(MinecraftToolContext.defineTool("move_to", "Commands Baritone to walk to target coordinates (GoalBlock).", moveParams));

      Map<String, ToolParam> pathParams = new LinkedHashMap<>();
      pathParams.put("x", new ToolParam("number", "Target X coordinate", true));
      pathParams.put("y", new ToolParam("number", "Target Y coordinate", true));
      pathParams.put("z", new ToolParam("number", "Target Z coordinate", true));
      pathParams.put("radius", new ToolParam("integer", "Arrival radius around target (default 2)", false));
      list.add(MinecraftToolContext.defineTool("pathfind_to", "Pathfinds within a specified radius of a position (GoalNear).", pathParams));

      list.add(MinecraftToolContext.defineTool("stop", "Immediately halts all current movements and pathfinding.", Map.of()));

      Map<String, ToolParam> followParams = new LinkedHashMap<>();
      followParams.put("target", new ToolParam("string", "Name or type of player/entity to follow", true));
      list.add(MinecraftToolContext.defineTool("follow", "Follows a named player or entity using Baritone follow process.", followParams));

      Map<String, ToolParam> lookParams = new LinkedHashMap<>();
      lookParams.put("yaw", new ToolParam("number", "Yaw rotation angle in degrees", false));
      lookParams.put("pitch", new ToolParam("number", "Pitch rotation angle in degrees", false));
      lookParams.put("x", new ToolParam("number", "Target X coordinate to face", false));
      lookParams.put("y", new ToolParam("number", "Target Y coordinate to face", false));
      lookParams.put("z", new ToolParam("number", "Target Z coordinate to face", false));
      list.add(MinecraftToolContext.defineTool("look_at", "Rotates player view towards pitch/yaw or specific coordinates.", lookParams));

      list.add(MinecraftToolContext.defineTool("jump", "Makes player jump from the ground.", Map.of()));

      return list;
   }

   @Override
   public CallToolResult execute(String toolName, Map<String, Object> arguments) {
      IBaritone baritone = MinecraftToolContext.getBaritone();

      switch (toolName) {
         case "move_to": {
            if (baritone == null) return MinecraftToolContext.error("Baritone is not available.");
            int x = (int) Math.floor(MinecraftToolContext.getDouble(arguments, "x", 0));
            int y = (int) Math.floor(MinecraftToolContext.getDouble(arguments, "y", 0));
            int z = (int) Math.floor(MinecraftToolContext.getDouble(arguments, "z", 0));
            MinecraftToolContext.runOnClient(() -> {
               baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(x, y, z));
            });
            return MinecraftToolContext.result("Pathfinding to block (" + x + ", " + y + ", " + z + ") initiated.");
         }
         case "pathfind_to": {
            if (baritone == null) return MinecraftToolContext.error("Baritone is not available.");
            int x = (int) Math.floor(MinecraftToolContext.getDouble(arguments, "x", 0));
            int y = (int) Math.floor(MinecraftToolContext.getDouble(arguments, "y", 0));
            int z = (int) Math.floor(MinecraftToolContext.getDouble(arguments, "z", 0));
            int radius = MinecraftToolContext.getInt(arguments, "radius", 2);
            MinecraftToolContext.runOnClient(() -> {
               baritone.getCustomGoalProcess().setGoalAndPath(new GoalNear(new BlockPos(x, y, z), radius));
            });
            return MinecraftToolContext.result("Pathfinding near (" + x + ", " + y + ", " + z + ") with radius " + radius + " initiated.");
         }
         case "stop": {
            MinecraftToolContext.runOnClient(() -> {
               if (baritone != null) {
                  baritone.getPathingBehavior().cancelEverything();
                  baritone.getPathingBehavior().forceCancel();
               }
            });
            return MinecraftToolContext.result("Movement and pathfinding stopped.");
         }
         case "follow": {
            if (baritone == null) return MinecraftToolContext.error("Baritone is not available.");
            String target = MinecraftToolContext.getString(arguments, "target", "");
            if (target.isBlank()) return MinecraftToolContext.error("Target name is required.");
            MinecraftToolContext.runOnClient(() -> {
               baritone.getFollowProcess().follow(entity -> {
                  if (entity == null) return false;
                  String name = entity.getName().getString();
                  String type = entity.getType().getDescription().getString();
                  return name.equalsIgnoreCase(target) || type.equalsIgnoreCase(target);
               });
            });
            return MinecraftToolContext.result("Following target: " + target);
         }
         case "look_at": {
            return MinecraftToolContext.runOnClientSync(() -> {
               Minecraft mc = MinecraftToolContext.mc();
               if (mc.player == null) return MinecraftToolContext.error("Player not available.");
               if (arguments.containsKey("yaw") || arguments.containsKey("pitch")) {
                  float yaw = (float) MinecraftToolContext.getDouble(arguments, "yaw", mc.player.getYRot());
                  float pitch = (float) MinecraftToolContext.getDouble(arguments, "pitch", mc.player.getXRot());
                  mc.player.setYRot(yaw);
                  mc.player.setXRot(pitch);
                  return MinecraftToolContext.result("Rotated look angle to yaw=" + yaw + ", pitch=" + pitch);
               } else if (arguments.containsKey("x") && arguments.containsKey("y") && arguments.containsKey("z")) {
                  double tx = MinecraftToolContext.getDouble(arguments, "x", 0);
                  double ty = MinecraftToolContext.getDouble(arguments, "y", 0);
                  double tz = MinecraftToolContext.getDouble(arguments, "z", 0);
                  double dx = tx - mc.player.getX();
                  double dy = ty - mc.player.getEyeY();
                  double dz = tz - mc.player.getZ();
                  double dist = Math.sqrt(dx * dx + dz * dz);
                  float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                  float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
                  mc.player.setYRot(yaw);
                  mc.player.setXRot(pitch);
                  return MinecraftToolContext.result("Rotated look angle towards (" + tx + ", " + ty + ", " + tz + ")");
               } else {
                  return MinecraftToolContext.error("Provide either yaw/pitch or x/y/z coordinates.");
               }
            });
         }
         case "jump": {
            return MinecraftToolContext.runOnClientSync(() -> {
               Minecraft mc = MinecraftToolContext.mc();
               if (mc.player == null) return MinecraftToolContext.error("Player not available.");
               mc.player.jumpFromGround();
               return MinecraftToolContext.result("Player jumped.");
            });
         }
         default:
            return MinecraftToolContext.error("Unknown movement tool: " + toolName);
      }
   }
}
