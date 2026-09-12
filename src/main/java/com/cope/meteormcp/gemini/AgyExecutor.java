package com.cope.meteormcp.gemini;

import com.cope.meteormcp.MeteorMCPAddon;
import com.cope.meteormcp.systems.GeminiConfig;
import com.cope.meteormcp.systems.MCPServerConnection;
import com.cope.meteormcp.systems.MCPServers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class AgyExecutor {
   private static final ObjectMapper MAPPER = new ObjectMapper();
   private static final int MAX_TOOL_CALL_ITERATIONS = 10;
   private static final int PROCESS_TIMEOUT_SECONDS = 40;

   private AgyExecutor() {
   }

   public static String executeSimplePrompt(String prompt) {
      if (prompt == null || prompt.isBlank()) {
         return "Warning: prompt is required.";
      }

      GeminiConfig config = MCPServers.get().getGeminiConfig();
      String agyPath = AgyClientManager.getInstance().getResolvedAgyPath(config);
      String model = (config != null && config.getAgyModel() != null && !config.getAgyModel().isBlank())
         ? config.getAgyModel()
         : "gemini-3.8-flash-high";

      try {
         return runAgyProcess(agyPath, model, prompt);
      } catch (Exception e) {
         MeteorMCPAddon.LOG.error("AGY CLI execution failed: {}", e.getMessage());
         return "Warning: AGY CLI request failed: " + e.getMessage();
      }
   }

   public static GeminiExecutor.GeminiMCPResult executeWithMCPToolsDetailed(String prompt, Set<String> serverNames) {
      if (prompt == null || prompt.isBlank()) {
         return new GeminiExecutor.GeminiMCPResult("Warning: prompt is required.", List.of());
      }

      GeminiConfig config = MCPServers.get().getGeminiConfig();
      String agyPath = AgyClientManager.getInstance().getResolvedAgyPath(config);
      String model = (config != null && config.getAgyModel() != null && !config.getAgyModel().isBlank())
         ? config.getAgyModel()
         : "gemini-3.8-flash-high";

      Map<String, MCPServerConnection> connections = new HashMap<>();
      Map<String, Tool> availableTools = new LinkedHashMap<>();
      Map<String, String> toolToServer = new HashMap<>();

      collectTools(serverNames, connections, availableTools, toolToServer);

      if (availableTools.isEmpty()) {
         return new GeminiExecutor.GeminiMCPResult(executeSimplePrompt(prompt), List.of());
      }

      List<GeminiExecutor.ToolCallInfo> toolHistory = new ArrayList<>();
      StringBuilder toolDescriptions = new StringBuilder();
      for (Map.Entry<String, Tool> entry : availableTools.entrySet()) {
         Tool tool = entry.getValue();
         toolDescriptions.append("- ").append(tool.name()).append(": ")
            .append(tool.description() != null ? tool.description() : "No description")
            .append("\n");
      }

      StringBuilder conversation = new StringBuilder();
      conversation.append("You are an autonomous Minecraft AI agent controlling the player via tools.\n");
      conversation.append("Available tools:\n").append(toolDescriptions).append("\n");
      conversation.append("Instructions:\n");
      conversation.append("1. If you need to perform an in-game action, respond ONLY with a JSON object: {\"tool\": \"<tool_name>\", \"arguments\": {<args>}}\n");
      conversation.append("2. When you have finished the task or if no tool is needed, respond with: {\"done\": true, \"message\": \"<your response>\"}\n\n");
      conversation.append("User Request: ").append(prompt).append("\n");

      int iteration = 0;
      String lastResponse = "";

      while (iteration < MAX_TOOL_CALL_ITERATIONS) {
         iteration++;
         String output;
         try {
            output = runAgyProcess(agyPath, model, conversation.toString());
         } catch (Exception e) {
            MeteorMCPAddon.LOG.error("AGY tool loop iteration {} failed: {}", iteration, e.getMessage());
            return new GeminiExecutor.GeminiMCPResult("Warning: AGY execution failed: " + e.getMessage(), toolHistory);
         }

         output = output.trim();
         lastResponse = output;

         // Try to parse JSON action from output
         ParsedAction action = parseAction(output);
         if (action == null || action.isDone || action.toolName == null || action.toolName.isBlank()) {
            String message = (action != null && action.message != null && !action.message.isBlank()) ? action.message : output;
            return new GeminiExecutor.GeminiMCPResult(message, toolHistory);
         }

         String toolName = action.toolName;
         Map<String, Object> arguments = action.arguments != null ? action.arguments : Map.of();
         String serverName = toolToServer.getOrDefault(toolName, "minecraft");
         MCPServerConnection connection = connections.get(serverName);

         if (connection != null && connection.isConnected()) {
            long start = System.currentTimeMillis();
            boolean success = false;
            String errorMessage = null;
            String resultText;

            try {
               CallToolResult res = connection.callTool(toolName, arguments);
               long duration = System.currentTimeMillis() - start;
               success = !Boolean.TRUE.equals(res.isError());
               if (!success) {
                  errorMessage = "Tool returned error";
               }
               resultText = extractToolResultText(res);
               toolHistory.add(new GeminiExecutor.ToolCallInfo(serverName, toolName, arguments, duration, success, errorMessage));
            } catch (Exception e) {
               long duration = System.currentTimeMillis() - start;
               errorMessage = e.getMessage();
               resultText = "Error: " + e.getMessage();
               toolHistory.add(new GeminiExecutor.ToolCallInfo(serverName, toolName, arguments, duration, false, errorMessage));
            }

            conversation.append("\nAgent selected tool: ").append(toolName).append(" with args: ").append(arguments).append("\n");
            conversation.append("Tool execution result: ").append(resultText).append("\n");
            conversation.append("Next step: proceed with the next action or output {\"done\": true, \"message\": \"...\"} if finished.\n");
         } else {
            conversation.append("\nTool '").append(toolName).append("' not found or server not connected.\n");
         }
      }

      return new GeminiExecutor.GeminiMCPResult(lastResponse, toolHistory);
   }

   private static String runAgyProcess(String agyPath, String model, String prompt) throws Exception {
      ProcessBuilder pb = new ProcessBuilder(
         agyPath,
         "-p", prompt,
         "--model", model,
         "--disable-slash-commands"
      );
      pb.redirectErrorStream(true);
      Process process = pb.start();

      StringBuilder sb = new StringBuilder();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
         String line;
         while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
         }
      }

      boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      if (!finished) {
         process.destroyForcibly();
         throw new IllegalStateException("Antigravity CLI timed out after " + PROCESS_TIMEOUT_SECONDS + " seconds.");
      }

      if (process.exitValue() != 0) {
         throw new IllegalStateException("AGY CLI exited with code " + process.exitValue() + ": " + sb.toString().trim());
      }

      return sb.toString().trim();
   }

   private static ParsedAction parseAction(String text) {
      if (text == null || text.isBlank()) return null;

      String jsonCandidate = text.trim();
      // If wrapped in ```json ... ```
      if (jsonCandidate.contains("```")) {
         int start = jsonCandidate.indexOf("```");
         int nextLine = jsonCandidate.indexOf('\n', start);
         int end = jsonCandidate.lastIndexOf("```");
         if (nextLine != -1 && end > nextLine) {
            jsonCandidate = jsonCandidate.substring(nextLine + 1, end).trim();
         }
      }

      // If not starting with {, try finding first { and last }
      if (!jsonCandidate.startsWith("{")) {
         int firstBrace = jsonCandidate.indexOf('{');
         int lastBrace = jsonCandidate.lastIndexOf('}');
         if (firstBrace != -1 && lastBrace > firstBrace) {
            jsonCandidate = jsonCandidate.substring(firstBrace, lastBrace + 1).trim();
         }
      }

      try {
         JsonNode node = MAPPER.readTree(jsonCandidate);
         if (node.isObject()) {
            ParsedAction action = new ParsedAction();
            if (node.has("done") && node.get("done").asBoolean()) {
               action.isDone = true;
               action.message = node.has("message") ? node.get("message").asText() : "";
               return action;
            }
            if (node.has("tool")) {
               action.toolName = node.get("tool").asText();
               if (node.has("arguments") && node.get("arguments").isObject()) {
                  action.arguments = MAPPER.convertValue(node.get("arguments"), new TypeReference<Map<String, Object>>() {});
               }
               return action;
            }
            if (node.has("message") && !node.has("tool")) {
               action.isDone = true;
               action.message = node.get("message").asText();
               return action;
            }
         }
      } catch (Exception ignored) {
      }

      return null;
   }

   private static String extractToolResultText(CallToolResult result) {
      if (result == null || result.content() == null || result.content().isEmpty()) {
         return "Success";
      }
      StringBuilder sb = new StringBuilder();
      for (io.modelcontextprotocol.spec.McpSchema.Content c : result.content()) {
         if (c instanceof io.modelcontextprotocol.spec.McpSchema.TextContent tc) {
            sb.append(tc.text()).append(" ");
         }
      }
      String out = sb.toString().trim();
      return out.isEmpty() ? "Success" : out;
   }

   private static void collectTools(
      Collection<String> serverNames,
      Map<String, MCPServerConnection> connections,
      Map<String, Tool> availableTools,
      Map<String, String> toolToServer
   ) {
      Collection<MCPServerConnection> targetConns;
      if (serverNames != null && !serverNames.isEmpty()) {
         targetConns = new ArrayList<>();
         for (String name : serverNames) {
            MCPServerConnection c = MCPServers.get().getConnection(name);
            if (c != null) targetConns.add(c);
         }
      } else {
         targetConns = MCPServers.get().getAllConnections();
      }

      for (MCPServerConnection conn : targetConns) {
         if (conn != null && conn.isConnected()) {
            String serverName = conn.getConfig().getName();
            connections.put(serverName, conn);
            for (Tool t : conn.getTools()) {
               availableTools.put(t.name(), t);
               toolToServer.put(t.name(), serverName);
            }
         }
      }
   }

   private static class ParsedAction {
      boolean isDone = false;
      String message = "";
      String toolName;
      Map<String, Object> arguments = new HashMap<>();
   }
}
