package com.cope.meteormcp.systems;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;

public class OllamaConfig {
   public static final String[] KEEP_ALIVE_OPTIONS = new String[]{"5m", "10m", "30m", "1h", "-1"};
   private static final String DEFAULT_HOST = "http://localhost:11434";
   private static final Set<String> VALID_KEEP_ALIVE = Set.of("0m", "1m", "5m", "10m", "30m", "1h", "2h", "-1");
   private String host = "http://localhost:11434";
   private String model = "llama3.1";
   private int contextLength = 8192;
   private int maxOutputTokens = 2048;
   private float temperature = 0.7F;
   private boolean enabled = false;
   private String keepAlive = "5m";

   public OllamaConfig copy() {
      OllamaConfig copy = new OllamaConfig();
      copy.host = this.host;
      copy.model = this.model;
      copy.contextLength = this.contextLength;
      copy.maxOutputTokens = this.maxOutputTokens;
      copy.temperature = this.temperature;
      copy.enabled = this.enabled;
      copy.keepAlive = this.keepAlive;
      return copy;
   }

   public boolean isConfigured() {
      return this.host != null && !this.host.isBlank() && this.model != null && !this.model.isBlank();
   }

   public boolean isValid() {
      return this.enabled && this.isConfigured();
   }

   public String getHost() {
      return this.host;
   }

   public void setHost(String host) {
      this.host = normalizeHost(host);
   }

   public String getModel() {
      return this.model;
   }

   public void setModel(String model) {
      this.model = model != null ? model.trim() : "llama3.1";
   }

   public int getContextLength() {
      return this.contextLength;
   }

   public void setContextLength(int contextLength) {
      this.contextLength = Math.max(2048, Math.min(131072, contextLength));
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

   public String getKeepAlive() {
      return this.keepAlive;
   }

   public void setKeepAlive(String keepAlive) {
      if (keepAlive != null && VALID_KEEP_ALIVE.contains(keepAlive.trim())) {
         this.keepAlive = keepAlive.trim();
      } else {
         this.keepAlive = "5m";
      }
   }

   private static String normalizeHost(String host) {
      if (host == null) {
         return "http://localhost:11434";
      } else {
         String candidate = host.trim();
         if (candidate.isEmpty()) {
            return "http://localhost:11434";
         } else {
            try {
               URI parsed = new URI(candidate);
               String scheme = parsed.getScheme();
               String hostName = parsed.getHost();
               int port = parsed.getPort();
               String path = parsed.getPath();
               if (scheme == null || hostName == null) {
                  return "http://localhost:11434";
               } else if (parsed.getRawUserInfo() != null) {
                  return "http://localhost:11434";
               } else if (parsed.getRawQuery() != null || parsed.getRawFragment() != null) {
                  return "http://localhost:11434";
               } else if (port >= -1 && port <= 65535) {
                  String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
                  if (!normalizedScheme.equals("http") && !normalizedScheme.equals("https")) {
                     return "http://localhost:11434";
                  } else if (path != null && !path.isEmpty() && !path.equals("/")) {
                     return "http://localhost:11434";
                  } else {
                     URI normalized = new URI(normalizedScheme, null, hostName, port, null, null, null);
                     return normalized.toString();
                  }
               } else {
                  return "http://localhost:11434";
               }
            } catch (IllegalArgumentException | URISyntaxException var9) {
               return "http://localhost:11434";
            }
         }
      }
   }

   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("host", this.host != null ? this.host : "");
      tag.putString("model", this.model != null ? this.model : "");
      tag.putInt("context_length", this.contextLength);
      tag.putInt("max_tokens", this.maxOutputTokens);
      tag.putFloat("temperature", this.temperature);
      tag.putBoolean("enabled", this.enabled);
      tag.putString("keep_alive", this.keepAlive != null ? this.keepAlive : "5m");
      return tag;
   }

   public static OllamaConfig fromTag(CompoundTag tag) {
      OllamaConfig config = new OllamaConfig();
      if (tag == null) {
         return config;
      } else {
         if (tag.contains("host")) config.setHost(tag.getString("host"));
         if (tag.contains("model")) config.setModel(tag.getString("model"));
         if (tag.contains("context_length")) config.contextLength = tag.getInt("context_length");
         if (tag.contains("max_tokens")) config.maxOutputTokens = tag.getInt("max_tokens");
         if (tag.contains("temperature")) config.temperature = tag.getFloat("temperature");
         if (tag.contains("enabled")) config.enabled = tag.getBoolean("enabled");
         if (tag.contains("keep_alive")) config.setKeepAlive(tag.getString("keep_alive"));
         config.setContextLength(config.contextLength);
         config.setMaxOutputTokens(config.maxOutputTokens);
         config.setTemperature(config.temperature);
         return config;
      }
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else {
         return !(o instanceof OllamaConfig that)
            ? false
            : this.contextLength == that.contextLength
               && this.maxOutputTokens == that.maxOutputTokens
               && Float.compare(that.temperature, this.temperature) == 0
               && this.enabled == that.enabled
               && Objects.equals(this.host, that.host)
               && Objects.equals(this.model, that.model)
               && Objects.equals(this.keepAlive, that.keepAlive);
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.host, this.model, this.contextLength, this.maxOutputTokens, this.temperature, this.enabled, this.keepAlive);
   }

   @Override
   public String toString() {
      return "OllamaConfig{host='"
         + this.host
         + "', model='"
         + this.model
         + "', contextLength="
         + this.contextLength
         + ", maxOutputTokens="
         + this.maxOutputTokens
         + ", temperature="
         + this.temperature
         + ", enabled="
         + this.enabled
         + ", keepAlive='"
         + this.keepAlive
         + "'}";
   }
}
