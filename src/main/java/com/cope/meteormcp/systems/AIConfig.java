package com.cope.meteormcp.systems;

import java.util.Locale;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;

public class AIConfig {
   private AIConfig.ProviderType activeProvider = AIConfig.ProviderType.GEMINI;
   private GeminiConfig geminiConfig = new GeminiConfig();
   private OllamaConfig ollamaConfig = new OllamaConfig();

   public AIConfig copy() {
      AIConfig copy = new AIConfig();
      copy.activeProvider = this.activeProvider;
      copy.geminiConfig = this.geminiConfig.copy();
      copy.ollamaConfig = this.ollamaConfig.copy();
      return copy;
   }

   public AIConfig.ProviderType getActiveProvider() {
      return this.activeProvider;
   }

   public void setActiveProvider(AIConfig.ProviderType activeProvider) {
      this.activeProvider = activeProvider != null ? activeProvider : AIConfig.ProviderType.GEMINI;
   }

   public GeminiConfig getGeminiConfig() {
      return this.geminiConfig;
   }

   public void setGeminiConfig(GeminiConfig geminiConfig) {
      this.geminiConfig = geminiConfig != null ? geminiConfig : new GeminiConfig();
   }

   public OllamaConfig getOllamaConfig() {
      return this.ollamaConfig;
   }

   public void setOllamaConfig(OllamaConfig ollamaConfig) {
      this.ollamaConfig = ollamaConfig != null ? ollamaConfig : new OllamaConfig();
   }

   public boolean isActiveProviderValid() {
      return switch (this.activeProvider) {
         case GEMINI -> this.geminiConfig.isValid();
         case OLLAMA -> this.ollamaConfig.isValid();
      };
   }

   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("provider", this.activeProvider.name());
      tag.put("gemini", this.geminiConfig.toTag());
      tag.put("ollama", this.ollamaConfig.toTag());
      return tag;
   }

   public static AIConfig fromTag(CompoundTag tag) {
      AIConfig config = new AIConfig();
      if (tag == null) {
         return config;
      } else {
         if (tag.contains("provider")) config.activeProvider = AIConfig.ProviderType.fromName(tag.getString("provider"));
         if (tag.contains("gemini")) config.geminiConfig = GeminiConfig.fromTag(tag.getCompound("gemini"));
         if (tag.contains("ollama")) config.ollamaConfig = OllamaConfig.fromTag(tag.getCompound("ollama"));
         return config;
      }
   }

   public static AIConfig migrateFromGeminiTag(CompoundTag geminiTag) {
      AIConfig config = new AIConfig();
      config.activeProvider = AIConfig.ProviderType.GEMINI;
      config.geminiConfig = GeminiConfig.fromTag(geminiTag);
      return config;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else {
         return !(o instanceof AIConfig that)
            ? false
            : this.activeProvider == that.activeProvider
               && Objects.equals(this.geminiConfig, that.geminiConfig)
               && Objects.equals(this.ollamaConfig, that.ollamaConfig);
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.activeProvider, this.geminiConfig, this.ollamaConfig);
   }

   @Override
   public String toString() {
      return "AIConfig{provider=" + this.activeProvider + "}";
   }

   public static enum ProviderType {
      GEMINI,
      OLLAMA;

      public static AIConfig.ProviderType fromName(String name) {
         if (name == null) {
            return GEMINI;
         } else {
            try {
               return valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException var2) {
               return GEMINI;
            }
         }
      }
   }
}
