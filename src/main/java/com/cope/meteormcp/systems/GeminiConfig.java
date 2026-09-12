package com.cope.meteormcp.systems;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;

public class GeminiConfig {
   private static final byte[] KEY_SALT = "meteor-mcp-gemini".getBytes(StandardCharsets.UTF_8);
   private AuthType authType = AuthType.AGY_CLI;
   private String agyPath = "";
   private String agyModel = "gemini-3.8-flash-high";
   private String apiKey = "";
   private GeminiConfig.GeminiModel model = GeminiConfig.GeminiModel.GEMINI_2_5_FLASH;
   private int maxOutputTokens = 2048;
   private float temperature = 0.7F;
   private boolean enabled = false;

   public GeminiConfig copy() {
      GeminiConfig copy = new GeminiConfig();
      copy.authType = this.authType;
      copy.agyPath = this.agyPath;
      copy.agyModel = this.agyModel;
      copy.apiKey = this.apiKey;
      copy.model = this.model;
      copy.maxOutputTokens = this.maxOutputTokens;
      copy.temperature = this.temperature;
      copy.enabled = this.enabled;
      return copy;
   }

   public boolean hasCredentials() {
      if (this.authType == AuthType.AGY_CLI) {
         return com.cope.meteormcp.gemini.AgyClientManager.getInstance().isAgyInstalled();
      }
      return this.apiKey != null && !this.apiKey.isBlank() && this.model != null;
   }

   public boolean isValid() {
      return this.enabled && this.hasCredentials();
   }

   public AuthType getAuthType() {
      return this.authType;
   }

   public void setAuthType(AuthType authType) {
      this.authType = authType != null ? authType : AuthType.AGY_CLI;
   }

   public String getAgyPath() {
      return this.agyPath;
   }

   public void setAgyPath(String agyPath) {
      this.agyPath = agyPath != null ? agyPath.trim() : "";
   }

   public String getAgyModel() {
      return this.agyModel;
   }

   public void setAgyModel(String agyModel) {
      this.agyModel = agyModel != null && !agyModel.isBlank() ? agyModel.trim() : "gemini-3.8-flash-high";
   }

   public String getApiKey() {
      return this.apiKey;
   }

   public void setApiKey(String apiKey) {
      this.apiKey = apiKey != null ? apiKey.trim() : "";
   }

   public GeminiConfig.GeminiModel getModel() {
      return this.model;
   }

   public void setModel(GeminiConfig.GeminiModel model) {
      this.model = model != null ? model : GeminiConfig.GeminiModel.GEMINI_2_5_FLASH;
   }

   public int getMaxOutputTokens() {
      return this.maxOutputTokens;
   }

   public void setMaxOutputTokens(int maxOutputTokens) {
      this.maxOutputTokens = Math.max(1, Math.min(8192, maxOutputTokens));
   }

   public float getTemperature() {
      return this.temperature;
   }

   public void setTemperature(float temperature) {
      if (!Float.isNaN(temperature) && !Float.isInfinite(temperature)) {
         this.temperature = Math.max(0.0F, Math.min(2.0F, temperature));
      } else {
         this.temperature = 0.7F;
      }
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   public String getModelId() {
      return this.model != null ? this.model.getId() : GeminiConfig.GeminiModel.GEMINI_2_5_FLASH.getId();
   }

   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("auth_type", this.authType != null ? this.authType.name() : AuthType.AGY_CLI.name());
      if (this.agyPath != null && !this.agyPath.isEmpty()) {
         tag.putString("agy_path", this.agyPath);
      }
      if (this.agyModel != null && !this.agyModel.isEmpty()) {
         tag.putString("agy_model", this.agyModel);
      }
      if (this.apiKey != null && !this.apiKey.isEmpty()) {
         tag.putString("api_key", encode(this.apiKey));
      }

      tag.putString("model", this.model != null ? this.model.name() : GeminiConfig.GeminiModel.GEMINI_2_5_FLASH.name());
      tag.putInt("max_tokens", this.maxOutputTokens);
      tag.putFloat("temperature", this.temperature);
      tag.putBoolean("enabled", this.enabled);
      return tag;
   }

   public static GeminiConfig fromTag(CompoundTag tag) {
      GeminiConfig config = new GeminiConfig();
      if (tag == null) {
         return config;
      } else {
         if (tag.contains("auth_type")) config.authType = AuthType.fromName(tag.getString("auth_type"));
         if (tag.contains("agy_path")) config.agyPath = tag.getString("agy_path");
         if (tag.contains("agy_model")) config.agyModel = tag.getString("agy_model");
         if (tag.contains("api_key")) config.apiKey = decode(tag.getString("api_key"));
         if (tag.contains("model")) config.model = GeminiConfig.GeminiModel.fromName(tag.getString("model"));
         if (tag.contains("max_tokens")) config.maxOutputTokens = tag.getInt("max_tokens");
         if (tag.contains("temperature")) config.temperature = tag.getFloat("temperature");
         if (tag.contains("enabled")) config.enabled = tag.getBoolean("enabled");
         config.setMaxOutputTokens(config.maxOutputTokens);
         config.setTemperature(config.temperature);
         config.setModel(config.model);
         return config;
      }
   }

   private static String encode(String raw) {
      byte[] bytes = raw.getBytes(StandardCharsets.UTF_8);

      for (int i = 0; i < bytes.length; i++) {
         bytes[i] ^= KEY_SALT[i % KEY_SALT.length];
      }

      return Base64.getEncoder().encodeToString(bytes);
   }

   private static String decode(String encoded) {
      try {
         byte[] bytes = Base64.getDecoder().decode(encoded);

         for (int i = 0; i < bytes.length; i++) {
            bytes[i] ^= KEY_SALT[i % KEY_SALT.length];
         }

         return new String(bytes, StandardCharsets.UTF_8);
      } catch (IllegalArgumentException var3) {
         return "";
      }
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else {
         return !(o instanceof GeminiConfig that)
            ? false
            : this.maxOutputTokens == that.maxOutputTokens
               && Float.compare(that.temperature, this.temperature) == 0
               && this.enabled == that.enabled
               && this.authType == that.authType
               && Objects.equals(this.agyPath, that.agyPath)
               && Objects.equals(this.agyModel, that.agyModel)
               && Objects.equals(this.apiKey, that.apiKey)
               && this.model == that.model;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.authType, this.agyPath, this.agyModel, this.apiKey, this.model, this.maxOutputTokens, this.temperature, this.enabled);
   }

   @Override
   public String toString() {
      return "GeminiConfig{authType="
         + this.authType
         + ", agyModel="
         + this.agyModel
         + ", model="
         + this.model
         + ", maxOutputTokens="
         + this.maxOutputTokens
         + ", temperature="
         + this.temperature
         + ", enabled="
         + this.enabled
         + "}";
   }

   public static enum AuthType {
      AGY_CLI("Antigravity CLI (agy) - No API Key required"),
      API_KEY("Google AI Studio API Key");

      private final String displayName;

      private AuthType(String displayName) {
         this.displayName = displayName;
      }

      public String getDisplayName() {
         return this.displayName;
      }

      @Override
      public String toString() {
         return this.displayName;
      }

      public static AuthType fromName(String name) {
         if (name == null) {
            return AGY_CLI;
         } else {
            try {
               return valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException var2) {
               return AGY_CLI;
            }
         }
      }
   }

   public static enum GeminiModel {
      GEMINI_2_5_PRO("gemini-2.5-pro"),
      GEMINI_2_5_FLASH("gemini-2.5-flash"),
      GEMINI_2_5_FLASH_LITE("gemini-2.5-flash-lite"),
      GEMINI_FLASH_LATEST("gemini-flash-latest"),
      GEMINI_FLASH_LITE_LATEST("gemini-flash-lite-latest");

      private final String id;

      private GeminiModel(String id) {
         this.id = id;
      }

      public String getId() {
         return this.id;
      }

      public static GeminiConfig.GeminiModel fromName(String name) {
         if (name == null) {
            return GEMINI_2_5_FLASH;
         } else {
            try {
               return valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException var2) {
               return GEMINI_2_5_FLASH;
            }
         }
      }
   }
}
