package com.cope.meteormcp.ollama;

import com.cope.meteormcp.MeteorMCPAddon;
import com.cope.meteormcp.systems.MCPServers;
import com.cope.meteormcp.systems.OllamaConfig;
import com.fasterxml.jackson.databind.DeserializationFeature;
import io.github.ollama4j.Ollama;
import io.github.ollama4j.models.chat.OllamaChatMessageRole;
import io.github.ollama4j.models.chat.OllamaChatRequest;
import io.github.ollama4j.models.chat.OllamaChatResult;
import io.github.ollama4j.utils.Utils;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public final class OllamaClientManager {
   private static final OllamaClientManager INSTANCE = new OllamaClientManager();
   private static final AtomicBoolean mapperConfigured = new AtomicBoolean(false);
   private final ReentrantLock clientLock = new ReentrantLock();
   private Ollama client;
   private OllamaConfig currentConfig;

   private OllamaClientManager() {
   }

   public static OllamaClientManager getInstance() {
      return INSTANCE;
   }

   public boolean isConfigured() {
      OllamaConfig config = MCPServers.get().getAIConfig().getOllamaConfig();
      return config != null && config.isValid();
   }

   public Ollama getClient() {
      this.clientLock.lock();

      Ollama var2;
      try {
         OllamaConfig config = MCPServers.get().getAIConfig().getOllamaConfig();
         if (config == null || !config.isValid()) {
            throw new IllegalStateException("Ollama configuration is not valid or not enabled.");
         }

         if (this.client == null || this.currentConfig == null || !Objects.equals(this.currentConfig, config)) {
            this.client = this.buildClient(config);
            this.currentConfig = config.copy();
            MeteorMCPAddon.LOG.info("Ollama client initialized for model {} at {}", config.getModel(), config.getHost());
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
         this.client = null;
         this.currentConfig = null;
      } finally {
         this.clientLock.unlock();
      }
   }

   public void shutdown() {
      this.invalidateClient();
   }

   static void ensureMapperCompatibility() {
      if (mapperConfigured.compareAndSet(false, true)) {
         Utils.getObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      }
   }

   public OllamaClientManager.TestResult quickTest(OllamaConfig config) {
      if (config != null && config.isConfigured()) {
         try {
            Ollama tempApi = this.buildClient(config);
            if (!tempApi.ping()) {
               return new OllamaClientManager.TestResult(false, "Server not reachable at " + config.getHost());
            } else {
               List<String> models = tempApi.listModels().stream().map(m -> m.getName()).collect(Collectors.toList());
               if (models.isEmpty()) {
                  return new OllamaClientManager.TestResult(false, "Server reachable but no models installed.");
               } else {
                  boolean modelFound = models.stream().anyMatch(name -> name.equals(config.getModel()) || name.startsWith(config.getModel() + ":"));
                  return !modelFound
                     ? new OllamaClientManager.TestResult(
                        false, "Server reachable but model '" + config.getModel() + "' not found. Available: " + String.join(", ", models)
                     )
                     : new OllamaClientManager.TestResult(true, "Connected! Model '" + config.getModel() + "' is available.");
               }
            }
         } catch (Exception var5) {
            return new OllamaClientManager.TestResult(false, safeMessage(var5));
         }
      } else {
         return new OllamaClientManager.TestResult(false, "Provide a host and model before testing.");
      }
   }

   public OllamaClientManager.TestResult loadModel(OllamaConfig config) {
      if (config != null && config.isConfigured()) {
         try {
            Ollama tempApi = this.buildClient(config);
            String keepAlive = config.getKeepAlive() != null ? config.getKeepAlive() : "5m";
            OllamaChatRequest request = OllamaChatRequest.builder()
               .withModel(config.getModel())
               .withMessage(OllamaChatMessageRole.USER, "Respond with only: ok")
               .withKeepAlive(keepAlive)
               .build();
            long start = System.currentTimeMillis();
            OllamaChatResult result = tempApi.chat(request, token -> {
            });
            long elapsed = System.currentTimeMillis() - start;
            String text = result.getResponseModel().getMessage().getResponse();
            if (text == null || text.isBlank()) {
               text = "(empty response)";
            }

            return new OllamaClientManager.TestResult(true, "Model loaded in " + elapsed + "ms (keep-alive: " + keepAlive + "). Response: " + text.trim());
         } catch (Exception var11) {
            return new OllamaClientManager.TestResult(false, "Load failed: " + safeMessage(var11));
         }
      } else {
         return new OllamaClientManager.TestResult(false, "Provide a host and model before loading.");
      }
   }

   public List<String> listModels(OllamaConfig config) {
      if (config != null && config.getHost() != null && !config.getHost().isBlank()) {
         try {
            Ollama tempApi = this.buildClient(config);
            return tempApi.listModels().stream().map(m -> m.getName()).collect(Collectors.toList());
         } catch (Exception var3) {
            MeteorMCPAddon.LOG.debug("Failed to list Ollama models: {}", var3.getMessage());
            return Collections.emptyList();
         }
      } else {
         return Collections.emptyList();
      }
   }

   private Ollama buildClient(OllamaConfig config) {
      ensureMapperCompatibility();
      Ollama api = new Ollama(config.getHost());
      api.setRequestTimeoutSeconds(60L);
      return api;
   }

   private static String safeMessage(Exception e) {
      String message = e.getMessage();
      return message != null && !message.isBlank() ? message : e.getClass().getSimpleName();
   }

   public static record TestResult(boolean success, String message) {
   }
}
