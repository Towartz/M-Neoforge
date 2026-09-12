package com.cope.meteormcp.commands;

import com.cope.meteormcp.MeteorMCPAddon;
import com.cope.meteormcp.systems.MCPServerConnection;
import com.cope.meteormcp.systems.MCPServers;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.commands.SharedSuggestionProvider;

public class MCPToolCommand extends Command {
   private final String serverName;
   private final String toolName;
   private final Tool toolSchema;

   public MCPToolCommand(String serverName, Tool toolSchema) {
      super(serverName + ":" + toolSchema.name(), Objects.requireNonNullElse(toolSchema.description(), "MCP Tool: " + toolSchema.name()), new String[0]);
      this.serverName = serverName;
      this.toolName = toolSchema.name();
      this.toolSchema = toolSchema;
   }

   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      builder.then(literal("help").executes(context -> {
         this.showHelp();
         return 1;
      }));
      builder.then(
         argument("args", StringArgumentType.greedyString())
            .suggests(this::suggestArguments)
            .executes(context -> this.executeTool((String)context.getArgument("args", String.class)))
      );
      builder.executes(context -> this.executeTool(""));
   }

   private int executeTool(String argsString) {
      MCPServerConnection connection = MCPServers.get().getConnection(this.serverName);
      if (connection != null && connection.isConnected()) {
         Map<String, Object> arguments;
         try {
            arguments = CommandUtils.parseArguments(argsString, this.toolSchema);
         } catch (IllegalArgumentException var5) {
            this.error("Argument parsing failed: {}", new Object[]{var5.getMessage()});
            return 0;
         } catch (Exception var6) {
            this.error("Argument parsing failed.", new Object[0]);
            MeteorMCPAddon.LOG.error("Failed to parse arguments for {}/{}: {}", new Object[]{this.serverName, this.toolName, var6.getMessage()});
            return 0;
         }

         if (!CommandUtils.validateRequiredParams(arguments, this.toolSchema)) {
            this.error("Missing required parameters. Usage: /{} {}", new Object[]{this.getName(), CommandUtils.generateUsage(this.toolSchema)});
            return 0;
         } else {
            Map<String, Object> callArgs = new LinkedHashMap<>(arguments);
            this.info("Executing %s:%s...", new Object[]{this.serverName, this.toolName});
            MeteorExecutor.execute(() -> {
               try {
                  CallToolResult result = connection.callTool(this.toolName, callArgs);
                  Runnable deliver = () -> CommandUtils.displayToolResult(this, result);
                  if (mc != null) {
                     mc.execute(deliver);
                  } else {
                     deliver.run();
                  }
               } catch (Exception var5x) {
                  MeteorMCPAddon.LOG.error("MCP tool command {}:{} failed", new Object[]{this.serverName, this.toolName, var5x});
                  Runnable fail = () -> this.error("Tool execution failed: {}", new Object[]{this.safeMessage(var5x)});
                  if (mc != null) {
                     mc.execute(fail);
                  } else {
                     fail.run();
                  }
               }
            });
            return 1;
         }
      } else {
         this.error("Server '{}' is not connected.", new Object[]{this.serverName});
         return 0;
      }
   }

   private CompletableFuture<Suggestions> suggestArguments(SuggestionsBuilder builder) {
      JsonSchema schema = this.toolSchema.inputSchema();
      Map<String, Object> properties = schema != null ? schema.properties() : null;
      if (properties != null && !properties.isEmpty()) {
         String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);

         for (String param : properties.keySet()) {
            String suggestion = param + "=";
            if (remaining.isEmpty() || suggestion.toLowerCase(Locale.ROOT).startsWith(remaining)) {
               builder.suggest(suggestion);
            }
         }

         return builder.buildFuture();
      } else {
         return builder.buildFuture();
      }
   }

   private CompletableFuture<Suggestions> suggestArguments(CommandContext<SharedSuggestionProvider> context, SuggestionsBuilder builder) {
      return this.suggestArguments(builder);
   }

   private void showHelp() {
      String description = this.toolSchema.description();
      if (description == null || description.isBlank()) {
         description = "No description provided.";
      }

      this.info("{} - {}", new Object[]{this.getName(), description});
      this.info("Usage: /{} {}", new Object[]{this.getName(), CommandUtils.generateUsage(this.toolSchema)});
      JsonSchema schema = this.toolSchema.inputSchema();
      Map<String, Object> properties = schema != null ? schema.properties() : null;
      if (properties != null && !properties.isEmpty()) {
         this.info("Parameters:", new Object[0]);

         for (Entry<String, Object> entry : properties.entrySet()) {
            String name = entry.getKey();
            String type = this.extractType(entry.getValue());
            boolean required = schema.required() != null && schema.required().contains(name);
            String flag = required ? "*" : "-";
            this.info("  {} {} ({}) {}", new Object[]{flag, name, type, this.extractDescription(entry.getValue())});
         }

         if (schema.required() != null && !schema.required().isEmpty()) {
            this.info("* indicates required parameter.", new Object[0]);
         }
      }
   }

   private String extractDescription(Object schema) {
      if (schema instanceof Map<?, ?> map) {
         Object description = map.get("description");
         if (description != null) {
            return description.toString();
         }
      }

      return "No description available.";
   }

   private String extractType(Object schema) {
      if (schema instanceof Map<?, ?> map) {
         Object type = map.get("type");
         if (type != null) {
            return type.toString();
         }
      }

      return "any";
   }

   private String safeMessage(Exception e) {
      String message = e.getMessage();
      return message != null && !message.isBlank() ? message : e.getClass().getSimpleName();
   }
}
