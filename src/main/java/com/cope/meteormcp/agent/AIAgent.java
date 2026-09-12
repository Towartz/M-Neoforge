package com.cope.meteormcp.agent;

import baritone.api.IBaritone;
import com.cope.meteormcp.llm.LLMProvider;
import com.cope.meteormcp.llm.LLMProviderManager;
import com.cope.meteormcp.minecraft.MinecraftToolContext;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.Set;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;

public class AIAgent extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

   private final Setting<String> goal = this.sgGeneral.add(
      new StringSetting.Builder()
         .name("goal")
         .description("Autonomous goal for the AI Agent to execute.")
         .defaultValue("mine 5 iron_ore and craft an iron_pickaxe")
         .wide()
         .build()
   );

   private final Setting<Integer> maxSteps = this.sgGeneral.add(
      new IntSetting.Builder()
         .name("max-steps")
         .description("Maximum planning iterations/steps before stopping.")
         .defaultValue(16)
         .min(1)
         .sliderMax(32)
         .build()
   );

   private final Setting<Integer> stepDelay = this.sgGeneral.add(
      new IntSetting.Builder()
         .name("step-delay-ms")
         .description("Delay in milliseconds between agent steps.")
         .defaultValue(1500)
         .min(200)
         .sliderMax(5000)
         .build()
   );

   private Thread agentThread;
   private volatile boolean running;

   public AIAgent() {
      super(Categories.Misc, "ai-agent", "Autonomous AI agent that plans and executes multi-step Minecraft goals using MCP tools.");
   }

   public String getGoal() {
      return this.goal.get();
   }

   public void setGoal(String newGoal) {
      this.goal.set(newGoal);
   }

   @Override
   public void onActivate() {
      LLMProvider provider = LLMProviderManager.getInstance().getActiveProvider();
      if (provider == null || !provider.isConfigured()) {
         ChatUtils.errorPrefix("AI Agent", "No AI provider configured. Set up Gemini or Ollama in MCP settings.");
         this.toggle();
         return;
      }

      final String currentGoal = this.goal.get();
      final int steps = this.maxSteps.get();
      final int delay = this.stepDelay.get();

      ChatUtils.infoPrefix("AI Agent", "Starting autonomous task: (highlight)%s(default)", currentGoal);
      this.running = true;

      this.agentThread = new Thread(() -> {
         try {
            int step = 1;
            while (this.running && step <= steps) {
               // Retrieve perception and inventory status
               CallToolResult statusResult = MinecraftToolContext.runOnClientSync(() -> 
                  com.cope.meteormcp.minecraft.MinecraftToolRegistry.execute("get_player_status", java.util.Map.of())
               );
               CallToolResult invResult = MinecraftToolContext.runOnClientSync(() -> 
                  com.cope.meteormcp.minecraft.MinecraftToolRegistry.execute("get_inventory", java.util.Map.of())
               );

               String statusText = statusResult != null && !statusResult.content().isEmpty() 
                  ? ((io.modelcontextprotocol.spec.McpSchema.TextContent) statusResult.content().get(0)).text() 
                  : "{}";
               String invText = invResult != null && !invResult.content().isEmpty() 
                  ? ((io.modelcontextprotocol.spec.McpSchema.TextContent) invResult.content().get(0)).text() 
                  : "{}";

               StringBuilder prompt = new StringBuilder();
               prompt.append("You are an autonomous AI Agent playing Minecraft.\n");
               prompt.append("Overall Goal: ").append(currentGoal).append("\n");
               prompt.append("Step ").append(step).append(" of ").append(steps).append(".\n");
               prompt.append("Current Player Status: ").append(statusText).append("\n");
               prompt.append("Current Inventory: ").append(invText).append("\n\n");
               prompt.append("Decide what action/tool to call next using available MCP tools in the 'minecraft' server. ");
               prompt.append("If the goal is completely finished, respond that the task is complete and summarize what was accomplished.");

               LLMProvider currentProvider = LLMProviderManager.getInstance().getActiveProvider();
               if (currentProvider == null) break;

               LLMProvider.MCPResult result = currentProvider.executeWithMCPTools(prompt.toString(), Set.of("minecraft"));
               if (result != null) {
                  if (result.toolCalls() != null && !result.toolCalls().isEmpty()) {
                     for (LLMProvider.ToolCallInfo info : result.toolCalls()) {
                        ChatUtils.infoPrefix("AI Agent", "Executed tool: (highlight)%s(default)", info.toolName());
                     }
                  }

                  String resp = result.response() != null ? result.response().trim() : "";
                  if (!resp.isEmpty()) {
                     ChatUtils.infoPrefix("AI Agent", "[Step %d] %s", step, resp);
                  }

                  if (resp.toLowerCase().contains("task is complete") || resp.toLowerCase().contains("goal is finished") || resp.toLowerCase().contains("goal completed")) {
                     ChatUtils.infoPrefix("AI Agent", "Task accomplished successfully!");
                     break;
                  }
               }

               step++;
               if (this.running && step <= steps) {
                  Thread.sleep(delay);
               }
            }
         } catch (InterruptedException ignored) {
         } catch (Exception e) {
            ChatUtils.errorPrefix("AI Agent", "Agent error: %s", e.getMessage());
         } finally {
            this.running = false;
            if (this.isActive()) {
               this.mc.execute(this::toggle);
            }
            ChatUtils.infoPrefix("AI Agent", "Agent finished task.");
         }
      }, "AIAgent-Thread");

      this.agentThread.setDaemon(true);
      this.agentThread.start();
   }

   @Override
   public void onDeactivate() {
      this.running = false;
      if (this.agentThread != null && this.agentThread.isAlive()) {
         this.agentThread.interrupt();
         this.agentThread = null;
      }

      IBaritone baritone = MinecraftToolContext.getBaritone();
      if (baritone != null) {
         MinecraftToolContext.runOnClient(() -> {
            baritone.getPathingBehavior().cancelEverything();
            baritone.getPathingBehavior().forceCancel();
         });
      }
   }
}
