package com.cope.meteormcp.starscript;

import com.cope.meteormcp.MeteorMCPAddon;
import com.cope.meteormcp.systems.MCPServerConnection;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.starscript.Starscript;
import meteordevelopment.starscript.value.Value;

public class MCPToolExecutor {
   private static final Map<String, MCPAsyncResult> asyncResults = new ConcurrentHashMap<>();

   public static Value execute(Starscript ss, int argCount, MCPServerConnection connection, Tool tool) {
      try {
         if (!connection.isConnected()) {
            MeteorMCPAddon.LOG.warn("Attempted to call tool {} on disconnected server {}", tool.name(), connection.getConfig().getName());
            return Value.string("Error: Server disconnected");
         } else {
            Map<String, Object> args = extractArguments(ss, argCount, tool);
            String cacheKey = generateCacheKey(connection.getConfig().getName(), tool.name(), args);
            MCPAsyncResult asyncResult = asyncResults.computeIfAbsent(cacheKey, k -> new MCPAsyncResult());
            if (asyncResult.tryStartTask()) {
               MeteorExecutor.execute(
                  () -> {
                     try {
                        CallToolResult result = connection.callTool(tool.name(), args);
                        String resultString = MCPValueConverter.toValue(result).toString();
                        asyncResult.setLastResult(resultString);
                     } catch (Exception var9) {
                        MeteorMCPAddon.LOG
                           .error(
                              "Async MCP tool execution failed for {} on {}: {}",
                              new Object[]{tool.name(), connection.getConfig().getName(), var9.getMessage()}
                           );
                        asyncResult.setLastResult("Error: " + var9.getMessage());
                     } finally {
                        asyncResult.completeTask();
                     }
                  }
               );
            }

            return Value.string(asyncResult.getLastResult());
         }
      } catch (Exception var7) {
         MeteorMCPAddon.LOG
            .error("Error executing MCP tool {} on server {}: {}", new Object[]{tool.name(), connection.getConfig().getName(), var7.getMessage()});
         return Value.string("Error: " + var7.getMessage());
      }
   }

   private static String generateCacheKey(String serverName, String toolName, Map<String, Object> args) {
      StringBuilder key = new StringBuilder();
      key.append(serverName).append(".").append(toolName).append("(");
      if (args != null && !args.isEmpty()) {
         List<String> sortedKeys = new ArrayList<>(args.keySet());
         Collections.sort(sortedKeys);

         for (int i = 0; i < sortedKeys.size(); i++) {
            String argKey = sortedKeys.get(i);
            Object argValue = args.get(argKey);
            if (i > 0) {
               key.append(",");
            }

            key.append(argKey).append("=").append(argValue);
         }
      }

      key.append(")");
      return key.toString();
   }

   public static void clearAsyncResults() {
      asyncResults.clear();
   }

   public static void clearAsyncResultsForServer(String serverName) {
      asyncResults.keySet().removeIf(key -> key.startsWith(serverName + "."));
   }

   private static Map<String, Object> extractArguments(Starscript ss, int argCount, Tool tool) {
      List<String> paramNames = getParameterNames(tool);
      return MCPValueConverter.extractArgumentsAsMap(ss, argCount, paramNames);
   }

   public static List<String> getParameterNames(Tool tool) {
      List<String> names = new ArrayList<>();

      try {
         JsonSchema schema = tool.inputSchema();
         Map<String, Object> properties = schema != null ? schema.properties() : null;
         if (properties != null) {
            names.addAll(properties.keySet());
         }
      } catch (Exception var4) {
         MeteorMCPAddon.LOG.warn("Could not extract parameter names from tool {}: {}", tool.name(), var4.getMessage());
      }

      return names;
   }

   public static Value createToolFunction(MCPServerConnection connection, Tool tool) {
      return Value.function((ss, argCount) -> execute(ss, argCount, connection, tool));
   }

   public static String generateExampleSyntax(String serverName, Tool tool) {
      StringBuilder syntax = new StringBuilder();
      syntax.append("{").append(serverName).append(".").append(tool.name()).append("(");
      List<String> paramNames = getParameterNames(tool);

      for (int i = 0; i < paramNames.size(); i++) {
         if (i > 0) {
            syntax.append(", ");
         }

         syntax.append(paramNames.get(i));
      }

      syntax.append(")}");
      return syntax.toString();
   }

   public static List<String> getRequiredParameters(Tool tool) {
      List<String> required = new ArrayList<>();

      try {
         JsonSchema schema = tool.inputSchema();
         List<String> requiredList = schema != null ? schema.required() : null;
         if (requiredList != null) {
            required.addAll(requiredList);
         }
      } catch (Exception var4) {
         MeteorMCPAddon.LOG.warn("Could not extract required parameters from tool {}: {}", tool.name(), var4.getMessage());
      }

      return required;
   }

   public static String getParameterType(Tool tool, String paramName) {
      try {
         JsonSchema schema = tool.inputSchema();
         Map<String, Object> properties = schema != null ? schema.properties() : null;
         if (properties != null && properties.get(paramName) instanceof Map<?, ?> paramSchemaMap) {
            Object type = paramSchemaMap.get("type");
            if (type != null) {
               return type.toString();
            }
         }
      } catch (Exception var7) {
      }

      return "any";
   }
}
