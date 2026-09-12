package com.cope.meteormcp.minecraft;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import com.cope.meteormcp.MeteorMCPAddon;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.Minecraft;

public class MinecraftToolContext {
   public static Minecraft mc() {
      return MeteorClient.mc != null ? MeteorClient.mc : Minecraft.getInstance();
   }

   public static IBaritone getBaritone() {
      try {
         if (BaritoneAPI.getProvider() != null) {
            return BaritoneAPI.getProvider().getPrimaryBaritone();
         }
      } catch (Throwable t) {
         MeteorMCPAddon.LOG.warn("Could not get Baritone instance: {}", t.getMessage());
      }
      return null;
   }

   public static <T> T runOnClientSync(Supplier<T> supplier) {
      Minecraft mc = mc();
      if (mc == null) return supplier.get();
      if (mc.isSameThread()) {
         return supplier.get();
      }
      return CompletableFuture.supplyAsync(supplier, mc).join();
   }

   public static void runOnClient(Runnable action) {
      Minecraft mc = mc();
      if (mc == null) {
         action.run();
         return;
      }
      if (mc.isSameThread()) {
         action.run();
      } else {
         mc.execute(action);
      }
   }

   public static CallToolResult result(String text) {
      return CallToolResult.builder()
         .content(List.of(new TextContent(text != null ? text : "")))
         .isError(false)
         .build();
   }

   public static CallToolResult error(String errorMsg) {
      return CallToolResult.builder()
         .content(List.of(new TextContent(errorMsg != null ? errorMsg : "Unknown error")))
         .isError(true)
         .build();
   }

   public static CallToolResult json(Object obj) {
      try {
         String json = McpJsonDefaults.getMapper().writeValueAsString(obj);
         return result(json);
      } catch (Exception e) {
         return result(String.valueOf(obj));
      }
   }

   public record ToolParam(String type, String description, boolean required) {}

   public static Tool defineTool(String name, String description, Map<String, ToolParam> params) {
      Map<String, Object> properties = new LinkedHashMap<>();
      List<String> requiredList = new ArrayList<>();

      if (params != null) {
         for (Map.Entry<String, ToolParam> entry : params.entrySet()) {
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", entry.getValue().type());
            prop.put("description", entry.getValue().description());
            properties.put(entry.getKey(), prop);
            if (entry.getValue().required()) {
               requiredList.add(entry.getKey());
            }
         }
      }

      JsonSchema schema = new JsonSchema("object", properties, requiredList, false, null, null);
      return Tool.builder()
         .name(name)
         .title(name)
         .description(description)
         .inputSchema(schema)
         .build();
   }

   public static String getString(Map<String, Object> args, String key, String defaultValue) {
      if (args == null || !args.containsKey(key) || args.get(key) == null) return defaultValue;
      return String.valueOf(args.get(key));
   }

   public static double getDouble(Map<String, Object> args, String key, double defaultValue) {
      if (args == null || !args.containsKey(key) || args.get(key) == null) return defaultValue;
      Object val = args.get(key);
      if (val instanceof Number n) return n.doubleValue();
      try {
         return Double.parseDouble(String.valueOf(val));
      } catch (Exception e) {
         return defaultValue;
      }
   }

   public static int getInt(Map<String, Object> args, String key, int defaultValue) {
      if (args == null || !args.containsKey(key) || args.get(key) == null) return defaultValue;
      Object val = args.get(key);
      if (val instanceof Number n) return n.intValue();
      try {
         return Integer.parseInt(String.valueOf(val));
      } catch (Exception e) {
         return defaultValue;
      }
   }

   public static boolean getBoolean(Map<String, Object> args, String key, boolean defaultValue) {
      if (args == null || !args.containsKey(key) || args.get(key) == null) return defaultValue;
      Object val = args.get(key);
      if (val instanceof Boolean b) return b;
      return Boolean.parseBoolean(String.valueOf(val));
   }
}
