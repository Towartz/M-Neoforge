package com.cope.meteormcp.gui.screens;

import com.cope.meteormcp.gui.MaskedTextBoxRenderer;
import com.cope.meteormcp.llm.LLMProvider;
import com.cope.meteormcp.llm.LLMProviderManager;
import com.cope.meteormcp.ollama.OllamaClientManager;
import com.cope.meteormcp.systems.AIConfig;
import com.cope.meteormcp.systems.GeminiConfig;
import com.cope.meteormcp.systems.MCPServers;
import com.cope.meteormcp.systems.OllamaConfig;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.input.WDoubleEdit;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;

public class AISettingsScreen extends WindowScreen {
   private final WidgetScreen parent;
   private AIConfig.ProviderType selectedProvider;
   private GeminiConfig editedGemini;
   private OllamaConfig editedOllama;
   private List<String> ollamaModels;
   private boolean ollamaModelsFetched;
   private WDropdown<AIConfig.ProviderType> providerDropdown;
   private WLabel statusLabel;
   private WDropdown<GeminiConfig.AuthType> geminiAuthTypeDropdown;
   private WDropdown<String> geminiAgyModelDropdown;
   private WTextBox geminiApiKeyInput;
   private WDropdown<GeminiConfig.GeminiModel> geminiModelDropdown;
   private WIntEdit geminiMaxTokensInput;
   private WDoubleEdit geminiTemperatureInput;
   private WCheckbox geminiEnabledCheckbox;
   private WTextBox ollamaHostInput;
   private WDropdown<String> ollamaModelDropdown;
   private WDropdown<String> ollamaKeepAliveDropdown;
   private WIntEdit ollamaContextLengthInput;
   private WIntEdit ollamaMaxTokensInput;
   private WDoubleEdit ollamaTemperatureInput;
   private WCheckbox ollamaEnabledCheckbox;

   public AISettingsScreen(GuiTheme theme, WidgetScreen parent) {
      super(theme, "Configure AI");
      this.parent = parent;
      AIConfig saved = MCPServers.get().getAIConfig();
      this.selectedProvider = saved.getActiveProvider();
      this.editedGemini = saved.getGeminiConfig().copy();
      this.editedOllama = saved.getOllamaConfig().copy();
      this.ollamaModels = new ArrayList<>();
      this.ollamaModelsFetched = false;
   }

   public void initWidgets() {
      this.add(this.theme.label("AI Provider:"));
      this.providerDropdown = (WDropdown<AIConfig.ProviderType>)this.add(this.theme.dropdown(AIConfig.ProviderType.values(), this.selectedProvider))
         .expandX()
         .widget();
      this.providerDropdown.action = () -> {
         this.snapshotCurrentWidgets();
         this.selectedProvider = (AIConfig.ProviderType)this.providerDropdown.get();
         this.reload();
      };
      this.add(this.theme.horizontalSeparator()).expandX();
      if (this.selectedProvider == AIConfig.ProviderType.GEMINI) {
         this.initGeminiWidgets();
      } else {
         this.initOllamaWidgets();
      }

      this.add(this.theme.horizontalSeparator()).expandX();
      this.statusLabel = (WLabel)this.add(this.theme.label("")).expandX().widget();
      this.updateStatusLabel();
      WHorizontalList buttons = (WHorizontalList)this.add(this.theme.horizontalList()).expandX().widget();
      if (this.selectedProvider == AIConfig.ProviderType.GEMINI) {
         WButton testButton = (WButton)buttons.add(this.theme.button("Test Connection")).expandX().widget();
         testButton.action = this::testGeminiConnection;
      } else {
         WButton testButton = (WButton)buttons.add(this.theme.button("Test Connection")).expandX().widget();
         testButton.action = this::testOllamaConnection;
         WButton loadButton = (WButton)buttons.add(this.theme.button("Load Model")).expandX().widget();
         loadButton.action = this::loadOllamaModel;
      }

      WButton saveButton = (WButton)buttons.add(this.theme.button("Save")).expandX().widget();
      saveButton.action = this::save;
      WButton cancelButton = (WButton)buttons.add(this.theme.button("Cancel")).expandX().widget();
      cancelButton.action = () -> MeteorClient.mc.setScreen(this.parent);
      if (this.selectedProvider == AIConfig.ProviderType.OLLAMA && !this.ollamaModelsFetched) {
         this.ollamaModelsFetched = true;
         this.fetchOllamaModels();
      }
   }

   private void initGeminiWidgets() {
      this.add(this.theme.label("Auth Mode:"));
      this.geminiAuthTypeDropdown = (WDropdown<GeminiConfig.AuthType>)this.add(
            this.theme.dropdown(GeminiConfig.AuthType.values(), this.editedGemini.getAuthType())
         )
         .expandX()
         .widget();
      this.geminiAuthTypeDropdown.action = () -> {
         this.snapshotCurrentWidgets();
         this.editedGemini.setAuthType((GeminiConfig.AuthType)this.geminiAuthTypeDropdown.get());
         this.reload();
      };

      if (this.editedGemini.getAuthType() == GeminiConfig.AuthType.AGY_CLI) {
         boolean agyInstalled = com.cope.meteormcp.gemini.AgyClientManager.getInstance().isAgyInstalled();
         String resolvedPath = com.cope.meteormcp.gemini.AgyClientManager.getInstance().getResolvedAgyPath(this.editedGemini);
         this.add(this.theme.label("AGY CLI: " + (agyInstalled ? "Detected" : "Not Found (Install agy)"))).expandX();

         this.add(this.theme.label("Model:"));
         String[] agyChoices = com.cope.meteormcp.gemini.AgyClientManager.SUPPORTED_AGY_MODELS.toArray(String[]::new);
         String currentAgyModel = this.editedGemini.getAgyModel();
         if (currentAgyModel == null || currentAgyModel.isBlank()) currentAgyModel = agyChoices[0];
         this.geminiAgyModelDropdown = (WDropdown<String>)this.add(this.theme.dropdown(agyChoices, currentAgyModel)).expandX().widget();
      } else {
         this.add(this.theme.label("Gemini API Key:"));
         this.geminiApiKeyInput = (WTextBox)this.add(this.theme.textBox(this.editedGemini.getApiKey(), (text, c) -> true, MaskedTextBoxRenderer.class))
            .expandX()
            .widget();
         this.add(this.theme.label("Model:"));
         this.geminiModelDropdown = (WDropdown<GeminiConfig.GeminiModel>)this.add(
               this.theme.dropdown(GeminiConfig.GeminiModel.values(), this.editedGemini.getModel())
            )
            .expandX()
            .widget();
      }

      this.add(this.theme.label("Max Output Tokens:"));
      this.geminiMaxTokensInput = (WIntEdit)this.add(this.theme.intEdit(this.editedGemini.getMaxOutputTokens(), 256, 8192, true)).expandX().widget();
      this.add(this.theme.label("Temperature:"));
      this.geminiTemperatureInput = (WDoubleEdit)this.add(this.theme.doubleEdit((double)this.editedGemini.getTemperature(), 0.0, 2.0, 0.0, 2.0))
         .expandX()
         .widget();
      this.geminiTemperatureInput.decimalPlaces = 2;
      this.geminiTemperatureInput.small = true;
      this.add(this.theme.label("Enable Gemini:"));
      this.geminiEnabledCheckbox = (WCheckbox)this.add(this.theme.checkbox(this.editedGemini.isEnabled())).widget();
   }

   private void initOllamaWidgets() {
      this.add(this.theme.label("Ollama Host:"));
      this.ollamaHostInput = (WTextBox)this.add(this.theme.textBox(this.editedOllama.getHost())).expandX().widget();
      this.add(this.theme.label("Model:"));
      String[] modelChoices = this.buildModelChoices(this.editedOllama.getModel());
      String selected = this.resolveSelectedModel(this.editedOllama.getModel(), modelChoices);
      WHorizontalList modelRow = (WHorizontalList)this.add(this.theme.horizontalList()).expandX().widget();
      this.ollamaModelDropdown = (WDropdown<String>)modelRow.add(this.theme.dropdown(modelChoices, selected)).expandX().widget();
      WButton refreshBtn = (WButton)modelRow.add(this.theme.button("Refresh")).widget();
      refreshBtn.action = this::fetchOllamaModels;
      this.add(this.theme.label("Keep Alive:"));
      String currentKeepAlive = this.resolveKeepAlive(this.editedOllama.getKeepAlive());
      this.ollamaKeepAliveDropdown = (WDropdown<String>)this.add(this.theme.dropdown(OllamaConfig.KEEP_ALIVE_OPTIONS, currentKeepAlive)).expandX().widget();
      this.add(this.theme.label("Context Length:"));
      this.ollamaContextLengthInput = (WIntEdit)this.add(this.theme.intEdit(this.editedOllama.getContextLength(), 2048, 131072, true)).expandX().widget();
      this.add(this.theme.label("Max Output Tokens:"));
      this.ollamaMaxTokensInput = (WIntEdit)this.add(this.theme.intEdit(this.editedOllama.getMaxOutputTokens(), 256, 8192, true)).expandX().widget();
      this.add(this.theme.label("Temperature:"));
      this.ollamaTemperatureInput = (WDoubleEdit)this.add(this.theme.doubleEdit((double)this.editedOllama.getTemperature(), 0.0, 2.0, 0.0, 2.0))
         .expandX()
         .widget();
      this.ollamaTemperatureInput.decimalPlaces = 2;
      this.ollamaTemperatureInput.small = true;
      this.add(this.theme.label("Enable Ollama:"));
      this.ollamaEnabledCheckbox = (WCheckbox)this.add(this.theme.checkbox(this.editedOllama.isEnabled())).widget();
   }

   private String[] buildModelChoices(String currentModel) {
      Set<String> choices = new LinkedHashSet<>();

      for (String model : this.ollamaModels) {
         if (model != null && !model.isBlank()) {
            choices.add(model);
         }
      }

      if (currentModel != null && !currentModel.isBlank()) {
         choices.add(currentModel);
      }

      if (choices.isEmpty()) {
         choices.add("llama3.1");
      }

      return choices.toArray(String[]::new);
   }

   private String resolveSelectedModel(String currentModel, String[] choices) {
      if (currentModel != null && !currentModel.isBlank()) {
         for (String c : choices) {
            if (c.equals(currentModel)) {
               return currentModel;
            }
         }
      }

      return choices[0];
   }

   private String resolveKeepAlive(String value) {
      for (String option : OllamaConfig.KEEP_ALIVE_OPTIONS) {
         if (option.equals(value)) {
            return value;
         }
      }

      return "5m";
   }

   private void fetchOllamaModels() {
      OllamaConfig tempConfig = new OllamaConfig();
      String host = this.ollamaHostInput != null ? this.ollamaHostInput.get().trim() : this.editedOllama.getHost();
      tempConfig.setHost(host);
      this.updateStatus("Fetching models from " + host + "...", true);
      MeteorExecutor.execute(() -> {
         List<String> models = OllamaClientManager.getInstance().listModels(tempConfig);
         Runnable update = () -> {
            this.snapshotCurrentWidgets();
            if (!models.isEmpty()) {
               this.ollamaModels = models;
               this.updateStatus("Found " + models.size() + " model(s)", true);
            } else {
               this.updateStatus("No models found. Is Ollama running at " + host + "?", false);
            }

            this.reload();
         };
         if (MeteorClient.mc != null) {
            MeteorClient.mc.execute(update);
         } else {
            update.run();
         }
      });
   }

   private void snapshotCurrentWidgets() {
      if (this.geminiAuthTypeDropdown != null) {
         this.editedGemini.setAuthType((GeminiConfig.AuthType)this.geminiAuthTypeDropdown.get());
      }
      if (this.geminiAgyModelDropdown != null) {
         this.editedGemini.setAgyModel((String)this.geminiAgyModelDropdown.get());
      }
      if (this.geminiApiKeyInput != null) {
         this.editedGemini.setApiKey(this.geminiApiKeyInput.get().trim());
      }
      if (this.geminiModelDropdown != null) {
         this.editedGemini.setModel((GeminiConfig.GeminiModel)this.geminiModelDropdown.get());
      }
      if (this.geminiMaxTokensInput != null) {
         this.editedGemini.setMaxOutputTokens(this.geminiMaxTokensInput.get());
      }
      if (this.geminiTemperatureInput != null) {
         this.editedGemini.setTemperature((float)this.geminiTemperatureInput.get());
      }
      if (this.geminiEnabledCheckbox != null) {
         this.editedGemini.setEnabled(this.geminiEnabledCheckbox.checked);
      }

      if (this.ollamaHostInput != null) {
         this.editedOllama.setHost(this.ollamaHostInput.get().trim());
         this.editedOllama.setModel((String)this.ollamaModelDropdown.get());
         this.editedOllama.setKeepAlive((String)this.ollamaKeepAliveDropdown.get());
         this.editedOllama.setContextLength(this.ollamaContextLengthInput.get());
         this.editedOllama.setMaxOutputTokens(this.ollamaMaxTokensInput.get());
         this.editedOllama.setTemperature((float)this.ollamaTemperatureInput.get());
         this.editedOllama.setEnabled(this.ollamaEnabledCheckbox.checked);
      }
   }

   private AIConfig collectFormConfig() {
      this.snapshotCurrentWidgets();
      AIConfig config = new AIConfig();
      config.setActiveProvider(this.selectedProvider);
      config.setGeminiConfig(this.editedGemini.copy());
      config.setOllamaConfig(this.editedOllama.copy());
      return config;
   }

   private void testGeminiConnection() {
      AIConfig draft = this.collectFormConfig();
      this.updateStatus("Testing Gemini connection...", true);
      MeteorExecutor.execute(() -> {
         AIConfig original = MCPServers.get().getAIConfig();
         MCPServers.get().setAIConfig(draft);

         try {
            LLMProvider provider = LLMProviderManager.getInstance().getActiveProvider();
            LLMProvider.TestResult result = provider.testConnection();
            Runnable update = () -> this.updateStatus(result.message(), result.success());
            if (MeteorClient.mc != null) {
               MeteorClient.mc.execute(update);
            } else {
               update.run();
            }
         } finally {
            MCPServers.get().setAIConfig(original);
         }
      });
   }

   private void testOllamaConnection() {
      this.snapshotCurrentWidgets();
      this.updateStatus("Testing connection...", true);
      OllamaConfig testConfig = this.editedOllama.copy();
      MeteorExecutor.execute(() -> {
         OllamaClientManager.TestResult result = OllamaClientManager.getInstance().quickTest(testConfig);
         Runnable update = () -> this.updateStatus(result.message(), result.success());
         if (MeteorClient.mc != null) {
            MeteorClient.mc.execute(update);
         } else {
            update.run();
         }
      });
   }

   private void loadOllamaModel() {
      this.snapshotCurrentWidgets();
      this.updateStatus("Loading model (this may take a while on first load)...", true);
      OllamaConfig loadConfig = this.editedOllama.copy();
      MeteorExecutor.execute(() -> {
         OllamaClientManager.TestResult result = OllamaClientManager.getInstance().loadModel(loadConfig);
         Runnable update = () -> this.updateStatus(result.message(), result.success());
         if (MeteorClient.mc != null) {
            MeteorClient.mc.execute(update);
         } else {
            update.run();
         }
      });
   }

   private void save() {
      AIConfig config = this.collectFormConfig();
      MCPServers.get().setAIConfig(config);
      if (this.parent != null) {
         this.parent.reload();
      }

      MeteorClient.mc.setScreen(this.parent);
   }

   private void updateStatusLabel() {
      boolean valid = this.selectedProvider == AIConfig.ProviderType.GEMINI ? this.editedGemini.isValid() : this.editedOllama.isValid();
      String text = "Status: " + this.selectedProvider.name() + " - " + (valid ? "Enabled" : "Not Active");
      this.updateStatus(text, valid);
   }

   private void updateStatus(String message, boolean success) {
      if (this.statusLabel != null) {
         this.statusLabel.set(message);
         this.statusLabel.color(success ? this.theme.textColor() : this.theme.textSecondaryColor());
      }
   }
}
