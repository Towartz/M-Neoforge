package com.cope.meteormcp.commands;

import com.cope.meteormcp.agent.AIAgent;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.commands.SharedSuggestionProvider;

public class AITaskCommand extends Command {
   public AITaskCommand() {
      super("ai-task", "Assigns an autonomous goal to the AI Agent.", "aitask", "agent");
   }

   @Override
   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      builder.then(literal("cancel").executes(context -> {
         AIAgent agent = Modules.get().get(AIAgent.class);
         if (agent != null && agent.isActive()) {
            agent.toggle();
            this.info("AI Agent task cancelled.");
         } else {
            this.info("AI Agent is not currently active.");
         }
         return 1;
      }));

      builder.then(literal("status").executes(context -> {
         AIAgent agent = Modules.get().get(AIAgent.class);
         if (agent != null) {
            this.info("AI Agent status: %s, Goal: %s", agent.isActive() ? "ACTIVE" : "IDLE", agent.getGoal());
         }
         return 1;
      }));

      builder.then(argument("goal", StringArgumentType.greedyString()).executes(context -> {
         String goal = StringArgumentType.getString(context, "goal");
         AIAgent agent = Modules.get().get(AIAgent.class);
         if (agent != null) {
            agent.setGoal(goal);
            if (!agent.isActive()) {
               agent.toggle();
            }
            this.info("Started AI Agent with goal: (highlight)%s(default)", goal);
         }
         return 1;
      }));
   }
}
