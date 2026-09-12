package com.cope.meteormcp.server;

import com.cope.meteormcp.MeteorMCPAddon;
import com.cope.meteormcp.minecraft.MinecraftToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class EmbeddedMCPServer {
   private static final int DEFAULT_PORT = 25590;
   private static HttpServer server;
   private static final ObjectMapper mapper = new ObjectMapper();

   public static synchronized void start() {
      start(DEFAULT_PORT);
   }

   public static synchronized void start(int port) {
      if (server != null) {
         MeteorMCPAddon.LOG.info("Embedded MCP server is already running on port {}", port);
         return;
      }

      try {
         server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
         server.createContext("/", new RootHandler());
         server.createContext("/tools", new ToolsHandler());
         server.createContext("/call", new CallHandler());
         server.createContext("/mcp", new JsonRpcMcpHandler());
         server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "EmbeddedMCPServer-Worker");
            t.setDaemon(true);
            return t;
         }));
         server.start();
         MeteorMCPAddon.LOG.info("Embedded MCP HTTP Server started at http://127.0.0.1:{}/", port);
      } catch (IOException e) {
         MeteorMCPAddon.LOG.error("Failed to start Embedded MCP Server on port {}: {}", port, e.getMessage());
      }
   }

   public static synchronized void stop() {
      if (server != null) {
         server.stop(1);
         server = null;
         MeteorMCPAddon.LOG.info("Embedded MCP Server stopped.");
      }
   }

   public static boolean isRunning() {
      return server != null;
   }

   private static void addCorsHeaders(HttpExchange exchange) {
      exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
      exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
      exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
   }

   private static void sendJsonResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
      addCorsHeaders(exchange);
      exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
      byte[] bytes = mapper.writeValueAsBytes(data);
      exchange.sendResponseHeaders(statusCode, bytes.length);
      try (OutputStream os = exchange.getResponseBody()) {
         os.write(bytes);
      }
   }

   private static class RootHandler implements HttpHandler {
      @Override
      public void handle(HttpExchange exchange) throws IOException {
         addCorsHeaders(exchange);
         if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
         }
         Map<String, Object> status = new LinkedHashMap<>();
         status.put("name", "Utility+ Minecraft MCP Server");
         status.put("status", "running");
         status.put("version", "1.0.0");
         status.put("toolsCount", MinecraftToolRegistry.getTools().size());
         sendJsonResponse(exchange, 200, status);
      }
   }

   private static class ToolsHandler implements HttpHandler {
      @Override
      public void handle(HttpExchange exchange) throws IOException {
         addCorsHeaders(exchange);
         if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
         }
         List<Tool> tools = MinecraftToolRegistry.getTools();
         Map<String, Object> resp = Map.of("tools", tools);
         sendJsonResponse(exchange, 200, resp);
      }
   }

   private static class CallHandler implements HttpHandler {
      @Override
      public void handle(HttpExchange exchange) throws IOException {
         addCorsHeaders(exchange);
         if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
         }
         if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJsonResponse(exchange, 405, Map.of("error", "Method not allowed. Use POST."));
            return;
         }

         try (InputStream is = exchange.getRequestBody()) {
            Map<String, Object> req = mapper.readValue(is, Map.class);
            String tool = (String) req.get("tool");
            if (tool == null || tool.isBlank()) {
               sendJsonResponse(exchange, 400, Map.of("error", "'tool' parameter is required."));
               return;
            }
            Map<String, Object> args = (Map<String, Object>) req.getOrDefault("arguments", Map.of());
            CallToolResult result = MinecraftToolRegistry.execute(tool, args);
            sendJsonResponse(exchange, 200, result);
         } catch (Exception e) {
            sendJsonResponse(exchange, 500, Map.of("error", e.getMessage()));
         }
      }
   }

   private static class JsonRpcMcpHandler implements HttpHandler {
      @Override
      public void handle(HttpExchange exchange) throws IOException {
         addCorsHeaders(exchange);
         if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
         }

         if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJsonResponse(exchange, 405, Map.of("error", "Use POST for JSON-RPC MCP requests."));
            return;
         }

         try (InputStream is = exchange.getRequestBody()) {
            Map<String, Object> rpc = mapper.readValue(is, Map.class);
            Object id = rpc.get("id");
            String method = (String) rpc.get("method");
            Map<String, Object> params = (Map<String, Object>) rpc.getOrDefault("params", Map.of());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("jsonrpc", "2.0");
            response.put("id", id);

            if ("notifications/initialized".equals(method) || "initialized".equals(method)) {
               sendJsonResponse(exchange, 204, Map.of());
               return;
            }

            if ("initialize".equals(method)) {
               Map<String, Object> res = new LinkedHashMap<>();
               res.put("protocolVersion", "2024-11-05");
               res.put("serverInfo", Map.of("name", "utility-plus-mcp", "version", "1.0.0"));
               res.put("capabilities", Map.of("tools", Map.of()));
               response.put("result", res);
            } else if ("tools/list".equals(method)) {
               response.put("result", Map.of("tools", MinecraftToolRegistry.getTools()));
            } else if ("tools/call".equals(method)) {
               String toolName = (String) params.get("name");
               Map<String, Object> args = (Map<String, Object>) params.getOrDefault("arguments", Map.of());
               CallToolResult result = MinecraftToolRegistry.execute(toolName, args);
               response.put("result", result);
            } else if ("ping".equals(method)) {
               response.put("result", Map.of());
            } else if (id == null) {
               sendJsonResponse(exchange, 204, Map.of());
               return;
            } else {
               response.put("error", Map.of("code", -32601, "message", "Method not found: " + method));
            }

            sendJsonResponse(exchange, 200, response);
         } catch (Exception e) {
            sendJsonResponse(exchange, 500, Map.of("error", e.getMessage()));
         }
      }
   }
}
