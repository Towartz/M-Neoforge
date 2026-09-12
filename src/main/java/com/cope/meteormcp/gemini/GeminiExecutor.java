package com.cope.meteormcp.gemini;

import com.cope.meteormcp.MeteorMCPAddon;
import com.cope.meteormcp.systems.GeminiConfig;
import com.cope.meteormcp.systems.MCPServerConnection;
import com.cope.meteormcp.systems.MCPServers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.UnmodifiableIterator;
import com.google.genai.Client;
import com.google.genai.types.AutomaticFunctionCallingConfig;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.FunctionResponsePart;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Tool;
import com.google.genai.types.GenerateContentConfig.Builder;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class GeminiExecutor {
   private static final int MAX_TOOL_CALL_ITERATIONS = 16;
   public static final String MINECRAFT_AGENT_SYSTEM_PROMPT =
      "You are an autonomous Minecraft AI agent capable of controlling the player using MCP tools in the 'minecraft' server. " +
      "You can move, pathfind, mine, build, craft items, manage inventory, attack entities, and toggle Meteor modules. " +
      "Analyze the user goal and world/player state, call the appropriate tools step-by-step to achieve the goal, and provide concise status updates. " +
      "Keep responses short and to the point. When the goal is completed, output a summary.";

   private GeminiExecutor() {
   }

   public static String executeSimplePrompt(String prompt) {
      if (prompt != null && !prompt.isBlank()) {
         GeminiClientManager manager = GeminiClientManager.getInstance();
         if (!manager.isConfigured()) {
            return "Warning: Gemini is not configured.";
         } else {
            try {
               Client client = manager.getClient();
               GeminiConfig config = MCPServers.get().getGeminiConfig();
               GenerateContentConfig request = GeminiClientManager.createBaseRequestConfig(config).build();
               GenerateContentResponse response = client.models.generateContent(config.getModelId(), prompt, request);
               return extractResponseText(response);
            } catch (Exception var6) {
               MeteorMCPAddon.LOG.error("Gemini prompt failed: {}", var6.getMessage());
               return formatError(var6);
            }
         }
      } else {
         return "Warning: prompt is required.";
      }
   }

   public static String executeWithMCPTools(String prompt, Set<String> serverNames) {
      return executeWithMCPToolsDetailed(prompt, serverNames).response();
   }

   public static GeminiExecutor.GeminiMCPResult executeWithMCPToolsDetailed(String prompt, Set<String> serverNames) {
      if (prompt != null && !prompt.isBlank()) {
         GeminiClientManager manager = GeminiClientManager.getInstance();
         if (!manager.isConfigured()) {
            return new GeminiExecutor.GeminiMCPResult("Warning: Gemini is not configured.", List.of());
         } else {
            GeminiConfig config = MCPServers.get().getGeminiConfig();
            Map<String, MCPServerConnection> connections = new HashMap<>();
            List<FunctionDeclaration> functionDeclarations = new ArrayList<>();
            List<GeminiExecutor.ToolCallInfo> toolHistory = new ArrayList<>();
            collectTools(serverNames, connections, functionDeclarations);
            if (functionDeclarations.isEmpty()) {
               return new GeminiExecutor.GeminiMCPResult(executeSimplePrompt(prompt), toolHistory);
            } else {
               try {
                  Client client = manager.getClient();
                  Builder requestBuilder = GeminiClientManager.createBaseRequestConfig(config)
                     .systemInstruction(Content.fromParts(new Part[]{Part.fromText(MINECRAFT_AGENT_SYSTEM_PROMPT)}))
                     .tools(List.of(Tool.builder().functionDeclarations(functionDeclarations).build()))
                     .automaticFunctionCalling(AutomaticFunctionCallingConfig.builder().disable(true).build());
                  GenerateContentConfig request = requestBuilder.build();
                  List<Content> history = new ArrayList<>();
                  history.add(Content.fromParts(new Part[]{Part.fromText(prompt)}));
                  int iteration = 0;

                  label82:
                  while (iteration < MAX_TOOL_CALL_ITERATIONS) {
                     GenerateContentResponse response = client.models.generateContent(config.getModelId(), history, request);
                     if (response == null) {
                        return new GeminiExecutor.GeminiMCPResult("Warning: Gemini returned no response.", toolHistory);
                     }

                     List<FunctionCall> functionCalls = response.functionCalls();
                     if (functionCalls == null || functionCalls.isEmpty()) {
                        return new GeminiExecutor.GeminiMCPResult(extractResponseText(response), toolHistory);
                     }

                     response.candidates().ifPresent(candidates -> {
                        if (!candidates.isEmpty()) {
                           ((Candidate)candidates.get(0)).content().ifPresent(history::add);
                        }
                     });
                     boolean executed = false;
                     Iterator var15 = functionCalls.iterator();

                     while (true) {
                        FunctionCall call;
                        String callName;
                        MCPToGeminiBridge.ToolCallRoute route;
                        while (true) {
                           if (!var15.hasNext()) {
                              if (!executed) {
                                 return new GeminiExecutor.GeminiMCPResult("Warning: Gemini could not execute any MCP tools.", toolHistory);
                              }

                              iteration++;
                              continue label82;
                           }

                           call = (FunctionCall)var15.next();
                           callName = call.name().orElse("");
                           if (!callName.isEmpty()) {
                              try {
                                 route = MCPToGeminiBridge.resolveRoute(callName);
                                 break;
                              } catch (IllegalArgumentException var28) {
                                 MeteorMCPAddon.LOG.warn("Gemini requested unknown function '{}'", callName);
                                 history.add(buildFunctionErrorResponse(callName, "Unknown function requested: " + callName));
                                 toolHistory.add(
                                    new GeminiExecutor.ToolCallInfo("unknown", callName, Map.of(), 0L, false, "Unknown function requested: " + callName)
                                 );
                              }
                           }
                        }

                        MCPServerConnection connection = connections.get(route.serverName());
                        if (connection != null && connection.isConnected()) {
                           Map<String, Object> arguments = safeArguments(call.args().orElse(Collections.emptyMap()));
                           long start = System.currentTimeMillis();
                           Map<String, Object> toolResult = executeMCPTool(connection, route.toolName(), arguments);
                           long duration = System.currentTimeMillis() - start;
                           history.add(Content.builder().parts(List.of(Part.fromFunctionResponse(callName, toolResult, new FunctionResponsePart[0]))).build());
                           boolean success = !Boolean.TRUE.equals(toolResult.get("error"));
                           String errorMessage = success ? null : Objects.toString(toolResult.get("message"), "Tool execution failed.");
                           toolHistory.add(new GeminiExecutor.ToolCallInfo(route.serverName(), route.toolName(), arguments, duration, success, errorMessage));
                           executed = true;
                        } else {
                           String errorMessage = "Server '" + route.serverName() + "' is not connected.";
                           history.add(buildFunctionErrorResponse(callName, errorMessage));
                           toolHistory.add(new GeminiExecutor.ToolCallInfo(route.serverName(), route.toolName(), Map.of(), 0L, false, errorMessage));
                        }
                     }
                  }

                  return new GeminiExecutor.GeminiMCPResult("Warning: Gemini did not finish after multiple tool calls.", toolHistory);
               } catch (Exception var29) {
                  MeteorMCPAddon.LOG.error("Gemini MCP execution failed: {}", var29.getMessage());
                  return new GeminiExecutor.GeminiMCPResult(formatError(var29), List.of());
               }
            }
         }
      } else {
         return new GeminiExecutor.GeminiMCPResult("Warning: prompt is required.", List.of());
      }
   }

   private static void collectTools(Collection<String> serverNames, Map<String, MCPServerConnection> connections, List<FunctionDeclaration> declarations) {
      if (serverNames != null && !serverNames.isEmpty()) {
         for (String serverName : serverNames) {
            if (serverName != null) {
               MCPServerConnection connection = MCPServers.get().getConnection(serverName);
               addToolsForConnection(connection, connections, declarations);
            }
         }
      } else {
         for (MCPServerConnection connection : MCPServers.get().getAllConnections()) {
            addToolsForConnection(connection, connections, declarations);
         }
      }
   }

   private static void addToolsForConnection(
      MCPServerConnection connection, Map<String, MCPServerConnection> connections, List<FunctionDeclaration> declarations
   ) {
      if (connection != null && connection.isConnected()) {
         connections.put(connection.getConfig().getName(), connection);

         for (io.modelcontextprotocol.spec.McpSchema.Tool tool : connection.getTools()) {
            try {
               declarations.add(MCPToGeminiBridge.convertMCPToolToGemini(tool, connection.getConfig().getName()));
            } catch (Exception var6) {
               MeteorMCPAddon.LOG
                  .warn("Failed to convert MCP tool {} from {}: {}", new Object[]{tool.name(), connection.getConfig().getName(), var6.getMessage()});
            }
         }
      }
   }

   private static Map<String, Object> executeMCPTool(MCPServerConnection connection, String toolName, Map<String, Object> arguments) {
      try {
         CallToolResult result = connection.callTool(toolName, arguments);
         return formatToolResult(result);
      } catch (Exception var4) {
         MeteorMCPAddon.LOG.error("Error executing tool {} on {}: {}", new Object[]{toolName, connection.getConfig().getName(), var4.getMessage()});
         return Map.of("error", true, "message", var4.getMessage() != null ? var4.getMessage() : "Tool execution failed.");
      }
   }

   private static Map<String, Object> formatToolResult(CallToolResult result) {
      Map<String, Object> payload = new LinkedHashMap<>();
      if (result == null) {
         payload.put("message", "Tool returned no result.");
         return payload;
      } else {
         if (Boolean.TRUE.equals(result.isError())) {
            payload.put("error", true);
         }

         if (result.structuredContent() != null) {
            payload.put("structuredContent", result.structuredContent());
         }

         String contentText = flattenContent(result.content());
         if (!contentText.isBlank()) {
            payload.put("content", contentText);
         }

         if (result.meta() != null && !result.meta().isEmpty()) {
            payload.put("meta", result.meta());
         }

         if (payload.isEmpty()) {
            payload.put("message", "Tool completed without returning data.");
         }

         return payload;
      }
   }

   private static String flattenContent(List<io.modelcontextprotocol.spec.McpSchema.Content> contents) {
      if (contents != null && !contents.isEmpty()) {
         StringBuilder sb = new StringBuilder();

         for (io.modelcontextprotocol.spec.McpSchema.Content content : contents) {
            if (content instanceof TextContent textContent && textContent.text() != null) {
               if (sb.length() > 0) {
                  sb.append('\n');
               }

               sb.append(textContent.text());
               continue;
            }

            if (sb.length() > 0) {
               sb.append('\n');
            }

            sb.append(content.type());
         }

         return sb.toString().trim();
      } else {
         return "";
      }
   }

   private static Map<String, Object> safeArguments(Map<String, Object> args) {
      Map<String, Object> sanitized = new LinkedHashMap<>();
      if (args == null) {
         return sanitized;
      } else {
         args.forEach((key, value) -> {
            if (key != null) {
               sanitized.put(key, value);
            }
         });
         return sanitized;
      }
   }

   private static Content buildFunctionErrorResponse(String functionName, String message) {
      Map<String, Object> errorPayload = Map.of("error", true, "message", message);
      return Content.builder().parts(List.of(Part.fromFunctionResponse(functionName, errorPayload, new FunctionResponsePart[0]))).build();
   }

   private static String extractResponseText(GenerateContentResponse response) {
      if (response == null) {
         return "Warning: Gemini returned no response.";
      } else {
         String text = response.text();
         if (text != null && !text.isBlank()) {
            return text.trim();
         } else {
            ImmutableList<Part> parts = response.parts();
            if (parts != null && !parts.isEmpty()) {
               StringBuilder sb = new StringBuilder();
               UnmodifiableIterator var4 = parts.iterator();

               while (var4.hasNext()) {
                  Part part = (Part)var4.next();
                  part.text().ifPresent(value -> {
                     if (!value.isBlank()) {
                        if (sb.length() > 0) {
                           sb.append('\n');
                        }

                        sb.append(value.trim());
                     }
                  });
               }

               if (sb.length() > 0) {
                  return sb.toString();
               }
            }

            return "Warning: Gemini returned no text.";
         }
      }
   }

   private static String formatError(Exception e) {
      String message = e.getMessage();
      if (message == null || message.isBlank()) {
         message = e.getClass().getSimpleName();
      }

      return "Warning: Gemini request failed: " + message;
   }

   public static record GeminiMCPResult(String response, List<GeminiExecutor.ToolCallInfo> toolCalls) {
      public GeminiMCPResult(String response, List<GeminiExecutor.ToolCallInfo> toolCalls) {
         this.response = response;
         this.toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
      }
   }

   public static record ToolCallInfo(String serverName, String toolName, Map<String, Object> arguments, long durationMs, boolean success, String errorMessage) {
      public ToolCallInfo(String serverName, String toolName, Map<String, Object> arguments, long durationMs, boolean success, String errorMessage) {
         arguments = arguments == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
         this.serverName = serverName;
         this.toolName = toolName;
         this.arguments = arguments;
         this.durationMs = durationMs;
         this.success = success;
         this.errorMessage = errorMessage;
      }
   }
}
