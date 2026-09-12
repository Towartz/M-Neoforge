package com.cope.meteormcp.ollama;

import com.cope.meteormcp.MeteorMCPAddon;
import com.cope.meteormcp.llm.LLMProvider;
import com.cope.meteormcp.systems.MCPServerConnection;
import com.cope.meteormcp.systems.MCPServers;
import com.cope.meteormcp.systems.OllamaConfig;
import io.github.ollama4j.Ollama;
import io.github.ollama4j.models.chat.OllamaChatMessageRole;
import io.github.ollama4j.models.chat.OllamaChatRequest;
import io.github.ollama4j.models.chat.OllamaChatResponseModel;
import io.github.ollama4j.models.chat.OllamaChatResult;
import io.github.ollama4j.utils.Options;
import io.github.ollama4j.utils.OptionsBuilder;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Content;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

public final class OllamaExecutor {
   private static final ReentrantLock toolLock = new ReentrantLock();
   public static final String SYSTEM_PROMPT =
      "You are an autonomous Minecraft AI agent capable of controlling the player using MCP tools in the 'minecraft' server. " +
      "You can move, pathfind, mine, build, craft items, manage inventory, attack entities, and toggle Meteor modules. " +
      "Analyze the user goal and world/player state, call the appropriate tools step-by-step to achieve the goal, and provide concise status updates. " +
      "Keep responses short and to the point — a few sentences at most. Do not use markdown formatting.";

   private OllamaExecutor() {
   }

   public static String executeSimplePrompt(String prompt) {
      if (prompt != null && !prompt.isBlank()) {
         OllamaClientManager manager = OllamaClientManager.getInstance();
         if (!manager.isConfigured()) {
            return "Warning: Ollama is not configured.";
         } else {
            try {
               OllamaClientManager.ensureMapperCompatibility();
               Ollama api = manager.getClient();
               OllamaConfig config = MCPServers.get().getAIConfig().getOllamaConfig();
               MeteorMCPAddon.LOG
                  .info(
                     "[Ollama] Simple prompt: model={}, numCtx={}, numPredict={}, temp={}, keepAlive={}",
                     new Object[]{config.getModel(), config.getContextLength(), config.getMaxOutputTokens(), config.getTemperature(), config.getKeepAlive()}
                  );
               Options options = new OptionsBuilder()
                  .setTemperature(config.getTemperature())
                  .setNumPredict(config.getMaxOutputTokens())
                  .setNumCtx(config.getContextLength())
                  .build();
               OllamaChatRequest request = OllamaChatRequest.builder()
                  .withModel(config.getModel())
                  .withMessage(
                     OllamaChatMessageRole.SYSTEM,
                     SYSTEM_PROMPT
                  )
                  .withMessage(OllamaChatMessageRole.USER, prompt)
                  .withOptions(options)
                  .withKeepAlive(config.getKeepAlive())
                  .build();
               long start = System.currentTimeMillis();
               OllamaChatResult result = api.chat(request, token -> {
               });
               long elapsed = System.currentTimeMillis() - start;
               String text = result.getResponseModel().getMessage().getResponse();
               logResponseMetrics(result.getResponseModel(), elapsed);
               return text != null && !text.isBlank() ? text.trim() : "Warning: Ollama returned no text.";
            } catch (Exception var12) {
               MeteorMCPAddon.LOG.error("Ollama prompt failed: {}", var12.getMessage());
               return formatError(var12);
            }
         }
      } else {
         return "Warning: prompt is required.";
      }
   }

   public static LLMProvider.MCPResult executeWithMCPToolsDetailed(String prompt, Set<String> serverNames) {
      if (prompt != null && !prompt.isBlank()) {
         OllamaClientManager manager = OllamaClientManager.getInstance();
         if (!manager.isConfigured()) {
            return new LLMProvider.MCPResult("Warning: Ollama is not configured.", List.of());
         } else {
            OllamaConfig config = MCPServers.get().getAIConfig().getOllamaConfig();
            Map<String, MCPServerConnection> connections = new HashMap<>();
            List<LLMProvider.ToolCallInfo> toolHistory = new CopyOnWriteArrayList<>();
            collectConnections(serverNames, connections);
            if (connections.isEmpty()) {
               return new LLMProvider.MCPResult(executeSimplePrompt(prompt), toolHistory);
            } else {
               toolLock.lock();

               LLMProvider.MCPResult options;
               try {
                  OllamaClientManager.ensureMapperCompatibility();
                  Ollama api = manager.getClient();
                  api.deregisterTools();

                  for (Entry<String, MCPServerConnection> entry : connections.entrySet()) {
                     String serverName = entry.getKey();
                     MCPServerConnection connection = entry.getValue();

                     for (Tool tool : connection.getTools()) {
                        try {
                           io.github.ollama4j.tools.Tools.Tool ollamaTool = MCPToOllamaBridge.convertMCPToolToOllama(
                              tool,
                              serverName,
                              args -> {
                                 long startx = System.currentTimeMillis();

                                 try {
                                    Map<String, Object> arguments = (Map<String, Object>)(args != null ? new LinkedHashMap<>(args) : Map.of());
                                    CallToolResult callResult = connection.callTool(tool.name(), arguments);
                                    long duration = System.currentTimeMillis() - startx;
                                    String resultText = flattenContent(callResult);
                                    toolHistory.add(new LLMProvider.ToolCallInfo(serverName, tool.name(), arguments, duration, true, null));
                                    return resultText;
                                 } catch (Exception var12x) {
                                    long durationx = System.currentTimeMillis() - startx;
                                    toolHistory.add(
                                       new LLMProvider.ToolCallInfo(
                                          serverName,
                                          tool.name(),
                                          (Map<String, Object>)(args != null ? new LinkedHashMap<>(args) : Map.of()),
                                          durationx,
                                          false,
                                          var12x.getMessage()
                                       )
                                    );
                                    return "Error: " + var12x.getMessage();
                                 }
                              }
                           );
                           api.registerTool(ollamaTool);
                        } catch (Exception var20) {
                           MeteorMCPAddon.LOG.warn("Failed to register Ollama tool {} from {}: {}", new Object[]{tool.name(), serverName, var20.getMessage()});
                        }
                     }
                  }

                  MeteorMCPAddon.LOG
                     .info(
                        "[Ollama] MCP prompt: model={}, numCtx={}, numPredict={}, temp={}, keepAlive={}, tools={}",
                        new Object[]{
                           config.getModel(),
                           config.getContextLength(),
                           config.getMaxOutputTokens(),
                           config.getTemperature(),
                           config.getKeepAlive(),
                           api.getRegisteredTools().size()
                        }
                     );
                  Options optionsx = new OptionsBuilder()
                     .setTemperature(config.getTemperature())
                     .setNumPredict(config.getMaxOutputTokens())
                     .setNumCtx(config.getContextLength())
                     .build();
                  OllamaChatRequest request = OllamaChatRequest.builder()
                     .withModel(config.getModel())
                     .withMessage(
                        OllamaChatMessageRole.SYSTEM,
                        SYSTEM_PROMPT
                     )
                     .withMessage(OllamaChatMessageRole.USER, prompt)
                     .withOptions(optionsx)
                     .withKeepAlive(config.getKeepAlive())
                     .withUseTools(true)
                     .build();
                  long start = System.currentTimeMillis();
                  OllamaChatResult result = api.chat(request, token -> {
                  });
                  long elapsed = System.currentTimeMillis() - start;
                  String text = result.getResponseModel().getMessage().getResponse();
                  MeteorMCPAddon.LOG.info("[Ollama] MCP toolCalls={}", toolHistory.size());
                  logResponseMetrics(result.getResponseModel(), elapsed);
                  if (text == null || text.isBlank()) {
                     text = "Warning: Ollama returned no text.";
                  }

                  return new LLMProvider.MCPResult(text.trim(), toolHistory);
               } catch (Exception var21) {
                  MeteorMCPAddon.LOG.error("Ollama MCP execution failed: {}", var21.getMessage());
                  options = new LLMProvider.MCPResult(formatError(var21), toolHistory);
               } finally {
                  toolLock.unlock();
               }

               return options;
            }
         }
      } else {
         return new LLMProvider.MCPResult("Warning: prompt is required.", List.of());
      }
   }

   private static void collectConnections(Collection<String> serverNames, Map<String, MCPServerConnection> connections) {
      if (serverNames != null && !serverNames.isEmpty()) {
         for (String name : serverNames) {
            if (name != null) {
               MCPServerConnection conn = MCPServers.get().getConnection(name);
               if (conn != null && conn.isConnected()) {
                  connections.put(name, conn);
               }
            }
         }
      } else {
         for (MCPServerConnection conn : MCPServers.get().getAllConnections()) {
            if (conn != null && conn.isConnected()) {
               connections.put(conn.getConfig().getName(), conn);
            }
         }
      }
   }

   private static String flattenContent(CallToolResult result) {
      if (result == null) {
         return "Tool returned no result.";
      } else {
         List<Content> contents = result.content();
         if (contents != null && !contents.isEmpty()) {
            StringBuilder sb = new StringBuilder();

            for (Content content : contents) {
               if (content instanceof TextContent) {
                  TextContent textContent = (TextContent)content;
                  if (textContent.text() != null) {
                     if (!sb.isEmpty()) {
                        sb.append('\n');
                     }

                     sb.append(textContent.text());
                  }
               }
            }

            return sb.isEmpty() ? "Tool completed without text output." : sb.toString().trim();
         } else {
            return "Tool completed without output.";
         }
      }
   }

   private static void logResponseMetrics(OllamaChatResponseModel response, long wallClockMs) {
      if (response == null) {
         MeteorMCPAddon.LOG.info("[Ollama] Response in {}ms (no metrics)", wallClockMs);
      } else {
         Long loadNs = response.getLoadDuration();
         Long promptNs = response.getPromptEvalDuration();
         Long evalNs = response.getEvalDuration();
         Integer evalCount = response.getEvalCount();
         String text = response.getMessage() != null ? response.getMessage().getResponse() : null;
         int wordCount = text != null && !text.isBlank() ? text.trim().split("\\s+").length : 0;
         MeteorMCPAddon.LOG
            .info(
               "[Ollama] Response in {}ms (~{} words) | load={}ms prompt={}ms eval={}ms tokens={}",
               new Object[]{
                  wallClockMs,
                  wordCount,
                  loadNs != null ? loadNs / 1000000L : "?",
                  promptNs != null ? promptNs / 1000000L : "?",
                  evalNs != null ? evalNs / 1000000L : "?",
                  evalCount != null ? evalCount : "?"
               }
            );
         if (evalCount != null && evalNs != null && evalNs > 0L) {
            double tokensPerSec = (double)evalCount.intValue() / ((double)evalNs.longValue() / 1.0E9);
            MeteorMCPAddon.LOG.info("[Ollama] Generation speed: {} tokens/sec", String.format("%.1f", tokensPerSec));
         }
      }
   }

   private static String formatError(Exception e) {
      String message = e.getMessage();
      if (message == null || message.isBlank()) {
         message = e.getClass().getSimpleName();
      }

      return "Warning: Ollama request failed: " + message;
   }
}
