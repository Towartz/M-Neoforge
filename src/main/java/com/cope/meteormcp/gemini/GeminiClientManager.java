package com.cope.meteormcp.gemini;

import com.cope.meteormcp.MeteorMCPAddon;
import com.cope.meteormcp.systems.GeminiConfig;
import com.cope.meteormcp.systems.MCPServers;
import com.google.common.collect.ImmutableList;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.GenerateContentConfig.Builder;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

public final class GeminiClientManager {
   private static final GeminiClientManager INSTANCE = new GeminiClientManager();
   private final ReentrantLock clientLock = new ReentrantLock();
   private Client client;
   private GeminiConfig currentConfig;

   private GeminiClientManager() {
   }

   public static GeminiClientManager getInstance() {
      return INSTANCE;
   }

   public boolean isConfigured() {
      GeminiConfig config = MCPServers.get().getGeminiConfig();
      return config != null && config.isValid();
   }

   public Client getClient() {
      this.clientLock.lock();

      Client var2;
      try {
         GeminiConfig config = MCPServers.get().getGeminiConfig();
         if (config == null || !config.isValid()) {
            throw new IllegalStateException("Gemini configuration is not valid or not enabled.");
         }

         if (this.client == null || this.currentConfig == null || !Objects.equals(this.currentConfig, config)) {
            this.closeClientUnsafe();
            this.client = this.buildClient(config);
            this.currentConfig = config.copy();
            MeteorMCPAddon.LOG.info("Gemini client initialized for model {}", this.currentConfig.getModelId());
            return this.client;
         }

         var2 = this.client;
      } finally {
         this.clientLock.unlock();
      }

      return var2;
   }

   public void invalidateClient() {
      this.clientLock.lock();

      try {
         this.closeClientUnsafe();
      } finally {
         this.clientLock.unlock();
      }
   }

   public void shutdown() {
      this.invalidateClient();
   }

   public GeminiClientManager.TestResult testConfiguration(GeminiConfig config) {
      if (config != null && config.hasCredentials()) {
         Client temp = null;

         GeminiClientManager.TestResult text;
         try {
            temp = this.buildClient(config);
            GenerateContentConfig requestConfig = createBaseRequestConfig(config).build();
            GenerateContentResponse response = temp.models.generateContent(config.getModelId(), "Respond with: test successful", requestConfig);
            String textx = "";
            if (response != null) {
               textx = response.text();
               if (textx == null || textx.isBlank()) {
                  ImmutableList<Part> parts = response.parts();
                  if (parts != null && !parts.isEmpty()) {
                     textx = parts.stream().map(part -> part.text().orElse("")).filter(str -> !str.isBlank()).findFirst().orElse("");
                  }
               }
            }

            if (textx.isEmpty()) {
               textx = "Received empty response from Gemini.";
            }

            return new GeminiClientManager.TestResult(true, textx);
         } catch (Exception var16) {
            String message = var16.getMessage();
            if (message == null || message.isBlank()) {
               message = var16.getClass().getSimpleName();
            }

            text = new GeminiClientManager.TestResult(false, message);
         } finally {
            if (temp != null) {
               try {
                  temp.close();
               } catch (Exception var15) {
               }
            }
         }

         return text;
      } else {
         return new GeminiClientManager.TestResult(false, "Provide an API key and model before testing.");
      }
   }

   static Builder createBaseRequestConfig(GeminiConfig config) {
      return GenerateContentConfig.builder().maxOutputTokens(config.getMaxOutputTokens()).temperature(config.getTemperature());
   }

   private Client buildClient(GeminiConfig config) {
      return Client.builder().apiKey(config.getApiKey()).build();
   }

   private void closeClientUnsafe() {
      if (this.client != null) {
         try {
            this.client.close();
         } catch (Exception var5) {
            MeteorMCPAddon.LOG.warn("Error while closing Gemini client: {}", var5.getMessage());
         } finally {
            this.client = null;
            this.currentConfig = null;
         }
      }
   }

   public static final class TestResult {
      private final boolean success;
      private final String message;

      public TestResult(boolean success, String message) {
         this.success = success;
         this.message = message;
      }

      public boolean success() {
         return this.success;
      }

      public String message() {
         return this.message;
      }
   }
}
