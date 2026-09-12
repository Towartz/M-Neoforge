package com.cope.meteormcp.llm;

import com.cope.meteormcp.ollama.OllamaClientManager;
import com.cope.meteormcp.systems.AIConfig;
import com.cope.meteormcp.systems.MCPServers;

public final class LLMProviderManager {
   private static final LLMProviderManager INSTANCE = new LLMProviderManager();
   private final GeminiProvider geminiProvider = new GeminiProvider();
   private final OllamaProvider ollamaProvider = new OllamaProvider();

   private LLMProviderManager() {
   }

   public static LLMProviderManager getInstance() {
      return INSTANCE;
   }

   public LLMProvider getActiveProvider() {
      AIConfig config = MCPServers.get().getAIConfig();

      return (LLMProvider)(switch (config.getActiveProvider()) {
         case GEMINI -> this.geminiProvider;
         case OLLAMA -> this.ollamaProvider;
      });
   }

   public boolean isConfigured() {
      LLMProvider provider = this.getActiveProvider();
      return provider != null && provider.isConfigured();
   }

   public String getActiveProviderName() {
      LLMProvider provider = this.getActiveProvider();
      return provider != null ? provider.name() : "None";
   }

   public void invalidate() {
      OllamaClientManager.getInstance().invalidateClient();
   }
}
