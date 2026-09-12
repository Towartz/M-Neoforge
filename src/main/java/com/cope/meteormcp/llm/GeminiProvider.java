package com.cope.meteormcp.llm;

import com.cope.meteormcp.gemini.AgyClientManager;
import com.cope.meteormcp.gemini.AgyExecutor;
import com.cope.meteormcp.gemini.GeminiClientManager;
import com.cope.meteormcp.gemini.GeminiExecutor;
import com.cope.meteormcp.systems.GeminiConfig;
import com.cope.meteormcp.systems.MCPServers;
import java.util.List;
import java.util.Set;

public final class GeminiProvider implements LLMProvider {
   @Override
   public String name() {
      GeminiConfig config = MCPServers.get().getGeminiConfig();
      if (config != null && config.getAuthType() == GeminiConfig.AuthType.AGY_CLI) {
         return "Gemini (AGY CLI)";
      }
      return "Gemini (API Key)";
   }

   @Override
   public boolean isConfigured() {
      GeminiConfig config = MCPServers.get().getGeminiConfig();
      return config != null && config.isValid();
   }

   @Override
   public String executeSimplePrompt(String prompt) {
      GeminiConfig config = MCPServers.get().getGeminiConfig();
      if (config != null && config.getAuthType() == GeminiConfig.AuthType.AGY_CLI) {
         return AgyExecutor.executeSimplePrompt(prompt);
      }
      return GeminiExecutor.executeSimplePrompt(prompt);
   }

   @Override
   public LLMProvider.MCPResult executeWithMCPTools(String prompt, Set<String> serverNames) {
      GeminiConfig config = MCPServers.get().getGeminiConfig();
      GeminiExecutor.GeminiMCPResult geminiResult;
      if (config != null && config.getAuthType() == GeminiConfig.AuthType.AGY_CLI) {
         geminiResult = AgyExecutor.executeWithMCPToolsDetailed(prompt, serverNames);
      } else {
         geminiResult = GeminiExecutor.executeWithMCPToolsDetailed(prompt, serverNames);
      }

      List<LLMProvider.ToolCallInfo> toolCalls = geminiResult.toolCalls()
         .stream()
         .map(tc -> new LLMProvider.ToolCallInfo(tc.serverName(), tc.toolName(), tc.arguments(), tc.durationMs(), tc.success(), tc.errorMessage()))
         .toList();
      return new LLMProvider.MCPResult(geminiResult.response(), toolCalls);
   }

   @Override
   public LLMProvider.TestResult testConnection() {
      GeminiConfig config = MCPServers.get().getGeminiConfig();
      if (config != null && config.getAuthType() == GeminiConfig.AuthType.AGY_CLI) {
         AgyClientManager.TestResult result = AgyClientManager.getInstance().testConfiguration(config);
         return new LLMProvider.TestResult(result.success(), result.message());
      } else {
         GeminiClientManager.TestResult result = GeminiClientManager.getInstance().testConfiguration(config);
         return new LLMProvider.TestResult(result.success(), result.message());
      }
   }
}
