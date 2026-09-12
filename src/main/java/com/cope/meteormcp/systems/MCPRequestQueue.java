package com.cope.meteormcp.systems;

import com.cope.meteormcp.MeteorMCPAddon;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class MCPRequestQueue {
   private final String serverName;
   private final McpSyncClient client;
   private final BlockingQueue<ToolRequest> requestQueue;
   private final Thread workerThread;
   private final AtomicBoolean running;

   public MCPRequestQueue(String serverName, McpSyncClient client) {
      this.serverName = serverName;
      this.client = client;
      this.requestQueue = new LinkedBlockingQueue<>();
      this.running = new AtomicBoolean(true);
      this.workerThread = this.createWorkerThread();
      this.workerThread.start();
   }

   public void submitRequest(ToolRequest request) {
      if (!this.running.get()) {
         request.resultFuture().completeExceptionally(new IllegalStateException("Request queue is shut down"));
      } else {
         try {
            this.requestQueue.put(request);
         } catch (InterruptedException var3) {
            Thread.currentThread().interrupt();
            request.resultFuture().completeExceptionally(var3);
         }
      }
   }

   public void shutdown() {
      if (this.running.compareAndSet(true, false)) {
         this.workerThread.interrupt();

         ToolRequest request;
         while ((request = this.requestQueue.poll()) != null) {
            request.resultFuture().completeExceptionally(new IllegalStateException("Server disconnected"));
         }
      }
   }

   public int getQueueSize() {
      return this.requestQueue.size();
   }

   public boolean isRunning() {
      return this.running.get();
   }

   private Thread createWorkerThread() {
      Thread thread = new Thread(() -> {
         MeteorMCPAddon.LOG.info("MCP request queue worker started for server: {}", this.serverName);

         while (this.running.get() && !Thread.currentThread().isInterrupted()) {
            try {
               ToolRequest request = this.requestQueue.take();

               try {
                  CallToolRequest mcpRequest = new CallToolRequest(request.toolName(), request.arguments());
                  CallToolResult result = this.client.callTool(mcpRequest);
                  request.resultFuture().complete(result);
               } catch (Exception var4) {
                  MeteorMCPAddon.LOG.error("Tool execution failed for {} on {}: {}", new Object[]{request.toolName(), this.serverName, var4.getMessage()});
                  request.resultFuture().completeExceptionally(var4);
               }
            } catch (InterruptedException var5) {
               Thread.currentThread().interrupt();
               break;
            }
         }

         MeteorMCPAddon.LOG.info("MCP request queue worker stopped for server: {}", this.serverName);
      }, "MCP-Queue-" + this.serverName);
      thread.setDaemon(true);
      return thread;
   }
}
