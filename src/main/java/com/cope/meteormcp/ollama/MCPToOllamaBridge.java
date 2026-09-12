package com.cope.meteormcp.ollama;

import io.github.ollama4j.tools.ToolFunction;
import io.github.ollama4j.tools.Tools.Parameters;
import io.github.ollama4j.tools.Tools.Property;
import io.github.ollama4j.tools.Tools.Tool;
import io.github.ollama4j.tools.Tools.ToolSpec;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public final class MCPToOllamaBridge {
   private static final ConcurrentHashMap<String, MCPToOllamaBridge.ToolCallRoute> ROUTING = new ConcurrentHashMap<>();

   private MCPToOllamaBridge() {
   }

   public static Tool convertMCPToolToOllama(io.modelcontextprotocol.spec.McpSchema.Tool mcpTool, String serverName, ToolFunction function) {
      if (mcpTool != null && serverName != null) {
         String functionName = buildFunctionName(serverName, mcpTool.name());
         String description = composeDescription(mcpTool, serverName);
         Parameters parameters = convertParameters(mcpTool.inputSchema());
         ToolSpec spec = ToolSpec.builder().name(functionName).description(description).parameters(parameters).build();
         ROUTING.put(functionName, new MCPToOllamaBridge.ToolCallRoute(serverName, mcpTool.name()));
         return Tool.builder().toolSpec(spec).toolFunction(function).build();
      } else {
         throw new IllegalArgumentException("Tool and server name must be provided.");
      }
   }

   public static MCPToOllamaBridge.ToolCallRoute resolveRoute(String functionName) {
      MCPToOllamaBridge.ToolCallRoute route = ROUTING.get(functionName);
      if (route != null) {
         return route;
      } else {
         throw new IllegalArgumentException("Cannot resolve Ollama function route: " + functionName);
      }
   }

   private static Parameters convertParameters(JsonSchema schema) {
      if (schema != null && schema.properties() != null && !schema.properties().isEmpty()) {
         Set<String> required = (Set<String>)(schema.required() != null ? new HashSet<>(schema.required()) : Collections.emptySet());
         Map<String, Property> properties = new LinkedHashMap<>();

         for (Entry<String, Object> entry : schema.properties().entrySet()) {
            String propName = entry.getKey();
            String propType = extractType(entry.getValue());
            String propDesc = extractDescription(entry.getValue());
            boolean isRequired = required.contains(propName);
            Property property = Property.builder()
               .type(propType.isEmpty() ? "string" : propType)
               .description(propDesc.isEmpty() ? propName : propDesc)
               .required(isRequired)
               .build();
            properties.put(propName, property);
         }

         List<String> requiredList = new ArrayList<>(required);
         return new Parameters(properties, requiredList);
      } else {
         return new Parameters();
      }
   }

   private static String buildFunctionName(String serverName, String toolName) {
      String normalized = normalizeSegment(serverName) + "_" + normalizeSegment(toolName);
      if (normalized.length() > 64) {
         normalized = normalized.substring(0, 64);
      }

      return normalized;
   }

   private static String normalizeSegment(String value) {
      return value != null && !value.isBlank() ? value.trim().replaceAll("[^A-Za-z0-9_]", "_").replaceAll("_+", "_") : "unknown";
   }

   private static String extractType(Object schema) {
      if (schema instanceof Map<?, ?> map) {
         Object type = map.get("type");
         if (type != null) {
            return type.toString();
         }
      }

      return "string";
   }

   private static String extractDescription(Object schema) {
      if (schema instanceof Map<?, ?> map) {
         Object desc = map.get("description");
         if (desc != null) {
            return desc.toString();
         }
      }

      return "";
   }

   private static String composeDescription(io.modelcontextprotocol.spec.McpSchema.Tool tool, String serverName) {
      StringBuilder sb = new StringBuilder();
      if (tool.description() != null && !tool.description().isBlank()) {
         sb.append(tool.description().trim());
      } else if (tool.title() != null && !tool.title().isBlank()) {
         sb.append(tool.title().trim());
      } else {
         sb.append("Tool ").append(tool.name());
      }

      sb.append(" (server: ").append(serverName).append(")");
      return sb.toString();
   }

   public static record ToolCallRoute(String serverName, String toolName) {
   }
}
