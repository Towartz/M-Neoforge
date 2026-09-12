package com.cope.meteormcp.gemini;

import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Schema;
import com.google.genai.types.FunctionDeclaration.Builder;
import com.google.genai.types.Type.Known;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public final class MCPToGeminiBridge {
   private static final int MAX_FUNCTION_NAME_LENGTH = 64;
   private static final ConcurrentHashMap<String, MCPToGeminiBridge.ToolCallRoute> ROUTING = new ConcurrentHashMap<>();

   private MCPToGeminiBridge() {
   }

   public static FunctionDeclaration convertMCPToolToGemini(Tool mcpTool, String serverName) {
      if (mcpTool != null && serverName != null) {
         String functionName = buildFunctionName(serverName, mcpTool.name());
         Builder builder = FunctionDeclaration.builder().name(functionName);
         String description = composeDescription(mcpTool, serverName);
         if (!description.isBlank()) {
            builder.description(description);
         }

         Schema parameters = convertJsonSchema(mcpTool.inputSchema());
         if (parameters != null) {
            builder.parameters(parameters);
         }

         ROUTING.put(functionName, new MCPToGeminiBridge.ToolCallRoute(serverName, mcpTool.name()));
         return builder.build();
      } else {
         throw new IllegalArgumentException("Tool and server name must be provided.");
      }
   }

   public static MCPToGeminiBridge.ToolCallRoute resolveRoute(String functionName) {
      MCPToGeminiBridge.ToolCallRoute route = ROUTING.get(functionName);
      if (route != null) {
         return route;
      } else {
         String fallback = functionName != null ? functionName : "";
         int idx = fallback.indexOf(95);
         if (idx > 0 && idx != fallback.length() - 1) {
            String server = fallback.substring(0, idx);
            String tool = fallback.substring(idx + 1);
            return new MCPToGeminiBridge.ToolCallRoute(server, tool);
         } else {
            throw new IllegalArgumentException("Cannot resolve Gemini function route: " + functionName);
         }
      }
   }

   private static String buildFunctionName(String serverName, String toolName) {
      String normalizedServer = normalizeSegment(serverName, "server");
      String normalizedTool = normalizeSegment(toolName, "tool");
      String base = normalizedServer + "_" + normalizedTool;
      if (base.length() > 64) {
         base = base.substring(0, 64);
      }

      MCPToGeminiBridge.ToolCallRoute existing = ROUTING.get(base);
      if (existing != null && existing.serverName().equals(serverName) && existing.toolName().equals(toolName)) {
         return base;
      } else {
         String candidate = base;
         int suffix = 1;

         while (ROUTING.containsKey(candidate)) {
            MCPToGeminiBridge.ToolCallRoute route = ROUTING.get(candidate);
            if (route != null && route.serverName().equals(serverName) && route.toolName().equals(toolName)) {
               return candidate;
            }

            String suffixStr = "_" + suffix++;
            int cut = Math.min(candidate.length(), 64 - suffixStr.length());
            candidate = candidate.substring(0, cut) + suffixStr;
         }

         return candidate;
      }
   }

   private static String normalizeSegment(String value, String fallback) {
      if (value == null || value.isBlank()) {
         value = fallback;
      }

      String sanitized = value.trim().replaceAll("[^A-Za-z0-9_\\-\\.]", "_").replaceAll("_+", "_");
      if (sanitized.isEmpty()) {
         sanitized = fallback;
      }

      if (!Character.isLetter(sanitized.charAt(0)) && sanitized.charAt(0) != '_') {
         sanitized = "_" + sanitized;
      }

      if (sanitized.length() > 32) {
         sanitized = sanitized.substring(0, 32);
      }

      return sanitized;
   }

   private static Schema convertJsonSchema(JsonSchema schema) {
      if (schema == null) {
         return Schema.builder().type(Known.OBJECT).build();
      } else {
         Map<String, Object> root = new LinkedHashMap<>();
         if (schema.type() != null) {
            root.put("type", schema.type());
         }

         if (schema.properties() != null) {
            root.put("properties", schema.properties());
         }

         if (schema.required() != null) {
            root.put("required", schema.required());
         }

         if (schema.additionalProperties() != null) {
            root.put("additionalProperties", schema.additionalProperties());
         }

         if (schema.defs() != null) {
            root.put("$defs", schema.defs());
         }

         if (schema.definitions() != null) {
            root.put("definitions", schema.definitions());
         }

         return convertSchemaObject(root, true);
      }
   }

   private static Schema convertSchemaObject(Object raw, boolean forceObject) {
      com.google.genai.types.Schema.Builder builder = Schema.builder();
      boolean typeSet = false;
      if (raw instanceof Map<?, ?> map) {
         if (map.get("type") instanceof String typeStr && !typeStr.isBlank()) {
            builder.type(typeStr);
            typeSet = true;
         }

         if (map.get("description") instanceof String desc && !desc.isBlank()) {
            builder.description(desc);
         }

         if (map.get("title") instanceof String titleStr && !titleStr.isBlank()) {
            builder.title(titleStr);
         }

         if (map.get("format") instanceof String fmt && !fmt.isBlank()) {
            builder.format(fmt);
         }

         Object defaultValue = map.get("default");
         if (defaultValue != null) {
            builder.default_(defaultValue);
         }

         Object example = map.get("example");
         if (example != null) {
            builder.example(example);
         }

         if (map.get("enum") instanceof List<?> list && !list.isEmpty()) {
            builder.enum_(list.stream().filter(Objects::nonNull).map(Object::toString).toList());
         }

         if (map.get("properties") instanceof Map<?, ?> props && !props.isEmpty()) {
            Map<String, Schema> childProps = new LinkedHashMap<>();

            for (Entry<?, ?> entry : props.entrySet()) {
               if (entry.getKey() != null) {
                  Schema child = convertSchemaObject(entry.getValue(), false);
                  childProps.put(entry.getKey().toString(), child);
               }
            }

            builder.properties(childProps);
            if (!typeSet) {
               builder.type(Known.OBJECT);
               typeSet = true;
            }
         }

         if (map.get("required") instanceof List<?> requiredList && !requiredList.isEmpty()) {
            List<String> names = requiredList.stream().filter(Objects::nonNull).map(Object::toString).toList();
            builder.required(names);
         }

         if (map.get("anyOf") instanceof List<?> anyOfList && !anyOfList.isEmpty()) {
            builder.anyOf(convertSchemaList(anyOfList));
         }

         Object items = map.get("items");
         if (items != null) {
            builder.items(convertSchemaObject(items, false));
            if (!typeSet) {
               builder.type(Known.ARRAY);
               typeSet = true;
            }
         }

         Double minimum = asDouble(map.get("minimum"));
         if (minimum != null) {
            builder.minimum(minimum);
         }

         Double maximum = asDouble(map.get("maximum"));
         if (maximum != null) {
            builder.maximum(maximum);
         }

         Long minItems = asLong(map.get("minItems"));
         if (minItems != null) {
            builder.minItems(minItems);
         }

         Long maxItems = asLong(map.get("maxItems"));
         if (maxItems != null) {
            builder.maxItems(maxItems);
         }

         Long minLength = asLong(map.get("minLength"));
         if (minLength != null) {
            builder.minLength(minLength);
         }

         Long maxLength = asLong(map.get("maxLength"));
         if (maxLength != null) {
            builder.maxLength(maxLength);
         }

         Long minProperties = asLong(map.get("minProperties"));
         if (minProperties != null) {
            builder.minProperties(minProperties);
         }

         Long maxProperties = asLong(map.get("maxProperties"));
         if (maxProperties != null) {
            builder.maxProperties(maxProperties);
         }

         if (map.get("nullable") instanceof Boolean bool) {
            builder.nullable(bool);
         }

         if (map.get("propertyOrdering") instanceof List<?> ordering && !ordering.isEmpty()) {
            List<String> order = ordering.stream().filter(Objects::nonNull).map(Object::toString).toList();
            builder.propertyOrdering(order);
         }
      } else if (raw instanceof JsonSchema nested) {
         return convertJsonSchema(nested);
      }

      if (!typeSet && forceObject) {
         builder.type(Known.OBJECT);
      } else if (!typeSet) {
         builder.type(Known.STRING);
      }

      return builder.build();
   }

   private static List<Schema> convertSchemaList(List<?> list) {
      List<Schema> schemas = new ArrayList<>();

      for (Object entry : list) {
         schemas.add(convertSchemaObject(entry, false));
      }

      return schemas;
   }

   private static Double asDouble(Object value) {
      if (value instanceof Number number) {
         return number.doubleValue();
      } else {
         if (value instanceof String str && !str.isBlank()) {
            try {
               return Double.parseDouble(str);
            } catch (NumberFormatException var3) {
            }
         }

         return null;
      }
   }

   private static Long asLong(Object value) {
      if (value instanceof Number number) {
         return number.longValue();
      } else {
         if (value instanceof String str && !str.isBlank()) {
            try {
               return Long.parseLong(str);
            } catch (NumberFormatException var3) {
            }
         }

         return null;
      }
   }

   private static String composeDescription(Tool tool, String serverName) {
      StringBuilder sb = new StringBuilder();
      if (tool.description() != null && !tool.description().isBlank()) {
         sb.append(tool.description().trim());
      } else if (tool.title() != null && !tool.title().isBlank()) {
         sb.append(tool.title().trim());
      }

      if (sb.length() == 0) {
         sb.append("Tool ").append(tool.name());
      }

      sb.append(" (server: ").append(serverName).append(")");
      return sb.toString();
   }

   public static final class ToolCallRoute {
      private final String serverName;
      private final String toolName;

      public ToolCallRoute(String serverName, String toolName) {
         this.serverName = serverName;
         this.toolName = toolName;
      }

      public String serverName() {
         return this.serverName;
      }

      public String toolName() {
         return this.toolName;
      }
   }
}
