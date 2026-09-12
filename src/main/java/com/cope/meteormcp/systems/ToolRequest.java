package com.cope.meteormcp.systems;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

record ToolRequest(String toolName, Map<String, Object> arguments, CompletableFuture<CallToolResult> resultFuture) {
   ToolRequest(String toolName, Map<String, Object> arguments, CompletableFuture<CallToolResult> resultFuture) {
      if (toolName == null || toolName.isBlank()) {
         throw new IllegalArgumentException("Tool name cannot be null or blank");
      } else if (resultFuture == null) {
         throw new IllegalArgumentException("Result future cannot be null");
      } else {
         this.toolName = toolName;
         this.arguments = arguments;
         this.resultFuture = resultFuture;
      }
   }
}
