package com.cope.meteormcp.systems;

import com.cope.meteormcp.MeteorMCPAddon;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters.Builder;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class MCPServerConnection {
   private final MCPServerConfig config;
   private McpSyncClient client;
   private McpClientTransport transport;
   private MCPRequestQueue requestQueue;
   private boolean connected;
   private List<Tool> tools;
   private long lastConnectAttempt;
   private static final long RECONNECT_COOLDOWN = 5000L;

   public MCPServerConnection(MCPServerConfig config) {
      this.config = config;
      this.connected = false;
      this.tools = new ArrayList<>();
      this.lastConnectAttempt = 0L;
   }

   public boolean connect() {
      if (this.connected) {
         return true;
      } else {
         long now = System.currentTimeMillis();
         if (now - this.lastConnectAttempt < 5000L) {
            return false;
         } else {
            this.lastConnectAttempt = now;

            try {
               switch (this.config.getTransport()) {
                  case STDIO:
                     return this.connectStdio();
                  case SSE:
                  case HTTP:
                     MeteorMCPAddon.LOG.warn("SSE/HTTP transport not yet implemented for {}", this.config.getName());
                     return false;
                  default:
                     return false;
               }
            } catch (Exception var4) {
               MeteorMCPAddon.LOG.error("Failed to connect to MCP server {}: {}", this.config.getName(), var4.getMessage());
               return false;
            }
         }
      }
   }

   private boolean connectStdio() {
      try {
         String command = this.config.getCommand();
         List<String> args = this.config.getArgs() != null ? new ArrayList<>(this.config.getArgs()) : new ArrayList<>();
         if (this.config.getWorkingDirectory() != null && !this.config.getWorkingDirectory().trim().isEmpty()) {
            String workingDir = this.config.getWorkingDirectory();
            StringBuilder fullCommand = new StringBuilder(command);

            for (String arg : args) {
               fullCommand.append(" ").append(arg);
            }

            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            if (isWindows) {
               command = "cmd.exe";
               args = new ArrayList<>();
               args.add("/c");
               args.add("cd /d " + workingDir + " && " + fullCommand.toString());
            } else {
               command = "sh";
               args = new ArrayList<>();
               args.add("-c");
               args.add("cd " + workingDir + " && " + fullCommand.toString());
            }
         }

         Builder paramsBuilder = ServerParameters.builder(command);
         if (!args.isEmpty()) {
            paramsBuilder.args(args);
         }

         if (this.config.getEnv() != null && !this.config.getEnv().isEmpty()) {
            paramsBuilder.env(this.config.getEnv());
         }

         ServerParameters params = paramsBuilder.build();
         this.transport = new StdioClientTransport(params, McpJsonDefaults.getMapper());
         this.client = McpClient.sync(this.transport)
            .requestTimeout(Duration.ofMillis((long)this.config.getTimeout()))
            .initializationTimeout(Duration.ofMillis((long)this.config.getTimeout()))
            .build();
         this.client.initialize();
         ListToolsResult result = this.client.listTools();
         this.tools = new ArrayList<>(result.tools());
         this.requestQueue = new MCPRequestQueue(this.config.getName(), this.client);
         this.connected = true;
         MeteorMCPAddon.LOG.info("Connected to MCP server: {} ({} tools available)", this.config.getName(), this.tools.size());
         return true;
      } catch (Exception var7) {
         MeteorMCPAddon.LOG.error("Error connecting to MCP server {}: {}", this.config.getName(), var7.getMessage());
         this.disconnect();
         return false;
      }
   }

   public void disconnect() {
      this.connected = false;
      this.tools = new ArrayList<>();
      if (this.requestQueue != null) {
         this.requestQueue.shutdown();
         this.requestQueue = null;
      }

      if (this.client != null) {
         try {
            this.client.closeGracefully();
         } catch (Exception var2) {
            MeteorMCPAddon.LOG.warn("Error closing MCP client: {}", var2.getMessage());
         }

         this.client = null;
      }

      this.transport = null;
      MeteorMCPAddon.LOG.info("Disconnected from MCP server: {}", this.config.getName());
   }

   public CallToolResult callTool(String toolName, Map<String, Object> arguments) {
      if (this.connected && this.requestQueue != null && this.requestQueue.isRunning()) {
         try {
            CompletableFuture<CallToolResult> future = new CompletableFuture<>();
            ToolRequest request = new ToolRequest(toolName, arguments, future);
            this.requestQueue.submitRequest(request);
            return future.get();
         } catch (ExecutionException var5) {
            MeteorMCPAddon.LOG
               .error(
                  "Error calling tool {} on server {}: {}",
                  new Object[]{toolName, this.config.getName(), var5.getCause() != null ? var5.getCause().getMessage() : var5.getMessage()}
               );
            throw new RuntimeException("Tool call failed", (Throwable)(var5.getCause() != null ? var5.getCause() : var5));
         } catch (InterruptedException var6) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Tool call interrupted", var6);
         }
      } else {
         throw new IllegalStateException("Not connected to MCP server");
      }
   }

   public boolean reconnect() {
      this.disconnect();
      return this.connect();
   }

   public MCPServerConfig getConfig() {
      return this.config;
   }

   public boolean isConnected() {
      return this.connected;
   }

   public List<Tool> getTools() {
      return new ArrayList<>(this.tools);
   }

   public MCPRequestQueue getRequestQueue() {
      return this.requestQueue;
   }

   public Tool getTool(String name) {
      return this.tools.stream().filter(tool -> tool.name().equals(name)).findFirst().orElse(null);
   }
}
