package com.cope.meteormcp.llm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface LLMProvider {
   String name();

   boolean isConfigured();

   String executeSimplePrompt(String var1);

   LLMProvider.MCPResult executeWithMCPTools(String var1, Set<String> var2);

   LLMProvider.TestResult testConnection();

   public static record MCPResult(String response, List<LLMProvider.ToolCallInfo> toolCalls) {
      public MCPResult(String response, List<LLMProvider.ToolCallInfo> toolCalls) {
         this.response = response;
         this.toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
      }
   }

   public static record TestResult(boolean success, String message) {
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
