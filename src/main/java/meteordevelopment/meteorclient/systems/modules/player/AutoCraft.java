package meteordevelopment.meteorclient.systems.modules.player;

import baritone.api.pathing.goals.GoalGetToBlock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.screens.AutoCraftScreen;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import meteordevelopment.meteorclient.gui.tabs.builtin.AutoCraftTab;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.player.autocraft.BackpackAdapter;
import meteordevelopment.meteorclient.systems.modules.player.autocraft.ContainerSearcher;
import meteordevelopment.meteorclient.systems.modules.player.autocraft.CraftItemResolver;
import meteordevelopment.meteorclient.systems.modules.player.autocraft.CraftPlanner;
import meteordevelopment.meteorclient.systems.modules.player.autocraft.CraftRecipeHelper;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class AutoCraft extends Module {
   public enum State {
      IDLE,
      RESOLVING,
      OPENING_BACKPACK,
      WITHDRAWING_FROM_BACKPACK,
      OFFLOADING_TO_BACKPACK,
      NAVIGATING_TO_CHEST,
      LOOTING_CHEST,
      CRAFTING_TABLE_PLACING,
      NAVIGATING_TO_TABLE,
      OPENING_TABLE,
      CRAFTING,
      WAITING_FOR_CRAFT_RESULT,
      SETTLING,
      CLEANUP
   }

   public enum GridPlaceResult {
      SUCCESS,
      INGREDIENTS_DEPLETED,
      BLOCKED
   }

   public static class CraftTask {
      public final Item item;
      public final int initialCount;
      public int remainingCount;

      public CraftTask(Item item, int count) {
         this.item = item;
         this.initialCount = count;
         this.remainingCount = count;
      }
   }

   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgBackpack = this.settings.createGroup("Backpack & Portable");
   private final SettingGroup sgChest = this.settings.createGroup("Chest Retrieval");
   private final SettingGroup sgTable = this.settings.createGroup("Crafting Table");

   // Backpack Settings
   private final Setting<Boolean> preferBackpack = this.sgBackpack.add(
      new BoolSetting.Builder()
         .name("prefer-portable-backpack")
         .description("Use equipped or held backpack portable crafting table instead of placing a block.")
         .defaultValue(true)
         .build()
   );

   // General Settings
   private final Setting<Integer> craftDelay = this.sgGeneral.add(
      new IntSetting.Builder()
         .name("craft-delay")
         .description("Tick delay between craft operations.")
         .defaultValue(2)
         .min(0)
         .sliderMax(20)
         .build()
   );
   private final Setting<Boolean> recursiveCrafting = this.sgGeneral.add(
      new BoolSetting.Builder()
         .name("recursive-crafting")
         .description("Automatically craft intermediate sub-ingredients (e.g. logs -> planks -> sticks).")
         .defaultValue(true)
         .build()
   );
   private final Setting<Boolean> closeWhenDone = this.sgGeneral.add(
      new BoolSetting.Builder()
         .name("close-when-done")
         .description("Close container and crafting screens when crafting completes.")
         .defaultValue(true)
         .build()
   );

   // Chest Retrieval Settings
   private final Setting<Boolean> searchChests = this.sgChest.add(
      new BoolSetting.Builder()
         .name("search-chests")
         .description("Search nearby chests and containers if materials are missing from inventory.")
         .defaultValue(false)
         .build()
   );
   private final Setting<Integer> chestRadius = this.sgChest.add(
      new IntSetting.Builder()
         .name("chest-radius")
         .description("Maximum block radius to search for chests.")
         .defaultValue(24)
         .min(4)
         .sliderMax(64)
         .build()
   );
   private final Setting<Boolean> autoWalk = this.sgChest.add(
      new BoolSetting.Builder()
         .name("auto-walk")
         .description("Automatically walk to chests and crafting tables using Baritone.")
         .defaultValue(true)
         .build()
   );

   // Crafting Table Settings
   private final Setting<Boolean> autoCraftingTable = this.sgTable.add(
      new BoolSetting.Builder()
         .name("auto-place-table")
         .description("Automatically place a Crafting Table when needed for 3x3 recipes.")
         .defaultValue(true)
         .build()
   );
   private final Setting<Boolean> breakPlacedTable = this.sgTable.add(
      new BoolSetting.Builder()
         .name("break-placed-table")
         .description("Mine and recover the placed Crafting Table after crafting completes.")
         .defaultValue(true)
         .build()
   );

   private final ContainerSearcher containerSearcher = new ContainerSearcher();
   private final List<CraftTask> queue = new ArrayList<>();
   private CraftTask currentTask = null;
   private State state = State.IDLE;
   private int timer = 0;
   private BlockPos targetChestPos = null;
   private BlockPos tablePos = null;
   private BlockPos placedTablePos = null;
   private RecipeHolder<CraftingRecipe> activeRecipe = null;
   private int chestWaitTicks = 0;
   private int backpackTabWaitTicks = 0;
   private int navWaitTicks = 0;
   private int craftRetryTicks = 0;
   private int gridResultWaitTicks = 0;
   private int consecutiveExhaustions = 0;
   private Item pendingCraftItem = null;
   private int pendingExpectedYield = 0;
   private int pendingBeforeDirectCount = 0;
   private int pendingBeforePoolCount = 0;
   private int pendingSlot = -1;
   private int pendingWaitTicks = 0;
   private boolean pendingIsBackpack = false;
   private int settleWaitTicks = 0;

   public AutoCraft() {
      super(Categories.Player, "auto-craft", "Automatically crafts items with dynamic mod resolution and chest search.");
   }

   @Override
   public void onDeactivate() {
      this.cancelTask();
   }

   @EventHandler
   private void onGameLeft(meteordevelopment.meteorclient.events.game.GameLeftEvent event) {
      this.cancelTask();
   }

   public void cancelTask() {
      this.queue.clear();
      this.currentTask = null;
      this.state = State.IDLE;
      this.targetChestPos = null;
      this.tablePos = null;
      this.placedTablePos = null;
      this.activeRecipe = null;
      this.chestWaitTicks = 0;
      this.backpackTabWaitTicks = 0;
      this.navWaitTicks = 0;
      this.craftRetryTicks = 0;
      this.gridResultWaitTicks = 0;
      this.consecutiveExhaustions = 0;
      this.settleWaitTicks = 0;
      clearPendingCraft();
      ContainerSearcher.stopNavigation();
      this.containerSearcher.reset();
   }

   private void clearPendingCraft() {
      this.pendingCraftItem = null;
      this.pendingExpectedYield = 0;
      this.pendingBeforeDirectCount = 0;
      this.pendingBeforePoolCount = 0;
      this.pendingSlot = -1;
      this.pendingWaitTicks = 0;
      this.pendingIsBackpack = false;
   }

   public void queueCraft(Item item, int count) {
      if (item == null || count <= 0) return;
      this.consecutiveExhaustions = 0;
      this.queue.add(new CraftTask(item, count));
      if (!this.isActive()) {
         this.toggle();
      }
      if (this.state == State.IDLE) {
         this.state = State.RESOLVING;
         this.timer = 0;
      }
      this.info("Queued craft: (highlight)%dx %s(default).", count, item.getDescription().getString());
   }

   public void queueBundle(List<Item> items, int count) {
      if (items == null || items.isEmpty()) return;
      this.consecutiveExhaustions = 0;
      for (Item item : items) {
         this.queue.add(new CraftTask(item, count));
      }
      if (!this.isActive()) {
         this.toggle();
      }
      if (this.state == State.IDLE) {
         this.state = State.RESOLVING;
         this.timer = 0;
      }
      this.info("Queued bundle with (highlight)%d(default) items.", items.size());
   }

   public State getCraftState() {
      return this.state;
   }

   public CraftTask getCurrentTask() {
      return this.currentTask;
   }

   public int getQueueSize() {
      return this.queue.size();
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.player == null || this.mc.level == null) return;

      if (this.timer > 0) {
         this.timer--;
         return;
      }

      switch (this.state) {
         case IDLE -> {
            if (!this.queue.isEmpty()) {
               this.state = State.RESOLVING;
            }
         }
         case RESOLVING -> handleResolving();
         case OPENING_BACKPACK -> handleOpeningBackpack();
         case WITHDRAWING_FROM_BACKPACK -> handleWithdrawingFromBackpack();
         case OFFLOADING_TO_BACKPACK -> handleOffloadingToBackpack();
         case NAVIGATING_TO_CHEST -> handleNavigatingToChest();
         case LOOTING_CHEST -> handleLootingChest();
         case CRAFTING_TABLE_PLACING -> handlePlacingTable();
         case NAVIGATING_TO_TABLE -> handleNavigatingToTable();
         case OPENING_TABLE -> handleOpeningTable();
         case CRAFTING -> handleCrafting();
         case WAITING_FOR_CRAFT_RESULT -> handleWaitingForCraftResult();
         case SETTLING -> handleSettling();
         case CLEANUP -> handleCleanup();
      }
   }

   private void handleOpeningBackpack() {
      if (BackpackAdapter.isBackpackMenu(this.mc.player.containerMenu)) {
         AbstractContainerMenu menu = this.mc.player.containerMenu;
         BackpackAdapter.BackpackCraftInfo info = BackpackAdapter.getBackpackCraftInfo(menu);

         int yield = CraftRecipeHelper.getResultCount(this.activeRecipe);
         int craftsNeeded = (int) Math.ceil((double) this.currentTask.remainingCount / yield);
         boolean hasDirect = CraftRecipeHelper.hasAllInDirectInventory(this.activeRecipe, craftsNeeded);

         // 1. If backpack already has an active 3x3 crafting grid, use it directly
         if (info.valid && info.resultSlot != -1) {
            this.backpackTabWaitTicks = 0;
            this.state = State.CRAFTING;
            this.timer = this.craftDelay.get();
            return;
         }

         // 2. If backpack has a CraftingUpgrade, automatically open the crafting tab!
         if (BackpackAdapter.hasCraftingUpgrade(menu)) {
            if (!BackpackAdapter.isCraftingTabOpen(menu)) {
               this.info("Crafting tab closed. Automatically opening backpack crafting tab...");
               BackpackAdapter.openCraftingTab(menu);
               this.timer = this.craftDelay.get() + 3;
               return;
            } else {
               // Tab is marked open, waiting briefly for slot repositioning sync (up to 5 checks = 10 ticks)
               this.backpackTabWaitTicks++;
               if (this.backpackTabWaitTicks <= 5) {
                  this.timer = 2;
                  return;
               }
               this.warning("Backpack crafting grid slots not detected in time. Falling back to inventory/table.");
            }
         }

         // 3. Storage-only backpack (or fallback):
         // If direct inventory already has all materials, close backpack and proceed to craft
         if (hasDirect) {
            this.mc.player.closeContainer();
            this.timer = this.craftDelay.get() + 2;
            this.state = State.RESOLVING;
            return;
         }

         // Storage-only backpack: withdraw required materials into player inventory
         this.state = State.WITHDRAWING_FROM_BACKPACK;
         this.timer = this.craftDelay.get();
         this.chestWaitTicks = 0;
         this.backpackTabWaitTicks = 0;
         return;
      }

      this.chestWaitTicks++;
      if (this.chestWaitTicks > 25) {
         this.error("Failed to open backpack (timed out).");
         this.cancelTask();
      }
   }

   private void handleWithdrawingFromBackpack() {
      if (this.currentTask == null || this.activeRecipe == null) {
         this.state = State.RESOLVING;
         return;
      }

      AbstractContainerMenu menu = this.mc.player.containerMenu;
      if (!BackpackAdapter.isBackpackMenu(menu)) {
         // Backpack closed or changed unexpectedly
         this.state = State.RESOLVING;
         return;
      }

      int yield = CraftRecipeHelper.getResultCount(this.activeRecipe);
      int craftsNeeded = (int) Math.ceil((double) this.currentTask.remainingCount / yield);
      Map<Item, Integer> neededInDirect = CraftRecipeHelper.getMissingFromDirectInventory(this.activeRecipe, craftsNeeded);

      if (neededInDirect.isEmpty()) {
         // All needed materials are in player direct inventory!
         this.mc.player.closeContainer();
         this.timer = this.craftDelay.get() + 2;
         this.state = State.RESOLVING;
         this.info("Retrieved materials from backpack. Proceeding to craft...");
         return;
      }

      int emptySlots = CraftRecipeHelper.getEmptyInventorySlots();
      int directCrafts = CraftRecipeHelper.getDirectCraftsPossible(this.activeRecipe);

      // Reserve at least 1 slot for crafting output unless direct crafts are possible
      if (emptySlots <= 1) {
         if (directCrafts > 0) {
            this.info("Inventory full, but enough materials for %d craft(s). Proceeding to craft...", directCrafts);
            this.mc.player.closeContainer();
            this.timer = this.craftDelay.get() + 2;
            this.state = State.RESOLVING;
            return;
         }

         // Try to offload non-essential items into backpack storage to make room
         int freed = BackpackAdapter.depositExcept(menu, neededInDirect.keySet());
         if (freed > 0) {
            this.info("Offloaded %d items to backpack to make inventory room.", freed);
            this.timer = this.craftDelay.get() + 2;
            return;
         }
      }

      BackpackAdapter.BackpackCraftInfo info = BackpackAdapter.getBackpackCraftInfo(menu);
      int storageStart = info.storageStart != -1 ? info.storageStart : 0;
      int storageEnd = info.storageEnd != -1 ? info.storageEnd : Math.max(0, menu.slots.size() - 37);

      // Also check if we need a crafting table retrieved from backpack
      boolean needTable = !CraftRecipeHelper.is2x2(this.activeRecipe)
         && !InvUtils.find(Items.CRAFTING_TABLE).found()
         && BackpackAdapter.hasItemInBackpack(Items.CRAFTING_TABLE);

      boolean withdrewAny = false;
      for (int i = storageStart; i <= Math.min(storageEnd, menu.slots.size() - 1); i++) {
         Slot slot = menu.slots.get(i);
         ItemStack stack = slot.getItem();
         if (stack.isEmpty()) continue;

         boolean isNeededIngredient = neededInDirect.containsKey(stack.getItem());
         boolean isTable = needTable && stack.is(Items.CRAFTING_TABLE);

         if (isNeededIngredient || isTable) {
            int currentEmpty = CraftRecipeHelper.getEmptyInventorySlots();
            int freeSpaceInStack = CraftRecipeHelper.getFreeSpaceFor(stack.getItem());
            if (currentEmpty <= 1 && freeSpaceInStack < stack.getCount() && directCrafts > 0) {
               this.info("Preserving inventory room for craft output. Executing sub-batch craft...");
               this.mc.player.closeContainer();
               this.timer = this.craftDelay.get() + 2;
               this.state = State.RESOLVING;
               return;
            }

            InvUtils.shiftClick().slotId(i);
            this.info("Withdrew (highlight)%dx %s(default) from backpack.", stack.getCount(), stack.getHoverName().getString());
            withdrewAny = true;
            this.timer = this.craftDelay.get() + 2;
            return;
         }
      }

      if (!withdrewAny) {
         if (CraftRecipeHelper.getDirectCraftsPossible(this.activeRecipe) > 0) {
            this.info("Enough materials in direct inventory for partial craft. Proceeding...");
            this.mc.player.closeContainer();
            this.timer = this.craftDelay.get() + 2;
            this.state = State.RESOLVING;
         } else {
            this.error("Inventory full! Cannot withdraw required materials from backpack.");
            this.mc.player.closeContainer();
            this.cancelTask();
         }
      }
   }

   private void handleOffloadingToBackpack() {
      if (this.currentTask == null) {
         this.state = State.RESOLVING;
         return;
      }

      AbstractContainerMenu menu = this.mc.player.containerMenu;
      if (BackpackAdapter.isBackpackMenu(menu)) {
         this.chestWaitTicks = 0;

         // 1. Deposit all crafted items of current task into backpack storage
         int depositedCrafted = BackpackAdapter.depositItemToBackpack(menu, this.currentTask.item);

         // 2. If direct inventory space is still critically low, deposit clutter or move excess stacks
         if (CraftRecipeHelper.getEmptyInventorySlots() <= 2 && this.activeRecipe != null) {
            Set<Item> ingredients = new HashSet<>();
            for (Ingredient ing : this.activeRecipe.value().getIngredients()) {
               if (ing != null && !ing.isEmpty()) {
                  for (ItemStack st : ing.getItems()) {
                     ingredients.add(st.getItem());
                  }
               }
            }
            int depositedOther = BackpackAdapter.depositExcept(menu, ingredients);
            if (depositedOther > 0) {
               this.info("Offloaded %d non-ingredient item(s) to backpack storage.", depositedOther);
            }
            if (CraftRecipeHelper.getEmptyInventorySlots() <= 1) {
               int freed = BackpackAdapter.makeRoomInInventory(menu, 3);
               if (freed > 0) {
                  this.info("Freed inventory space by moving %d item(s) to backpack storage.", freed);
               }
            }
         }

         if (depositedCrafted > 0) {
            this.info("Offloaded (highlight)%dx %s(default) to backpack storage.",
               depositedCrafted, this.currentTask.item.getDescription().getString());
         }

         // Close backpack and return to RESOLVING to resume crafting
         this.mc.player.closeContainer();
         this.timer = this.craftDelay.get() + 2;
         this.state = State.RESOLVING;
         return;
      }

      this.chestWaitTicks++;
      if (this.chestWaitTicks > 25) {
         this.error("Failed to open backpack for offloading (timed out).");
         this.cancelTask();
         return;
      }

      if (BackpackAdapter.hasPortableCraftingAvailable()) {
         BackpackAdapter.openPortableCrafting();
         this.timer = this.craftDelay.get() + 2;
      }
   }

   private void handleResolving() {
      if (this.currentTask == null || this.currentTask.remainingCount <= 0) {
         if (this.queue.isEmpty()) {
            this.state = State.CLEANUP;
            return;
         }
         this.currentTask = this.queue.remove(0);
         this.containerSearcher.reset();
      }

      this.activeRecipe = CraftRecipeHelper.findBestRecipe(this.currentTask.item);
      if (this.activeRecipe == null) {
         this.error("No crafting recipe found for (highlight)%s(default).", this.currentTask.item.getDescription().getString());
         this.currentTask = null;
         return;
      }

      int yield = CraftRecipeHelper.getResultCount(this.activeRecipe);
      int craftsNeeded = (int) Math.ceil((double) this.currentTask.remainingCount / yield);
      Map<Item, Integer> missing = CraftRecipeHelper.getMissingItems(this.activeRecipe, craftsNeeded);

      if (missing.isEmpty()) {
         startCraftingSequence();
         return;
      }

      // Ingredients missing for full order - check if CraftPlanner can resolve full order recursively
      CraftPlanner.CraftPlan plan = null;
      if (this.recursiveCrafting.get()) {
         plan = CraftPlanner.createPlan(this.currentTask.item, this.currentTask.remainingCount);
         if (plan.isSatisfied && plan.steps.size() > 1) {
            // Plan has intermediate prerequisite steps in dependency order!
            List<CraftPlanner.CraftStep> prereqs = new ArrayList<>(plan.steps.subList(0, plan.steps.size() - 1));

            CraftTask finalTask = this.currentTask;
            this.queue.add(0, finalTask);

            for (int i = prereqs.size() - 1; i >= 0; i--) {
               CraftPlanner.CraftStep step = prereqs.get(i);
               this.queue.add(0, new CraftTask(step.resultItem, step.yieldProduced));
            }

            this.info("Craft tree resolved: (highlight)%d step(s)(default) for %s.",
               prereqs.size() + 1, finalTask.item.getDescription().getString());
            this.currentTask = this.queue.remove(0);
            this.timer = this.craftDelay.get();
            return;
         }
      }

      // Circuit breaker: If consecutive exhaustions occurred, break out of loop gracefully
      if (this.consecutiveExhaustions >= 2) {
         int crafted = this.currentTask.initialCount - this.currentTask.remainingCount;
         if (crafted > 0) {
            this.info("Finished crafting available materials for %s (%d/%d crafted).",
               this.currentTask.item.getDescription().getString(), crafted, this.currentTask.initialCount);
         } else {
            this.warning("Materials exhausted for %s. Skipping...", this.currentTask.item.getDescription().getString());
         }
         this.consecutiveExhaustions = 0;
         this.currentTask = null;
         if (this.queue.isEmpty()) {
            this.state = State.CLEANUP;
         } else {
            this.state = State.RESOLVING;
         }
         return;
      }

      // If player has materials on-hand (in direct inventory, backpack, or open grid) to craft at least 1 batch:
      // ALWAYS CRAFT ON-HAND MATERIALS FIRST! Never wander off searching chests while holding craftable materials!
      if (CraftRecipeHelper.canSatisfy(this.activeRecipe, 1)) {
         this.info("Crafting available on-hand materials for %s (remaining count will adjust)...", this.currentTask.item.getDescription().getString());
         startCraftingSequence();
         return;
      }

      // On-hand materials genuinely exhausted: search nearby chests if enabled
      Map<Item, Integer> neededForChest = (plan != null && !plan.missingRawMaterials.isEmpty())
         ? plan.missingRawMaterials
         : missing;

      if (this.searchChests.get()) {
         List<BlockPos> containers = this.containerSearcher.findNearbyContainers(this.chestRadius.get());
         if (!containers.isEmpty()) {
            this.targetChestPos = containers.get(0);
            if (ContainerSearcher.isWithinReach(this.targetChestPos)) {
               ContainerSearcher.openContainer(this.targetChestPos);
               this.state = State.LOOTING_CHEST;
               this.chestWaitTicks = 0;
               this.timer = this.craftDelay.get() + 2;
            } else if (this.autoWalk.get() && BaritoneUtils.IS_AVAILABLE) {
               ContainerSearcher.navigateTo(this.targetChestPos);
               this.state = State.NAVIGATING_TO_CHEST;
               this.info("Searching %s at [%d, %d, %d]...", ContainerSearcher.getContainerName(this.targetChestPos), this.targetChestPos.getX(), this.targetChestPos.getY(), this.targetChestPos.getZ());
            } else {
               this.warning("%s at [%d, %d, %d] is out of reach (auto-walk disabled).", ContainerSearcher.getContainerName(this.targetChestPos), this.targetChestPos.getX(), this.targetChestPos.getY(), this.targetChestPos.getZ());
               int crafted = this.currentTask.initialCount - this.currentTask.remainingCount;
               if (crafted > 0) {
                  this.info("Finished crafting available materials for %s (%d/%d crafted).",
                     this.currentTask.item.getDescription().getString(), crafted, this.currentTask.initialCount);
               }
               this.consecutiveExhaustions = 0;
               this.currentTask = null;
               if (this.queue.isEmpty()) {
                  this.state = State.CLEANUP;
               } else {
                  this.state = State.RESOLVING;
               }
            }
            return;
         }
      }

      // Missing materials and no chests available
      StringBuilder sb = new StringBuilder();
      for (Map.Entry<Item, Integer> e : neededForChest.entrySet()) {
         sb.append(e.getValue()).append("x ").append(e.getKey().getDescription().getString()).append(", ");
      }
      String missingStr = sb.length() > 2 ? sb.substring(0, sb.length() - 2) : "unknown";

      int crafted = this.currentTask.initialCount - this.currentTask.remainingCount;
      if (crafted > 0) {
         this.warning("Partially crafted %s (%d/%d). Missing remaining materials: %s.",
            this.currentTask.item.getDescription().getString(), crafted, this.currentTask.initialCount, missingStr);
      } else {
         this.warning("Missing materials for %s: %s.", this.currentTask.item.getDescription().getString(), missingStr);
      }
      this.consecutiveExhaustions = 0;
      this.currentTask = null;
      if (this.queue.isEmpty()) {
         this.state = State.CLEANUP;
      } else {
         this.state = State.RESOLVING;
      }
   }

   private void startCraftingSequence() {
      int directCrafts = CraftRecipeHelper.getDirectCraftsPossible(this.activeRecipe);
      boolean canCraftDirect = directCrafts > 0;
      boolean is2x2 = CraftRecipeHelper.is2x2(this.activeRecipe);

      // 1. If preferBackpack is enabled and crafting-capable backpack is available, prefer backpack crafting!
      if (this.preferBackpack.get() && BackpackAdapter.hasCraftingCapableBackpack()) {
         if (BackpackAdapter.isBackpackMenu(this.mc.player.containerMenu)) {
            BackpackAdapter.BackpackCraftInfo info = BackpackAdapter.getBackpackCraftInfo(this.mc.player.containerMenu);
            if (info.valid && info.resultSlot != -1) {
               this.state = State.CRAFTING;
               return;
            } else if (BackpackAdapter.hasCraftingUpgrade(this.mc.player.containerMenu)) {
               this.state = State.OPENING_BACKPACK;
               return;
            }
         } else if (BackpackAdapter.openPortableCrafting()) {
            this.timer = this.craftDelay.get() + 3;
            this.state = State.OPENING_BACKPACK;
            this.chestWaitTicks = 0;
            this.backpackTabWaitTicks = 0;
            this.info("Opening portable backpack crafting table...");
            return;
         }
      }

      // 2. If already in a backpack menu:
      if (BackpackAdapter.isBackpackMenu(this.mc.player.containerMenu)) {
         BackpackAdapter.BackpackCraftInfo info = BackpackAdapter.getBackpackCraftInfo(this.mc.player.containerMenu);
         if (info.valid && info.resultSlot != -1) {
            this.state = State.CRAFTING;
            return;
         } else if (BackpackAdapter.hasCraftingUpgrade(this.mc.player.containerMenu)) {
            this.state = State.OPENING_BACKPACK;
            return;
         } else if (canCraftDirect) {
            this.mc.player.closeContainer();
            this.timer = this.craftDelay.get() + 2;
            this.state = State.RESOLVING;
            return;
         } else {
            this.state = State.WITHDRAWING_FROM_BACKPACK;
            return;
         }
      }

      // 3. If recipe fits 2x2 player grid and direct inventory can craft at least one sub-batch:
      if (is2x2 && canCraftDirect) {
         this.state = State.CRAFTING;
         return;
      }

      // 4. If at a vanilla crafting table and direct inventory can craft at least one sub-batch:
      if (this.mc.player.containerMenu instanceof CraftingMenu && canCraftDirect) {
         this.state = State.CRAFTING;
         return;
      }

      // 5. If direct inventory cannot even craft 1 item, retrieve materials from backpack:
      if (!canCraftDirect) {
         if (BackpackAdapter.hasPortableCraftingAvailable() && BackpackAdapter.openPortableCrafting()) {
            this.timer = this.craftDelay.get() + 3;
            this.state = State.OPENING_BACKPACK;
            this.chestWaitTicks = 0;
            this.backpackTabWaitTicks = 0;
            this.info("Opening backpack to retrieve crafting materials...");
            return;
         }
         this.error("Materials for (highlight)%s(default) are in backpack, but backpack could not be opened.", this.currentTask.item.getDescription().getString());
         this.cancelTask();
         return;
      }

      // 6. Direct inventory can craft (canCraftDirect is true), but recipe needs 3x3 table:
      // If portable backpack with crafting upgrade is preferred and available:
      if (this.preferBackpack.get() && BackpackAdapter.hasCraftingCapableBackpack()) {
         if (BackpackAdapter.openPortableCrafting()) {
            this.timer = this.craftDelay.get() + 3;
            this.state = State.OPENING_BACKPACK;
            this.chestWaitTicks = 0;
            this.backpackTabWaitTicks = 0;
            this.info("Opening portable backpack crafting table...");
            return;
         }
      }

      // Check if crafting table already in reach
      BlockPos nearbyTable = findNearbyTable(16);
      if (nearbyTable != null) {
         this.tablePos = nearbyTable;
         if (ContainerSearcher.isWithinReach(nearbyTable)) {
            this.state = State.OPENING_TABLE;
         } else if (this.autoWalk.get() && BaritoneUtils.IS_AVAILABLE) {
            ContainerSearcher.navigateTo(nearbyTable);
            this.state = State.NAVIGATING_TO_TABLE;
         } else {
            this.state = State.OPENING_TABLE;
         }
      } else if (this.autoCraftingTable.get()) {
         this.state = State.CRAFTING_TABLE_PLACING;
      } else {
         this.error("Crafting Table required for (highlight)%s(default) but none found.", this.currentTask.item.getDescription().getString());
         this.cancelTask();
      }
   }

   private void handleNavigatingToChest() {
      if (this.targetChestPos == null) {
         this.navWaitTicks = 0;
         this.state = State.RESOLVING;
         return;
      }

      if (ContainerSearcher.isWithinReach(this.targetChestPos)) {
         ContainerSearcher.stopNavigation();
         ContainerSearcher.openContainer(this.targetChestPos);
         this.state = State.LOOTING_CHEST;
         this.chestWaitTicks = 0;
         this.navWaitTicks = 0;
         this.timer = this.craftDelay.get() + 2;
         return;
      }

      this.navWaitTicks++;
      if (this.navWaitTicks > 120) {
         this.warning("Chest at [%d, %d, %d] unreachable (timed out). Skipping...", this.targetChestPos.getX(), this.targetChestPos.getY(), this.targetChestPos.getZ());
         ContainerSearcher.stopNavigation();
         this.containerSearcher.markVisited(this.targetChestPos);
         this.targetChestPos = null;
         this.navWaitTicks = 0;
         this.state = State.RESOLVING;
      }
   }

   private void handleLootingChest() {
      AbstractContainerMenu menu = this.mc.player.containerMenu;
      if (menu == null || menu instanceof InventoryMenu) {
         this.chestWaitTicks++;
         if (this.chestWaitTicks > 25) {
            // Container didn't open or closed
            if (this.targetChestPos != null) {
               this.containerSearcher.markVisited(this.targetChestPos);
            }
            this.state = State.RESOLVING;
         }
         return;
      }

      int yield = CraftRecipeHelper.getResultCount(this.activeRecipe);
      int craftsNeeded = (int) Math.ceil((double) this.currentTask.remainingCount / yield);
      CraftPlanner.CraftPlan plan = CraftPlanner.createPlan(this.currentTask.item, this.currentTask.remainingCount);
      Map<Item, Integer> needed = (plan != null && !plan.missingRawMaterials.isEmpty())
         ? new HashMap<>(plan.missingRawMaterials)
         : CraftRecipeHelper.getMissingItems(this.activeRecipe, craftsNeeded);

      int directTaken = this.containerSearcher.lootContainer(menu, needed, this.currentTask.item, this.currentTask.remainingCount);
      if (directTaken > 0) {
         this.currentTask.remainingCount -= directTaken;
         this.info("Withdrew (highlight)%dx %s(default) from %s.", directTaken, this.currentTask.item.getDescription().getString(), ContainerSearcher.getContainerName(this.targetChestPos));
      }

      if (this.targetChestPos != null) {
         this.containerSearcher.markVisited(this.targetChestPos);
      }

      this.mc.player.closeContainer();
      this.timer = this.craftDelay.get() + 2;
      this.state = State.RESOLVING;
   }

   private void handlePlacingTable() {
      FindItemResult tableItem = InvUtils.find(Items.CRAFTING_TABLE);
      if (!tableItem.found()) {
         // Check if a Crafting Table is stored inside an equipped or held backpack
         if (BackpackAdapter.hasItemInBackpack(Items.CRAFTING_TABLE)) {
            if (BackpackAdapter.openPortableCrafting()) {
               this.info("Crafting Table found in backpack. Opening backpack to retrieve it...");
               this.timer = this.craftDelay.get() + 3;
               this.state = State.OPENING_BACKPACK;
               this.chestWaitTicks = 0;
               return;
            }
         }

         // Can we craft a crafting table?
         RecipeHolder<CraftingRecipe> ctRecipe = CraftRecipeHelper.findBestRecipe(Items.CRAFTING_TABLE);
         if (ctRecipe != null && CraftRecipeHelper.canSatisfyRecursive(ctRecipe, 1, 3)) {
            this.queue.add(0, this.currentTask);
            this.currentTask = new CraftTask(Items.CRAFTING_TABLE, 1);
            this.state = State.RESOLVING;
            return;
         } else {
            this.error("No Crafting Table found and not enough materials to craft one.");
            this.cancelTask();
            return;
         }
      }

      // Find an air block adjacent to player on solid ground
      BlockPos playerPos = this.mc.player.blockPosition();
      BlockPos placePos = null;
      for (Direction dir : Direction.Plane.HORIZONTAL) {
         BlockPos checkPos = playerPos.relative(dir);
         if (this.mc.level.getBlockState(checkPos).canBeReplaced() && !this.mc.level.getBlockState(checkPos.below()).isAir()) {
            placePos = checkPos;
            break;
         }
      }

      if (placePos == null) {
         placePos = playerPos;
      }

      if (!tableItem.isHotbar()) {
         InvUtils.move().from(tableItem.slot()).toHotbar(0);
         tableItem = InvUtils.findInHotbar(Items.CRAFTING_TABLE);
      }

      if (tableItem.isHotbar()) {
         BlockUtils.place(placePos, tableItem, true, 50, true, true);
         this.placedTablePos = placePos;
         this.tablePos = placePos;
         this.timer = this.craftDelay.get() + 2;
         this.state = State.OPENING_TABLE;
      } else {
         this.error("Unable to move Crafting Table to hotbar.");
         this.cancelTask();
      }
   }

   private void handleNavigatingToTable() {
      if (this.tablePos == null) {
         this.navWaitTicks = 0;
         this.state = State.RESOLVING;
         return;
      }

      if (ContainerSearcher.isWithinReach(this.tablePos)) {
         ContainerSearcher.stopNavigation();
         this.navWaitTicks = 0;
         this.state = State.OPENING_TABLE;
         return;
      }

      this.navWaitTicks++;
      if (this.navWaitTicks > 120) {
         this.warning("Crafting table unreachable (timed out). Falling back to placement...");
         ContainerSearcher.stopNavigation();
         this.tablePos = null;
         this.navWaitTicks = 0;
         this.state = State.CRAFTING_TABLE_PLACING;
      }
   }

   private void handleOpeningTable() {
      if (this.tablePos == null) {
         this.state = State.RESOLVING;
         return;
      }

      if (this.mc.player.containerMenu instanceof CraftingMenu) {
         this.state = State.CRAFTING;
         return;
      }

      BlockHitResult bhr = new BlockHitResult(Vec3.atCenterOf(this.tablePos), Direction.UP, this.tablePos, false);
      BlockUtils.interact(bhr, InteractionHand.MAIN_HAND, true);
      this.timer = this.craftDelay.get() + 2;
      this.state = State.CRAFTING;
   }

   private void handleCrafting() {
      if (this.currentTask == null || this.activeRecipe == null) {
         this.state = State.RESOLVING;
         return;
      }

      AbstractContainerMenu menu = this.mc.player.containerMenu;

      // Check if backpack menu is open
      if (BackpackAdapter.isBackpackMenu(menu)) {
         BackpackAdapter.BackpackCraftInfo info = BackpackAdapter.getBackpackCraftInfo(menu);
         if (info.valid && info.resultSlot != -1) {
            this.backpackTabWaitTicks = 0;
            ItemStack currentResult = menu.getSlot(info.resultSlot).getItem();
            if (!currentResult.isEmpty() && currentResult.is(this.currentTask.item)) {
               this.gridResultWaitTicks = 0;
               this.pendingCraftItem = this.currentTask.item;
               this.pendingExpectedYield = currentResult.getCount();
               this.pendingBeforeDirectCount = CraftRecipeHelper.countInInventory(this.currentTask.item);
               List<Integer> srcSlots = BackpackAdapter.getAvailableSourceSlots(menu, info);
               this.pendingBeforePoolCount = BackpackAdapter.getSourcePool(menu, srcSlots).getOrDefault(this.currentTask.item, 0);
               this.pendingSlot = info.resultSlot;
               this.pendingIsBackpack = true;
               this.pendingWaitTicks = 0;

               InvUtils.shiftClick().slotId(info.resultSlot);
               BackpackAdapter.clearCache();
               this.state = State.WAITING_FOR_CRAFT_RESULT;
               this.timer = 1;
               return;
            }

            // If grid already contains items, wait for server to populate result slot before declaring exhaustion
            boolean gridHasItems = false;
            for (int s = info.gridStart; s <= info.gridEnd; s++) {
               if (!menu.getSlot(s).getItem().isEmpty()) {
                  gridHasItems = true;
                  break;
               }
            }

            if (gridHasItems) {
               this.gridResultWaitTicks++;
               if (this.gridResultWaitTicks <= 15) {
                  this.timer = 2;
                  return;
               }
               this.warning("Backpack crafting grid result timed out with items in grid. Clearing grid...");
               BackpackAdapter.clearGrid(menu, info.gridStart, info.gridEnd);
               this.gridResultWaitTicks = 0;
               this.consecutiveExhaustions++;
               this.state = State.RESOLVING;
               this.timer = this.craftDelay.get() + 2;
               return;
            }

            // Calculate how many complete batches can be crafted from available sources
            List<Integer> sourceSlots = BackpackAdapter.getAvailableSourceSlots(menu, info);
            Map<Item, Integer> pool = BackpackAdapter.getSourcePool(menu, sourceSlots);
            int maxBatch = CraftRecipeHelper.calculateMaxCraftsFromPool(this.activeRecipe, pool);
            int yield = CraftRecipeHelper.getResultCount(this.activeRecipe);
            int craftsNeeded = (int) Math.ceil((double) this.currentTask.remainingCount / Math.max(1, yield));
            int batchCount = Math.min(maxBatch, craftsNeeded);

            if (batchCount <= 0) {
               BackpackAdapter.clearGrid(menu, info.gridStart, info.gridEnd);
               this.info("Backpack crafting materials exhausted (remaining: %d). Resolving next batch...", this.currentTask.remainingCount);
               this.gridResultWaitTicks = 0;
               this.consecutiveExhaustions++;
               this.state = State.RESOLVING;
               this.timer = this.craftDelay.get() + 2;
               return;
            }

            // Populate backpack crafting grid with batchCount items per slot
            GridPlaceResult placeRes = placeBackpackGrid(menu, info, this.activeRecipe, batchCount);
            if (placeRes == GridPlaceResult.INGREDIENTS_DEPLETED) {
               if (this.currentTask.remainingCount > 0) {
                  BackpackAdapter.clearGrid(menu, info.gridStart, info.gridEnd);
                  this.info("Backpack crafting materials exhausted (remaining: %d). Resolving next batch...", this.currentTask.remainingCount);
                  this.gridResultWaitTicks = 0;
                  this.consecutiveExhaustions++;
                  this.state = State.RESOLVING;
                  this.timer = this.craftDelay.get() + 2;
                  return;
               }
            } else if (placeRes == GridPlaceResult.BLOCKED) {
               return;
            } else {
               this.gridResultWaitTicks++;
               if (this.gridResultWaitTicks > 12) {
                  this.warning("Backpack crafting grid result timed out. Resolving next batch...");
                  this.gridResultWaitTicks = 0;
                  this.state = State.RESOLVING;
                  this.timer = this.craftDelay.get() + 2;
                  return;
               }
            }

            this.timer = this.craftDelay.get() + 1;
            return;
         } else if (BackpackAdapter.hasCraftingUpgrade(menu)) {
            // Backpack has crafting upgrade but tab not open yet or syncing
            if (!BackpackAdapter.isCraftingTabOpen(menu)) {
               this.info("Opening backpack crafting tab...");
               BackpackAdapter.openCraftingTab(menu);
               this.timer = this.craftDelay.get() + 3;
            } else {
               this.backpackTabWaitTicks++;
               if (this.backpackTabWaitTicks <= 5) {
                  this.timer = 2;
                  return;
               }
               this.warning("Backpack crafting grid not detected in time. Withdrawing materials...");
               this.state = State.WITHDRAWING_FROM_BACKPACK;
               this.timer = 0;
               return;
            }
            return;
         } else {
            // Backpack menu is open, but does NOT have an active crafting grid!
            this.state = State.WITHDRAWING_FROM_BACKPACK;
            this.timer = 0;
            return;
         }
      }

      boolean table3x3 = !CraftRecipeHelper.is2x2(this.activeRecipe);

      if (table3x3 && !(menu instanceof CraftingMenu)) {
         // Need table but not open yet
         this.state = State.OPENING_TABLE;
         return;
      }

      // Check result slot 0
      ItemStack currentResult = menu.getSlot(0).getItem();
      if (!currentResult.isEmpty() && currentResult.is(this.currentTask.item)) {
         this.gridResultWaitTicks = 0;
         this.pendingCraftItem = this.currentTask.item;
         this.pendingExpectedYield = currentResult.getCount();
         this.pendingBeforeDirectCount = CraftRecipeHelper.countInInventory(this.currentTask.item);
         this.pendingBeforePoolCount = this.pendingBeforeDirectCount;
         this.pendingSlot = 0;
         this.pendingIsBackpack = false;
         this.pendingWaitTicks = 0;

         InvUtils.shiftClick().slotId(0);
         BackpackAdapter.clearCache();
         this.state = State.WAITING_FOR_CRAFT_RESULT;
         this.timer = 1;
         return;
      }

      int gridStart = 1;
      int gridEnd = table3x3 ? 9 : 4;
      boolean tableGridHasItems = false;
      for (int s = gridStart; s <= gridEnd; s++) {
         if (!menu.getSlot(s).getItem().isEmpty()) {
            tableGridHasItems = true;
            break;
         }
      }

      if (tableGridHasItems) {
         this.gridResultWaitTicks++;
         if (this.gridResultWaitTicks <= 15) {
            this.timer = 2;
            return;
         }
         this.warning("Crafting grid result timed out with items in grid. Clearing grid...");
         BackpackAdapter.clearGrid(menu, gridStart, gridEnd);
         if (table3x3 && menu instanceof CraftingMenu) {
            this.mc.player.closeContainer();
         }
         this.gridResultWaitTicks = 0;
         this.consecutiveExhaustions++;
         this.state = State.RESOLVING;
         this.timer = this.craftDelay.get() + 2;
         return;
      }

      // Calculate how many complete batches can be crafted from available inventory slots
      int invStart = table3x3 ? 10 : 9;
      List<Integer> tableSourceSlots = new ArrayList<>();
      for (int s = invStart; s < menu.slots.size(); s++) {
         tableSourceSlots.add(s);
      }
      Map<Item, Integer> tablePool = BackpackAdapter.getSourcePool(menu, tableSourceSlots);
      int maxTableBatch = CraftRecipeHelper.calculateMaxCraftsFromPool(this.activeRecipe, tablePool);
      int tableYield = CraftRecipeHelper.getResultCount(this.activeRecipe);
      int tableCraftsNeeded = (int) Math.ceil((double) this.currentTask.remainingCount / Math.max(1, tableYield));
      int tableBatchCount = Math.min(maxTableBatch, tableCraftsNeeded);

      if (tableBatchCount <= 0) {
         BackpackAdapter.clearGrid(menu, 1, table3x3 ? 9 : 4);
         this.info("Direct ingredients exhausted (remaining: %d). Resolving next batch...", this.currentTask.remainingCount);
         if (table3x3 && menu instanceof CraftingMenu) {
            this.mc.player.closeContainer();
         }
         this.gridResultWaitTicks = 0;
         this.consecutiveExhaustions++;
         this.timer = this.craftDelay.get() + 2;
         this.state = State.RESOLVING;
         return;
      }

      // Place recipe into crafting grid via direct slot clicks with tableBatchCount items per slot
      GridPlaceResult placeRes = placeGridManually(menu, this.activeRecipe, table3x3, tableBatchCount);

      ItemStack afterPlace = menu.getSlot(0).getItem();
      if (!afterPlace.isEmpty() && afterPlace.is(this.currentTask.item)) {
         this.gridResultWaitTicks = 0;
      } else if (placeRes == GridPlaceResult.INGREDIENTS_DEPLETED) {
         if (this.currentTask.remainingCount > 0) {
            BackpackAdapter.clearGrid(menu, 1, table3x3 ? 9 : 4);
            this.info("Direct ingredients exhausted (remaining: %d). Resolving next batch...", this.currentTask.remainingCount);
            if (table3x3 && menu instanceof CraftingMenu) {
               this.mc.player.closeContainer();
            }
            this.gridResultWaitTicks = 0;
            this.consecutiveExhaustions++;
            this.timer = this.craftDelay.get() + 2;
            this.state = State.RESOLVING;
            return;
         }
      } else if (placeRes == GridPlaceResult.BLOCKED) {
         return;
      } else {
         this.gridResultWaitTicks++;
         if (this.gridResultWaitTicks > 12) {
            this.warning("Crafting grid result timed out. Resolving next batch...");
            if (table3x3 && menu instanceof CraftingMenu) {
               this.mc.player.closeContainer();
            }
            this.gridResultWaitTicks = 0;
            this.state = State.RESOLVING;
            this.timer = this.craftDelay.get() + 2;
            return;
         }
      }

      this.timer = this.craftDelay.get() + 1;
   }

   private void handleWaitingForCraftResult() {
      if (this.currentTask == null || this.pendingCraftItem == null) {
         clearPendingCraft();
         this.state = State.RESOLVING;
         return;
      }

      AbstractContainerMenu menu = this.mc.player.containerMenu;
      this.pendingWaitTicks++;

      int currentDirect = CraftRecipeHelper.countInInventory(this.pendingCraftItem);
      int directGained = currentDirect - this.pendingBeforeDirectCount;

      int poolGained = directGained;
      if (this.pendingIsBackpack && BackpackAdapter.isBackpackMenu(menu)) {
         BackpackAdapter.BackpackCraftInfo info = BackpackAdapter.getBackpackCraftInfo(menu);
         List<Integer> srcSlots = BackpackAdapter.getAvailableSourceSlots(menu, info);
         int poolNow = BackpackAdapter.getSourcePool(menu, srcSlots).getOrDefault(this.pendingCraftItem, 0);
         poolGained = poolNow - this.pendingBeforePoolCount;
      }

      int actuallyGained = Math.max(directGained, poolGained);

      // Check 1: Direct inventory or backpack storage count increased
      if (actuallyGained > 0) {
         this.consecutiveExhaustions = 0;
         this.craftRetryTicks = 0;
         this.gridResultWaitTicks = 0;
         this.currentTask.remainingCount -= actuallyGained;
         this.info("Crafted (highlight)%dx %s(default) (remaining: %d).",
            actuallyGained, this.pendingCraftItem.getDescription().getString(), Math.max(0, this.currentTask.remainingCount));
         clearPendingCraft();

         if (this.currentTask.remainingCount <= 0) {
            this.currentTask = null;
            this.settleWaitTicks = 3;
            this.state = State.SETTLING;
            this.timer = 1;
         } else {
            this.state = State.CRAFTING;
            this.timer = this.craftDelay.get();
         }
         return;
      }

      // Check 2: Result slot was emptied by server
      if (this.pendingWaitTicks >= 2 && menu != null && this.pendingSlot >= 0 && this.pendingSlot < menu.slots.size()) {
         ItemStack resultItem = menu.getSlot(this.pendingSlot).getItem();
         if (resultItem.isEmpty()) {
            int gained = Math.max(1, this.pendingExpectedYield);
            this.consecutiveExhaustions = 0;
            this.craftRetryTicks = 0;
            this.gridResultWaitTicks = 0;
            this.currentTask.remainingCount -= gained;
            this.info("Crafted (highlight)%dx %s(default) via server sync (remaining: %d).",
               gained, this.pendingCraftItem.getDescription().getString(), Math.max(0, this.currentTask.remainingCount));
            clearPendingCraft();

            if (this.currentTask.remainingCount <= 0) {
               this.currentTask = null;
               this.settleWaitTicks = 3;
               this.state = State.SETTLING;
               this.timer = 1;
            } else {
               this.state = State.CRAFTING;
               this.timer = this.craftDelay.get();
            }
            return;
         }
      }

      // Check 3: Timeout after 30 ticks (1.5 seconds)
      if (this.pendingWaitTicks > 30) {
         int freeSpace = CraftRecipeHelper.getFreeSpaceFor(this.pendingCraftItem);
         if (freeSpace <= 0) {
            if (this.pendingIsBackpack && BackpackAdapter.isBackpackMenu(menu)) {
               int deposited = BackpackAdapter.depositItemToBackpack(menu, this.pendingCraftItem);
               if (deposited > 0) {
                  clearPendingCraft();
                  this.state = State.CRAFTING;
                  this.timer = this.craftDelay.get();
                  return;
               }
            } else if (BackpackAdapter.isWearingBackpack() || BackpackAdapter.findBackpackInInventory().found()) {
               this.info("Inventory full: offloading items to backpack storage...");
               this.mc.player.closeContainer();
               this.timer = this.craftDelay.get() + 2;
               this.state = State.OFFLOADING_TO_BACKPACK;
               clearPendingCraft();
               return;
            }
         }

         this.warning("Craft confirmation timed out. Resynchronizing container...");
         clearPendingCraft();
         this.state = State.CRAFTING;
         this.timer = this.craftDelay.get() + 2;
         return;
      }

      this.timer = 1;
   }

   private void handleSettling() {
      this.settleWaitTicks--;
      if (this.settleWaitTicks <= 0) {
         this.settleWaitTicks = 0;
         this.state = State.RESOLVING;
         this.timer = 1;
      }
   }

   private GridPlaceResult placeBackpackGrid(AbstractContainerMenu menu, BackpackAdapter.BackpackCraftInfo info, RecipeHolder<CraftingRecipe> recipe, int batchCount) {
      Ingredient[] grid = CraftRecipeHelper.getGridIngredients(recipe, true);
      List<Integer> sourceSlots = BackpackAdapter.getAvailableSourceSlots(menu, info);

      for (int i = 0; i < grid.length && (info.gridStart + i) <= info.gridEnd; i++) {
         Ingredient ing = grid[i];
         int targetSlot = info.gridStart + i;

         if (ing == null || ing.isEmpty()) {
            if (menu.getSlot(targetSlot).hasItem()) {
               InvUtils.shiftClick().slotId(targetSlot);
            }
            continue;
         }

         if (menu.getSlot(targetSlot).hasItem()) {
            ItemStack inSlot = menu.getSlot(targetSlot).getItem();
            if (!ing.test(inSlot) || inSlot.getCount() > batchCount) {
               InvUtils.shiftClick().slotId(targetSlot);
               if (menu.getSlot(targetSlot).hasItem()) {
                  if (info.storageStart != -1 && info.storageEnd != -1) {
                     for (int s = info.storageStart; s <= info.storageEnd && s < menu.slots.size(); s++) {
                        if (menu.slots.get(s).getItem().isEmpty()) {
                           this.mc.gameMode.handleInventoryMouseClick(menu.containerId, targetSlot, 0, ClickType.PICKUP, this.mc.player);
                           this.mc.gameMode.handleInventoryMouseClick(menu.containerId, s, 0, ClickType.PICKUP, this.mc.player);
                           break;
                        }
                     }
                  }
                  if (menu.getSlot(targetSlot).hasItem()) {
                     this.error("Inventory/backpack full: unable to clear crafting grid slot.");
                     this.cancelTask();
                     return GridPlaceResult.BLOCKED;
                  }
               }
            }
         }

         int currentInSlot = (menu.getSlot(targetSlot).hasItem() && ing.test(menu.getSlot(targetSlot).getItem()))
            ? menu.getSlot(targetSlot).getItem().getCount()
            : 0;
         int needed = batchCount - currentInSlot;

         while (needed > 0) {
            int foundSource = -1;
            for (int s : sourceSlots) {
               if (s == targetSlot) continue;
               ItemStack stack = menu.getSlot(s).getItem();
               if (!stack.isEmpty() && ing.test(stack)) {
                  foundSource = s;
                  break;
               }
            }

            if (foundSource == -1) {
               break;
            }

            ItemStack sourceStack = menu.getSlot(foundSource).getItem();
            int available = sourceStack.getCount();
            int toMove = Math.min(needed, available);

            if (toMove == available && !menu.getSlot(targetSlot).hasItem()) {
               this.mc.gameMode.handleInventoryMouseClick(menu.containerId, foundSource, 0, ClickType.PICKUP, this.mc.player);
               this.mc.gameMode.handleInventoryMouseClick(menu.containerId, targetSlot, 0, ClickType.PICKUP, this.mc.player);
               if (!menu.getCarried().isEmpty()) {
                  this.mc.gameMode.handleInventoryMouseClick(menu.containerId, foundSource, 0, ClickType.PICKUP, this.mc.player);
               }
            } else if (toMove == available && menu.getSlot(targetSlot).hasItem() && menu.getSlot(targetSlot).getItem().is(sourceStack.getItem())) {
               this.mc.gameMode.handleInventoryMouseClick(menu.containerId, foundSource, 0, ClickType.PICKUP, this.mc.player);
               this.mc.gameMode.handleInventoryMouseClick(menu.containerId, targetSlot, 0, ClickType.PICKUP, this.mc.player);
               if (!menu.getCarried().isEmpty()) {
                  this.mc.gameMode.handleInventoryMouseClick(menu.containerId, foundSource, 0, ClickType.PICKUP, this.mc.player);
               }
            } else {
               this.mc.gameMode.handleInventoryMouseClick(menu.containerId, foundSource, 0, ClickType.PICKUP, this.mc.player);
               for (int c = 0; c < toMove; c++) {
                  this.mc.gameMode.handleInventoryMouseClick(menu.containerId, targetSlot, 1, ClickType.PICKUP, this.mc.player);
               }
               if (!menu.getCarried().isEmpty()) {
                  this.mc.gameMode.handleInventoryMouseClick(menu.containerId, foundSource, 0, ClickType.PICKUP, this.mc.player);
               }
            }

            int afterInSlot = (menu.getSlot(targetSlot).hasItem() && ing.test(menu.getSlot(targetSlot).getItem()))
               ? menu.getSlot(targetSlot).getItem().getCount()
               : 0;
            int moved = afterInSlot - currentInSlot;
            if (moved <= 0) break;
            currentInSlot = afterInSlot;
            needed = batchCount - currentInSlot;
         }
      }

      for (int i = 0; i < grid.length && (info.gridStart + i) <= info.gridEnd; i++) {
         Ingredient ing = grid[i];
         if (ing != null && !ing.isEmpty()) {
            ItemStack inSlot = menu.getSlot(info.gridStart + i).getItem();
            if (inSlot.isEmpty() || !ing.test(inSlot) || inSlot.getCount() < batchCount) {
               return GridPlaceResult.INGREDIENTS_DEPLETED;
            }
         }
      }

      return GridPlaceResult.SUCCESS;
   }

   private GridPlaceResult placeGridManually(AbstractContainerMenu menu, RecipeHolder<CraftingRecipe> recipe, boolean table3x3, int batchCount) {
      Ingredient[] grid = CraftRecipeHelper.getGridIngredients(recipe, table3x3);
      int gridOffset = 1;
      int invStart = table3x3 ? 10 : 9;

      for (int i = 0; i < grid.length; i++) {
         Ingredient ing = grid[i];
         int targetSlot = gridOffset + i;

         if (ing == null || ing.isEmpty()) {
            if (menu.getSlot(targetSlot).hasItem()) {
               InvUtils.shiftClick().slotId(targetSlot);
            }
            continue;
         }

         if (menu.getSlot(targetSlot).hasItem()) {
            ItemStack inSlot = menu.getSlot(targetSlot).getItem();
            if (!ing.test(inSlot) || inSlot.getCount() > batchCount) {
               InvUtils.shiftClick().slotId(targetSlot);
               if (menu.getSlot(targetSlot).hasItem()) {
                  ItemStack gridItem = menu.getSlot(targetSlot).getItem();
                  for (int s = invStart; s < menu.slots.size(); s++) {
                     ItemStack sStack = menu.getSlot(s).getItem();
                     if (sStack.is(gridItem.getItem()) && sStack.getCount() < sStack.getMaxStackSize()) {
                        this.mc.gameMode.handleInventoryMouseClick(menu.containerId, targetSlot, 0, ClickType.PICKUP, this.mc.player);
                        this.mc.gameMode.handleInventoryMouseClick(menu.containerId, s, 0, ClickType.PICKUP, this.mc.player);
                        if (!menu.getCarried().isEmpty()) {
                           this.mc.gameMode.handleInventoryMouseClick(menu.containerId, targetSlot, 0, ClickType.PICKUP, this.mc.player);
                        }
                        break;
                     }
                  }
                  if (menu.getSlot(targetSlot).hasItem()) {
                     this.error("Inventory full: unable to clear crafting grid slot.");
                     this.cancelTask();
                     return GridPlaceResult.BLOCKED;
                  }
               }
            }
         }

         int currentInSlot = (menu.getSlot(targetSlot).hasItem() && ing.test(menu.getSlot(targetSlot).getItem()))
            ? menu.getSlot(targetSlot).getItem().getCount()
            : 0;
         int needed = batchCount - currentInSlot;

         while (needed > 0) {
            int foundSource = -1;
            for (int s = invStart; s < menu.slots.size(); s++) {
               if (s == targetSlot) continue;
               ItemStack stack = menu.getSlot(s).getItem();
               if (!stack.isEmpty() && ing.test(stack)) {
                  foundSource = s;
                  break;
               }
            }

            if (foundSource == -1) {
               break;
            }

            ItemStack sourceStack = menu.getSlot(foundSource).getItem();
            int available = sourceStack.getCount();
            int toMove = Math.min(needed, available);

            if (toMove == available && !menu.getSlot(targetSlot).hasItem()) {
               this.mc.gameMode.handleInventoryMouseClick(menu.containerId, foundSource, 0, ClickType.PICKUP, this.mc.player);
               this.mc.gameMode.handleInventoryMouseClick(menu.containerId, targetSlot, 0, ClickType.PICKUP, this.mc.player);
               if (!menu.getCarried().isEmpty()) {
                  this.mc.gameMode.handleInventoryMouseClick(menu.containerId, foundSource, 0, ClickType.PICKUP, this.mc.player);
               }
            } else if (toMove == available && menu.getSlot(targetSlot).hasItem() && menu.getSlot(targetSlot).getItem().is(sourceStack.getItem())) {
               this.mc.gameMode.handleInventoryMouseClick(menu.containerId, foundSource, 0, ClickType.PICKUP, this.mc.player);
               this.mc.gameMode.handleInventoryMouseClick(menu.containerId, targetSlot, 0, ClickType.PICKUP, this.mc.player);
               if (!menu.getCarried().isEmpty()) {
                  this.mc.gameMode.handleInventoryMouseClick(menu.containerId, foundSource, 0, ClickType.PICKUP, this.mc.player);
               }
            } else {
               this.mc.gameMode.handleInventoryMouseClick(menu.containerId, foundSource, 0, ClickType.PICKUP, this.mc.player);
               for (int c = 0; c < toMove; c++) {
                  this.mc.gameMode.handleInventoryMouseClick(menu.containerId, targetSlot, 1, ClickType.PICKUP, this.mc.player);
               }
               if (!menu.getCarried().isEmpty()) {
                  this.mc.gameMode.handleInventoryMouseClick(menu.containerId, foundSource, 0, ClickType.PICKUP, this.mc.player);
               }
            }

            int afterInSlot = (menu.getSlot(targetSlot).hasItem() && ing.test(menu.getSlot(targetSlot).getItem()))
               ? menu.getSlot(targetSlot).getItem().getCount()
               : 0;
            int moved = afterInSlot - currentInSlot;
            if (moved <= 0) break;
            currentInSlot = afterInSlot;
            needed = batchCount - currentInSlot;
         }
      }

      for (int i = 0; i < grid.length; i++) {
         Ingredient ing = grid[i];
         if (ing != null && !ing.isEmpty()) {
            ItemStack inSlot = menu.getSlot(gridOffset + i).getItem();
            if (inSlot.isEmpty() || !ing.test(inSlot) || inSlot.getCount() < batchCount) {
               return GridPlaceResult.INGREDIENTS_DEPLETED;
            }
         }
      }

      return GridPlaceResult.SUCCESS;
   }

   private void handleCleanup() {
      if (this.closeWhenDone.get() && !(this.mc.player.containerMenu instanceof InventoryMenu)) {
         this.mc.player.closeContainer();
      }

      if (this.placedTablePos != null && this.breakPlacedTable.get()) {
         this.mc.gameMode.startDestroyBlock(this.placedTablePos, Direction.UP);
         this.placedTablePos = null;
      }

      if (this.queue.isEmpty() && this.currentTask == null) {
         this.info("AutoCraft task complete!");
         this.state = State.IDLE;
         this.toggle();
      } else {
         this.state = State.RESOLVING;
      }
   }

   private BlockPos findNearbyTable(int radius) {
      BlockPos pPos = this.mc.player.blockPosition();
      for (int x = -radius; x <= radius; x++) {
         for (int y = -3; y <= 3; y++) {
            for (int z = -radius; z <= radius; z++) {
               BlockPos check = pPos.offset(x, y, z);
               if (this.mc.level.getBlockState(check).is(Blocks.CRAFTING_TABLE)) {
                  return check;
               }
            }
         }
      }
      return null;
   }

   public String getStatusString() {
      if (this.state == State.IDLE) return "Status: Idle";
      if (this.currentTask != null) {
         return String.format("Status: [%s] Crafting %s (%d left, queue: %d)",
            this.state, this.currentTask.item.getDescription().getString(), this.currentTask.remainingCount, this.queue.size());
      }
      return "Status: " + this.state;
   }

   @Override
   public WWidget getWidget(GuiTheme theme) {
      WVerticalList list = theme.verticalList();

      // Open AutoCraft Screen button
      WButton openScreenBtn = list.add(theme.button("Open AutoCraft UI Dashboard")).expandX().widget();
      openScreenBtn.action = () -> {
         Tab tab = Tabs.get().stream().filter(t -> t instanceof AutoCraftTab).findFirst().orElse(null);
         if (tab != null) tab.openScreen(theme);
         else this.mc.setScreen(new AutoCraftScreen(theme, this));
      };

      // Portable status indicator
      String portableStatus = BackpackAdapter.isWearingBackpack()
         ? "Portable: Traveler's Backpack (Equipped)"
         : (BackpackAdapter.findBackpackInInventory().found() ? "Portable: Backpack in Inventory" : "Portable: None (Crafting Table Required)");
      list.add(theme.label(portableStatus));

      // Status display
      list.add(theme.label(this.getStatusString()));

      // Custom item craft row
      WHorizontalList customRow = list.add(theme.horizontalList()).widget();
      WTextBox itemInput = customRow.add(theme.textBox("diamond_pickaxe")).expandX().widget();
      WIntEdit countInput = customRow.add(theme.intEdit(1, 1, 64, 1, 64)).widget();
      WButton craftBtn = customRow.add(theme.button("Craft")).widget();
      craftBtn.action = () -> {
         Item item = CraftItemResolver.resolveSingle(itemInput.get());
         if (item != null) {
            this.queueCraft(item, countInput.get());
         } else {
            this.error("Could not resolve item: %s", itemInput.get());
         }
      };

      // Cancel button
      WButton cancelBtn = list.add(theme.button("Cancel Active Task")).expandX().widget();
      cancelBtn.action = () -> {
         this.cancelTask();
         this.info("AutoCraft task cancelled.");
      };

      return list;
   }
}
