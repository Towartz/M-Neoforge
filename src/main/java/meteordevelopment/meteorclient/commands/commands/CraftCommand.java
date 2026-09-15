package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.List;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.screens.AutoCraftScreen;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import meteordevelopment.meteorclient.gui.tabs.builtin.AutoCraftTab;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.AutoCraft;
import meteordevelopment.meteorclient.systems.modules.player.autocraft.CraftItemResolver;
import meteordevelopment.meteorclient.systems.modules.player.autocraft.CraftPlanner;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class CraftCommand extends Command {
   public CraftCommand() {
      super("craft", "Automatically crafts items or sets with chest search support.", "autocraft");
   }

   @Override
   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      // .craft gui / ui / open
      builder.then(literal("gui").executes(context -> {
         Tab tab = Tabs.get().stream().filter(t -> t instanceof AutoCraftTab).findFirst().orElse(null);
         if (tab != null) tab.openScreen(GuiThemes.get());
         else mc.setScreen(new AutoCraftScreen(GuiThemes.get(), Modules.get().get(AutoCraft.class)));
         return 1;
      }));
      builder.then(literal("ui").executes(context -> {
         Tab tab = Tabs.get().stream().filter(t -> t instanceof AutoCraftTab).findFirst().orElse(null);
         if (tab != null) tab.openScreen(GuiThemes.get());
         else mc.setScreen(new AutoCraftScreen(GuiThemes.get(), Modules.get().get(AutoCraft.class)));
         return 1;
      }));
      builder.then(literal("open").executes(context -> {
         Tab tab = Tabs.get().stream().filter(t -> t instanceof AutoCraftTab).findFirst().orElse(null);
         if (tab != null) tab.openScreen(GuiThemes.get());
         else mc.setScreen(new AutoCraftScreen(GuiThemes.get(), Modules.get().get(AutoCraft.class)));
         return 1;
      }));

      // .craft cancel
      builder.then(literal("cancel").executes(context -> {
         AutoCraft autoCraft = Modules.get().get(AutoCraft.class);
         if (autoCraft != null) {
            autoCraft.cancelTask();
            this.info("AutoCraft task cancelled.");
         }
         return 1;
      }));

      // .craft status
      builder.then(literal("status").executes(context -> {
         AutoCraft autoCraft = Modules.get().get(AutoCraft.class);
         if (autoCraft != null) {
            AutoCraft.CraftTask task = autoCraft.getCurrentTask();
            if (task != null) {
               this.info("Active Task: (highlight)%s(default), Remaining: (highlight)%d(default), State: (highlight)%s(default), Queue: (highlight)%d(default)",
                  task.item.getDescription().getString(), task.remainingCount, autoCraft.getCraftState(), autoCraft.getQueueSize());
            } else {
               this.info("AutoCraft is currently idle (queue: %d).", autoCraft.getQueueSize());
            }
         }
         return 1;
      }));

      // .craft list
      builder.then(literal("list").executes(context -> {
         AutoCraft autoCraft = Modules.get().get(AutoCraft.class);
         if (autoCraft != null) {
            this.info("Pending tasks in queue: (highlight)%d(default).", autoCraft.getQueueSize());
         }
         return 1;
      }));

      // .craft plan <item> [count]
      builder.then(literal("plan")
         .then(((RequiredArgumentBuilder)argument("item", ItemArgument.item(REGISTRY_ACCESS))
            .executes(context -> {
               ItemStack stack = ItemArgument.getItem(context, "item").createItemStack(1, false);
               executeDryRun(stack.getItem(), 1);
               return 1;
            }))
            .then(argument("count", IntegerArgumentType.integer(1)).executes(context -> {
               ItemStack stack = ItemArgument.getItem(context, "item").createItemStack(1, false);
               int count = IntegerArgumentType.getInteger(context, "count");
               executeDryRun(stack.getItem(), count);
               return 1;
            }))
         )
      );

      // .craft armor <material> [count]
      builder.then(literal("armor")
         .then(((RequiredArgumentBuilder)argument("material", StringArgumentType.word())
            .executes(context -> {
               String material = StringArgumentType.getString(context, "material");
               List<Item> armor = CraftItemResolver.getArmorSet(material);
               if (armor.isEmpty()) {
                  this.error("Could not resolve armor set for material: %s", material);
                  return 0;
               }
               AutoCraft autoCraft = Modules.get().get(AutoCraft.class);
               autoCraft.queueBundle(armor, 1);
               this.info("Queued (highlight)%s(default) armor set (4 pieces).", material);
               return 1;
            }))
            .then(argument("count", IntegerArgumentType.integer(1)).executes(context -> {
               String material = StringArgumentType.getString(context, "material");
               int count = IntegerArgumentType.getInteger(context, "count");
               List<Item> armor = CraftItemResolver.getArmorSet(material);
               if (armor.isEmpty()) {
                  this.error("Could not resolve armor set for material: %s", material);
                  return 0;
               }
               AutoCraft autoCraft = Modules.get().get(AutoCraft.class);
               autoCraft.queueBundle(armor, count);
               this.info("Queued %dx (highlight)%s(default) armor set.", count, material);
               return 1;
            }))
         )
      );

      // .craft tools <material> [count]
      builder.then(literal("tools")
         .then(((RequiredArgumentBuilder)argument("material", StringArgumentType.word())
            .executes(context -> {
               String material = StringArgumentType.getString(context, "material");
               List<Item> tools = CraftItemResolver.getToolsSet(material);
               if (tools.isEmpty()) {
                  this.error("Could not resolve tools set for material: %s", material);
                  return 0;
               }
               AutoCraft autoCraft = Modules.get().get(AutoCraft.class);
               autoCraft.queueBundle(tools, 1);
               this.info("Queued (highlight)%s(default) tools set (5 pieces).", material);
               return 1;
            }))
            .then(argument("count", IntegerArgumentType.integer(1)).executes(context -> {
               String material = StringArgumentType.getString(context, "material");
               int count = IntegerArgumentType.getInteger(context, "count");
               List<Item> tools = CraftItemResolver.getToolsSet(material);
               if (tools.isEmpty()) {
                  this.error("Could not resolve tools set for material: %s", material);
                  return 0;
               }
               AutoCraft autoCraft = Modules.get().get(AutoCraft.class);
               autoCraft.queueBundle(tools, count);
               this.info("Queued %dx (highlight)%s(default) tools set.", count, material);
               return 1;
            }))
         )
      );

      // .craft <item> [count]
      builder.then(((RequiredArgumentBuilder)argument("item", ItemArgument.item(REGISTRY_ACCESS))
         .executes(context -> {
            ItemStack stack = ItemArgument.getItem(context, "item").createItemStack(1, false);
            AutoCraft autoCraft = Modules.get().get(AutoCraft.class);
            autoCraft.queueCraft(stack.getItem(), 1);
            return 1;
         }))
         .then(argument("count", IntegerArgumentType.integer(1)).executes(context -> {
            ItemStack stack = ItemArgument.getItem(context, "item").createItemStack(1, false);
            int count = IntegerArgumentType.getInteger(context, "count");
            AutoCraft autoCraft = Modules.get().get(AutoCraft.class);
            autoCraft.queueCraft(stack.getItem(), count);
            return 1;
         }))
      );
   }

   private void executeDryRun(Item item, int count) {
      if (item == null || count <= 0) return;
      this.info("Calculating craft plan for (highlight)%dx %s(default)...", count, item.getDescription().getString());

      CraftPlanner.CraftPlan plan = CraftPlanner.createPlan(item, count);
      if (plan == null) {
         this.error("No recipe found or unable to create plan for %s.", item.getDescription().getString());
         return;
      }

      this.info("=== Plan for %dx %s ===", count, item.getDescription().getString());
      this.info("Craftable with current inventory/backpack: (highlight)%s(default)", plan.isSatisfied ? "YES" : "NO");
      this.info("Requires Crafting Table: (highlight)%s(default)", plan.requires3x3Table() ? "YES (3x3)" : "NO (2x2)");

      if (!plan.steps.isEmpty()) {
         this.info("Crafting Steps (%d):", plan.steps.size());
         for (int i = 0; i < plan.steps.size(); i++) {
            CraftPlanner.CraftStep step = plan.steps.get(i);
            this.info("  %d. Craft %dx (highlight)%s(default)",
               i + 1, step.deficitNeeded, step.resultItem.getDescription().getString());
         }
      } else {
         this.info("No crafting steps needed (item already in inventory or no recipe).");
      }

      if (!plan.rawMaterialsNeeded.isEmpty()) {
         this.info("Raw Materials Consumed:");
         plan.rawMaterialsNeeded.forEach((mat, amt) -> {
            this.info("  - %dx (highlight)%s(default)", amt, mat.getDescription().getString());
         });
      }

      if (!plan.missingRawMaterials.isEmpty()) {
         this.info("Missing Materials:");
         plan.missingRawMaterials.forEach((missingItem, needed) -> {
            this.info("  - %dx (highlight)%s(default)", needed, missingItem.getDescription().getString());
         });
      }
      this.info("========================");
   }
}
