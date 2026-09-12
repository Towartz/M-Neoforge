package com.cope.meteormcp.commands;

import com.cope.meteormcp.MeteorMCPAddon;
import com.cope.meteormcp.llm.LLMProvider;
import com.cope.meteormcp.llm.LLMProviderManager;
import com.cope.meteormcp.systems.MCPServerConnection;
import com.cope.meteormcp.systems.MCPServers;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.commands.SharedSuggestionProvider;

public class AIMCPCommand extends Command {
   public AIMCPCommand() {
      super("ai-mcp", "Query AI with access to all connected MCP tools", new String[0]);
   }

   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      builder.then(
         argument("prompt", StringArgumentType.greedyString()).executes(context -> this.executeAIMCP((String)context.getArgument("prompt", String.class)))
      );
      builder.executes(context -> {
         this.error("Prompt is required. Usage: .ai-mcp \"prompt\"", new Object[0]);
         return 0;
      });
   }

   private int executeAIMCP(String prompt) {
      if (prompt != null && !prompt.isBlank()) {
         LLMProviderManager manager = LLMProviderManager.getInstance();
         if (!manager.isConfigured()) {
            this.error("AI is not configured. Open Meteor GUI → MCP → Configure AI.", new Object[0]);
            return 0;
         } else if (!AICommand.enforceCooldown(this)) {
            return 0;
         } else {
            Set<String> connectedServers = MCPServers.get()
               .getAllConnections()
               .stream()
               .filter(MCPServerConnection::isConnected)
               .map(connection -> connection.getConfig().getName())
               .collect(Collectors.toCollection(LinkedHashSet::new));
            if (connectedServers.isEmpty()) {
               this.warning("No MCP servers connected. Running simple AI query.", new Object[0]);
               AICommand.runSimpleQueryAsync(this, prompt);
               return 1;
            } else {
               LLMProvider provider = manager.getActiveProvider();
               this.info("Querying %s with %d MCP server(s)...", new Object[]{provider.name(), connectedServers.size()});
               MeteorExecutor.execute(() -> {
                  try {
                     LLMProvider.MCPResult result = provider.executeWithMCPTools(prompt, connectedServers);
                     this.info(result.response(), new Object[0]);
                     this.displayToolUsage(result.toolCalls());
                  } catch (Exception var5) {
                     this.error("AI MCP query failed: %s", new Object[]{safeMessage(var5)});
                     MeteorMCPAddon.LOG.error("AI MCP command failed", var5);
                  }
               });
               return 1;
            }
         }
      } else {
         this.error("Prompt is required. Usage: .ai-mcp \"prompt\"", new Object[0]);
         return 0;
      }
   }

   private static String safeMessage(Exception e) {
      String message = e.getMessage();
      return message != null && !message.isBlank() ? message : e.getClass().getSimpleName();
   }

   private void displayToolUsage(List<LLMProvider.ToolCallInfo> toolCalls) {
      if (toolCalls != null && !toolCalls.isEmpty()) {
         StringBuilder builder = new StringBuilder("[Tools Used] ");

         for (int i = 0; i < toolCalls.size(); i++) {
            LLMProvider.ToolCallInfo call = toolCalls.get(i);
            if (i > 0) {
               builder.append(", ");
            }

            builder.append(call.serverName()).append(":").append(call.toolName());
            List<String> annotations = new ArrayList<>();
            if (call.durationMs() > 0L) {
               annotations.add(call.durationMs() + "ms");
            }

            if (!call.success()) {
               annotations.add("failed");
            }

            if (!annotations.isEmpty()) {
               builder.append(" (").append(String.join(", ", annotations)).append(")");
            }

            if (!call.success() && call.errorMessage() != null && !call.errorMessage().isBlank()) {
               builder.append(" - ").append(call.errorMessage());
            }
         }

         this.info(builder.toString(), new Object[0]);
      }
   }
}
