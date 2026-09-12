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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

public class PerceptionTools implements MinecraftToolHandler {

   @Override
   public List<Tool> getTools() {
      List<Tool> list = new ArrayList<>();

      list.add(MinecraftToolContext.defineTool("get_player_status", "Returns player health, food, coordinates, rotation, dimension, and status effects.", Map.of()));

      Map<String, ToolParam> entityParams = new LinkedHashMap<>();
      entityParams.put("radius", new ToolParam("number", "Search radius in blocks (default 16)", false));
      entityParams.put("entity_type", new ToolParam("string", "Filter by entity type (e.g. 'zombie', 'cow', 'player')", false));
      list.add(MinecraftToolContext.defineTool("get_nearby_entities", "Scans for entities near player.", entityParams));

      Map<String, ToolParam> blockParams = new LinkedHashMap<>();
      blockParams.put("radius", new ToolParam("integer", "Search radius in blocks (max 16, default 8)", false));
      blockParams.put("block_types", new ToolParam("string", "Comma-separated block names to find (e.g. 'chest,crafting_table,diamond_ore')", false));
      list.add(MinecraftToolContext.defineTool("get_nearby_blocks", "Scans for specific blocks in a radius around player.", blockParams));

      return list;
   }

   @Override
   public CallToolResult execute(String toolName, Map<String, Object> arguments) {
      switch (toolName) {
         case "get_player_status": {
            return MinecraftToolContext.runOnClientSync(() -> {
               Minecraft mc = MinecraftToolContext.mc();
               if (mc.player == null) return MinecraftToolContext.error("Player not available.");
               Map<String, Object> status = new LinkedHashMap<>();
               status.put("name", mc.player.getName().getString());
               status.put("health", mc.player.getHealth());
               status.put("maxHealth", mc.player.getMaxHealth());
               status.put("food", mc.player.getFoodData().getFoodLevel());
               status.put("saturation", mc.player.getFoodData().getSaturationLevel());

               Map<String, Object> pos = new LinkedHashMap<>();
               pos.put("x", Math.round(mc.player.getX() * 100.0) / 100.0);
               pos.put("y", Math.round(mc.player.getY() * 100.0) / 100.0);
               pos.put("z", Math.round(mc.player.getZ() * 100.0) / 100.0);
               status.put("position", pos);

               status.put("yaw", Math.round(mc.player.getYRot() * 10.0) / 10.0);
               status.put("pitch", Math.round(mc.player.getXRot() * 10.0) / 10.0);
               status.put("dimension", mc.player.level().dimension().location().toString());
               status.put("gamemode", mc.gameMode != null && mc.gameMode.getPlayerMode() != null ? mc.gameMode.getPlayerMode().getName() : "unknown");
               status.put("onGround", mc.player.onGround());
               status.put("inWater", mc.player.isInWater());

               List<String> effects = new ArrayList<>();
               for (MobEffectInstance inst : mc.player.getActiveEffects()) {
                  effects.add(inst.getDescriptionId() + " (amp=" + inst.getAmplifier() + ", dur=" + inst.getDuration() + "t)");
               }
               status.put("effects", effects);
               return MinecraftToolContext.json(status);
            });
         }
         case "get_nearby_entities": {
            double radius = MinecraftToolContext.getDouble(arguments, "radius", 16.0);
            String filterType = MinecraftToolContext.getString(arguments, "entity_type", null);
            return MinecraftToolContext.runOnClientSync(() -> {
               Minecraft mc = MinecraftToolContext.mc();
               if (mc.level == null || mc.player == null) return MinecraftToolContext.error("Level/player not available.");
               List<Map<String, Object>> entities = new ArrayList<>();
               for (Entity entity : mc.level.entitiesForRendering()) {
                  if (entity == mc.player) continue;
                  double dist = mc.player.distanceTo(entity);
                  if (dist <= radius) {
                     String typeKey = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
                     if (filterType != null && !filterType.isBlank() && !typeKey.toLowerCase().contains(filterType.toLowerCase())) {
                        continue;
                     }
                     Map<String, Object> data = new LinkedHashMap<>();
                     data.put("id", entity.getId());
                     data.put("name", entity.getName().getString());
                     data.put("type", typeKey);
                     data.put("distance", Math.round(dist * 10.0) / 10.0);
                     Map<String, Object> epos = new LinkedHashMap<>();
                     epos.put("x", Math.round(entity.getX() * 10.0) / 10.0);
                     epos.put("y", Math.round(entity.getY() * 10.0) / 10.0);
                     epos.put("z", Math.round(entity.getZ() * 10.0) / 10.0);
                     data.put("position", epos);
                     if (entity instanceof LivingEntity living) {
                        data.put("health", Math.round(living.getHealth() * 10.0) / 10.0);
                        data.put("maxHealth", Math.round(living.getMaxHealth() * 10.0) / 10.0);
                     }
                     entities.add(data);
                     if (entities.size() >= 50) break;
                  }
               }
               return MinecraftToolContext.json(entities);
            });
         }
         case "get_nearby_blocks": {
            int radius = Math.min(MinecraftToolContext.getInt(arguments, "radius", 8), 16);
            String blockFilter = MinecraftToolContext.getString(arguments, "block_types", null);
            return MinecraftToolContext.runOnClientSync(() -> {
               Minecraft mc = MinecraftToolContext.mc();
               if (mc.level == null || mc.player == null) return MinecraftToolContext.error("Level/player not available.");
               BlockPos playerPos = mc.player.blockPosition();
               List<Map<String, Object>> found = new ArrayList<>();
               BlockPos.betweenClosedStream(playerPos.offset(-radius, -radius, -radius), playerPos.offset(radius, radius, radius)).forEach(bp -> {
                  if (found.size() >= 100) return;
                  BlockState state = mc.level.getBlockState(bp);
                  if (state.isAir()) return;
                  String blockKey = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                  if (blockFilter != null && !blockFilter.isBlank()) {
                     boolean match = false;
                     for (String part : blockFilter.split(",")) {
                        if (blockKey.toLowerCase().contains(part.trim().toLowerCase())) {
                           match = true;
                           break;
                        }
                     }
                     if (!match) return;
                  }
                  Map<String, Object> bData = new LinkedHashMap<>();
                  bData.put("name", blockKey);
                  bData.put("x", bp.getX());
                  bData.put("y", bp.getY());
                  bData.put("z", bp.getZ());
                  found.add(bData);
               });
               return MinecraftToolContext.json(found);
            });
         }
         default:
            return MinecraftToolContext.error("Unknown perception tool: " + toolName);
      }
   }
}
