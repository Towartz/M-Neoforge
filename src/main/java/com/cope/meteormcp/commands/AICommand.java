package com.cope.meteormcp.commands;

import com.cope.meteormcp.MeteorMCPAddon;
import com.cope.meteormcp.llm.LLMProvider;
import com.cope.meteormcp.llm.LLMProviderManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.commands.SharedSuggestionProvider;

public class AICommand extends Command {
   private static final long COOLDOWN_MS = 1000L;
   private static final Map<UUID, Long> LAST_CALL_TIME = new ConcurrentHashMap<>();

   public AICommand() {
      super("ai", "Query AI without MCP tools", new String[0]);
   }

   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      builder.then(
         argument("prompt", StringArgumentType.greedyString()).executes(context -> this.executeAI((String)context.getArgument("prompt", String.class)))
      );
      builder.executes(context -> {
         this.error("Prompt is required. Usage: .ai \"prompt\"", new Object[0]);
         return 0;
      });
   }

   int executeAI(String prompt) {
      if (prompt != null && !prompt.isBlank()) {
         LLMProviderManager manager = LLMProviderManager.getInstance();
         if (!manager.isConfigured()) {
            this.error("AI is not configured. Open Meteor GUI → MCP → Configure AI.", new Object[0]);
            return 0;
         } else if (!enforceCooldown(this)) {
            return 0;
         } else {
            runSimpleQueryAsync(this, prompt);
            return 1;
         }
      } else {
         this.error("Prompt is required. Usage: .ai \"prompt\"", new Object[0]);
         return 0;
      }
   }

   static boolean enforceCooldown(Command command) {
      if (mc.player == null) {
         return true;
      } else {
         UUID playerId = mc.player.getUUID();
         long now = System.currentTimeMillis();
         Long last = LAST_CALL_TIME.get(playerId);
         if (last != null && now - last < 1000L) {
            double waitSeconds = (double)(1000L - (now - last)) / 1000.0;
            command.warning("Please wait %.1f seconds before using this command again", new Object[]{waitSeconds});
            return false;
         } else {
            LAST_CALL_TIME.put(playerId, now);
            return true;
         }
      }
   }

   static void runSimpleQueryAsync(Command command, String prompt) {
      LLMProvider provider = LLMProviderManager.getInstance().getActiveProvider();
      command.info("Querying %s...", new Object[]{provider.name()});
      MeteorExecutor.execute(() -> {
         try {
            String response = provider.executeSimplePrompt(prompt);
            command.info(response, new Object[0]);
         } catch (Exception var4) {
            command.error("AI query failed: %s", new Object[]{safeMessage(var4)});
            MeteorMCPAddon.LOG.error("AI command failed", var4);
         }
      });
   }

   private static String safeMessage(Exception e) {
      String message = e.getMessage();
      return message != null && !message.isBlank() ? message : e.getClass().getSimpleName();
   }
}
