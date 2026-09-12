package com.cope.meteormcp.systems;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

public class MCPServerConfig {
   private String name;
   private MCPServerConfig.TransportType transport;
   private String command;
   private List<String> args;
   private String workingDirectory;
   private String url;
   private Map<String, String> env;
   private boolean autoConnect;
   private int timeout;

   public MCPServerConfig(String name, MCPServerConfig.TransportType transport) {
      this.name = name;
      this.transport = transport;
      this.args = new ArrayList<>();
      this.env = new HashMap<>();
      this.autoConnect = false;
      this.timeout = 5000;
   }

   public String getName() {
      return this.name;
   }

   public MCPServerConfig.TransportType getTransport() {
      return this.transport;
   }

   public String getCommand() {
      return this.command;
   }

   public List<String> getArgs() {
      return this.args;
   }

   public String getWorkingDirectory() {
      return this.workingDirectory;
   }

   public String getUrl() {
      return this.url;
   }

   public Map<String, String> getEnv() {
      return this.env;
   }

   public boolean isAutoConnect() {
      return this.autoConnect;
   }

   public int getTimeout() {
      return this.timeout;
   }

   public void setName(String name) {
      this.name = name;
   }

   public void setTransport(MCPServerConfig.TransportType transport) {
      this.transport = transport;
   }

   public void setCommand(String command) {
      this.command = command;
   }

   public void setArgs(List<String> args) {
      this.args = (List<String>)(args != null ? args : new ArrayList<>());
   }

   public void setWorkingDirectory(String workingDirectory) {
      this.workingDirectory = workingDirectory;
   }

   public void setUrl(String url) {
      this.url = url;
   }

   public void setEnv(Map<String, String> env) {
      this.env = (Map<String, String>)(env != null ? env : new HashMap<>());
   }

   public void setAutoConnect(boolean autoConnect) {
      this.autoConnect = autoConnect;
   }

   public void setTimeout(int timeout) {
      this.timeout = timeout;
   }

   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("name", this.name);
      tag.putString("transport", this.transport.name());
      tag.putBoolean("autoConnect", this.autoConnect);
      tag.putInt("timeout", this.timeout);
      if (this.command != null) {
         tag.putString("command", this.command);
      }

      if (this.workingDirectory != null) {
         tag.putString("workingDirectory", this.workingDirectory);
      }

      if (this.url != null) {
         tag.putString("url", this.url);
      }

      if (this.args != null && !this.args.isEmpty()) {
         ListTag argsList = new ListTag();

         for (String arg : this.args) {
            argsList.add(StringTag.valueOf(arg));
         }

         tag.put("args", argsList);
      }

      if (this.env != null && !this.env.isEmpty()) {
         CompoundTag envTag = new CompoundTag();

         for (Entry<String, String> entry : this.env.entrySet()) {
            envTag.putString(entry.getKey(), entry.getValue());
         }

         tag.put("env", envTag);
      }

      return tag;
   }

   public static MCPServerConfig fromTag(CompoundTag tag) {
      if (tag.contains("name") && tag.contains("transport")) {
         String name = tag.getString("name");
         MCPServerConfig.TransportType transport = MCPServerConfig.TransportType.valueOf(tag.getString("transport"));
         MCPServerConfig config = new MCPServerConfig(name, transport);
         config.setAutoConnect(tag.contains("autoConnect") && tag.getBoolean("autoConnect"));
         config.setTimeout(tag.contains("timeout") ? tag.getInt("timeout") : 5000);
         if (tag.contains("command")) config.setCommand(tag.getString("command"));
         if (tag.contains("workingDirectory")) config.setWorkingDirectory(tag.getString("workingDirectory"));
         if (tag.contains("url")) config.setUrl(tag.getString("url"));
         if (tag.contains("args") && tag.get("args") instanceof ListTag argsList) {
            List<String> args = new ArrayList<>();

            for (Tag argElement : argsList) {
               args.add(argElement.getAsString());
            }

            config.setArgs(args);
         }

         if (tag.contains("env")) {
            CompoundTag envTag = tag.getCompound("env");
            if (envTag != null) {
               Map<String, String> env = new HashMap<>();

               for (String key : envTag.getAllKeys()) {
                  env.put(key, envTag.getString(key));
               }

               config.setEnv(env);
            }
         }

         return config;
      } else {
         throw new RuntimeException("Invalid MCPServerConfig NBT: missing required fields");
      }
   }

   public boolean isValid() {
      if (this.name != null && !this.name.trim().isEmpty()) {
         switch (this.transport) {
            case STDIO:
               return this.command != null && !this.command.trim().isEmpty();
            case SSE:
            case HTTP:
               return this.url != null && !this.url.trim().isEmpty();
            default:
               return false;
         }
      } else {
         return false;
      }
   }

   @Override
   public String toString() {
      return this.name + " (" + this.transport + ")";
   }

   public static enum TransportType {
      STDIO,
      SSE,
      HTTP;
   }
}
