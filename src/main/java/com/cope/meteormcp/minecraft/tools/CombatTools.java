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
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class CombatTools implements MinecraftToolHandler {

   @Override
   public List<Tool> getTools() {
      List<Tool> list = new ArrayList<>();

      Map<String, ToolParam> attackParams = new LinkedHashMap<>();
      attackParams.put("target", new ToolParam("string", "Entity name, registry type, or numeric ID to attack", true));
      list.add(MinecraftToolContext.defineTool("attack_target", "Attacks a specified target entity within reach.", attackParams));

      Map<String, ToolParam> blockParams = new LinkedHashMap<>();
      blockParams.put("x", new ToolParam("number", "Target block X coordinate", true));
      blockParams.put("y", new ToolParam("number", "Target block Y coordinate", true));
      blockParams.put("z", new ToolParam("number", "Target block Z coordinate", true));
      blockParams.put("hand", new ToolParam("string", "Hand to interact with: 'mainhand' or 'offhand' (default 'mainhand')", false));
      list.add(MinecraftToolContext.defineTool("interact_block", "Interacts (right clicks) with a block at coordinates.", blockParams));

      Map<String, ToolParam> interactEntityParams = new LinkedHashMap<>();
      interactEntityParams.put("target", new ToolParam("string", "Entity name, registry type, or numeric ID to interact with", true));
      interactEntityParams.put("hand", new ToolParam("string", "Hand to interact with: 'mainhand' or 'offhand' (default 'mainhand')", false));
      list.add(MinecraftToolContext.defineTool("interact_entity", "Interacts (right clicks) with an entity (trading, mounting, etc.).", interactEntityParams));

      return list;
   }

   @Override
   public CallToolResult execute(String toolName, Map<String, Object> arguments) {
      switch (toolName) {
         case "attack_target": {
            String targetStr = MinecraftToolContext.getString(arguments, "target", "");
            if (targetStr.isBlank()) return MinecraftToolContext.error("target is required.");
            return MinecraftToolContext.runOnClientSync(() -> {
               Minecraft mc = MinecraftToolContext.mc();
               if (mc.level == null || mc.player == null || mc.gameMode == null) {
                  return MinecraftToolContext.error("Level/player not available.");
               }
               Entity targetEntity = null;
               try {
                  int entityId = Integer.parseInt(targetStr);
                  targetEntity = mc.level.getEntity(entityId);
               } catch (NumberFormatException ignored) {}

               if (targetEntity == null) {
                  for (Entity e : mc.level.entitiesForRendering()) {
                     if (e == mc.player) continue;
                     if (e.getName().getString().equalsIgnoreCase(targetStr) ||
                         BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equalsIgnoreCase(targetStr)) {
                        targetEntity = e;
                        break;
                     }
                  }
               }

               if (targetEntity == null) return MinecraftToolContext.error("Target entity not found: " + targetStr);
               mc.gameMode.attack(mc.player, targetEntity);
               mc.player.swing(InteractionHand.MAIN_HAND);
               return MinecraftToolContext.result("Attacked entity: " + targetEntity.getName().getString() + " (id=" + targetEntity.getId() + ")");
            });
         }
         case "interact_block": {
            int x = (int) Math.floor(MinecraftToolContext.getDouble(arguments, "x", 0));
            int y = (int) Math.floor(MinecraftToolContext.getDouble(arguments, "y", 0));
            int z = (int) Math.floor(MinecraftToolContext.getDouble(arguments, "z", 0));
            String handStr = MinecraftToolContext.getString(arguments, "hand", "mainhand");
            InteractionHand hand = handStr.equalsIgnoreCase("offhand") ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            BlockPos pos = new BlockPos(x, y, z);
            return MinecraftToolContext.runOnClientSync(() -> {
               Minecraft mc = MinecraftToolContext.mc();
               if (mc.player == null || mc.gameMode == null) return MinecraftToolContext.error("Player/gameMode not available.");
               BlockHitResult hit = new BlockHitResult(new Vec3(x + 0.5, y + 0.5, z + 0.5), Direction.UP, pos, false);
               mc.gameMode.useItemOn(mc.player, hand, hit);
               return MinecraftToolContext.result("Interacted with block at " + pos.toShortString());
            });
         }
         case "interact_entity": {
            String targetStr = MinecraftToolContext.getString(arguments, "target", "");
            if (targetStr.isBlank()) return MinecraftToolContext.error("target is required.");
            String handStr = MinecraftToolContext.getString(arguments, "hand", "mainhand");
            InteractionHand hand = handStr.equalsIgnoreCase("offhand") ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            return MinecraftToolContext.runOnClientSync(() -> {
               Minecraft mc = MinecraftToolContext.mc();
               if (mc.level == null || mc.player == null || mc.gameMode == null) {
                  return MinecraftToolContext.error("Level/player not available.");
               }
               Entity targetEntity = null;
               try {
                  int entityId = Integer.parseInt(targetStr);
                  targetEntity = mc.level.getEntity(entityId);
               } catch (NumberFormatException ignored) {}

               if (targetEntity == null) {
                  for (Entity e : mc.level.entitiesForRendering()) {
                     if (e == mc.player) continue;
                     if (e.getName().getString().equalsIgnoreCase(targetStr) ||
                         BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equalsIgnoreCase(targetStr)) {
                        targetEntity = e;
                        break;
                     }
                  }
               }

               if (targetEntity == null) return MinecraftToolContext.error("Target entity not found: " + targetStr);
               mc.gameMode.interact(mc.player, targetEntity, hand);
               return MinecraftToolContext.result("Interacted with entity: " + targetEntity.getName().getString());
            });
         }
         default:
            return MinecraftToolContext.error("Unknown combat tool: " + toolName);
      }
   }
}
