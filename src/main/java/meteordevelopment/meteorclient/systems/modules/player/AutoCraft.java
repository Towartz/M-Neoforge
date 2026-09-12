package meteordevelopment.meteorclient.systems.modules.player;

import baritone.api.pathing.goals.GoalGetToBlock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
      NAVIGATING_TO_CHEST,
      LOOTING_CHEST,
      CRAFTING_TABLE_PLACING,
      NAVIGATING_TO_TABLE,
      OPENING_TABLE,
      CRAFTING,
      CLEANUP
   }

   public static class CraftTask {
      public final Item item;
      public int remainingCount;

      public CraftTask(Item item, int count) {
         this.item = item;
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
         .defaultValue(true)
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
      ContainerSearcher.stopNavigation();
      this.containerSearcher.reset();
   }

   public void queueCraft(Item item, int count) {
      if (item == null || count <= 0) return;
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
         case NAVIGATING_TO_CHEST -> handleNavigatingToChest();
         case LOOTING_CHEST -> handleLootingChest();
         case CRAFTING_TABLE_PLACING -> handlePlacingTable();
         case NAVIGATING_TO_TABLE -> handleNavigatingToTable();
         case OPENING_TABLE -> handleOpeningTable();
         case CRAFTING -> handleCrafting();
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

      BackpackAdapter.BackpackCraftInfo info = BackpackAdapter.getBackpackCraftInfo(menu);
      int storageStart = info.storageStart != -1 ? info.storageStart : 0;
      int storageEnd = info.storageEnd != -1 ? info.storageEnd : Math.max(0, menu.slots.size() - 37);

      boolean withdrewAny = false;
      for (int i = storageStart; i <= Math.min(storageEnd, menu.slots.size() - 1); i++) {
         Slot slot = menu.slots.get(i);
         ItemStack stack = slot.getItem();
         if (stack.isEmpty()) continue;

         if (neededInDirect.containsKey(stack.getItem())) {
            InvUtils.shiftClick().slotId(i);
            this.info("Withdrew (highlight)%dx %s(default) from backpack.", stack.getCount(), stack.getHoverName().getString());
            withdrewAny = true;
            this.timer = this.craftDelay.get() + 2;
            return;
         }
      }

      if (!withdrewAny) {
         // Could not withdraw more items (e.g. inventory full)
         // Can we at least craft 1 item with what we currently have?
         if (CraftRecipeHelper.canSatisfyDirect(this.activeRecipe, 1)) {
            this.info("Inventory full, but enough materials for partial craft. Proceeding...");
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
         // All ingredients available (either in direct inventory or backpack)!
         boolean hasDirect = CraftRecipeHelper.hasAllInDirectInventory(this.activeRecipe, craftsNeeded);
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

         if (is2x2 && hasDirect) {
            // Player has all materials in direct inventory and recipe fits 2x2 grid -> craft directly!
            this.state = State.CRAFTING;
            return;
         }

         // If backpack menu is already open:
         if (BackpackAdapter.isBackpackMenu(this.mc.player.containerMenu)) {
            BackpackAdapter.BackpackCraftInfo info = BackpackAdapter.getBackpackCraftInfo(this.mc.player.containerMenu);
            if (info.valid && info.resultSlot != -1) {
               this.state = State.CRAFTING;
               return;
            } else if (BackpackAdapter.hasCraftingUpgrade(this.mc.player.containerMenu)) {
               this.state = State.OPENING_BACKPACK;
               return;
            } else if (!hasDirect) {
               this.state = State.WITHDRAWING_FROM_BACKPACK;
               return;
            }
         }

         // If at a vanilla crafting table and all ingredients are in direct inventory, craft directly
         if (this.mc.player.containerMenu instanceof CraftingMenu && hasDirect) {
            this.state = State.CRAFTING;
            return;
         }

         // If materials are in backpack: open backpack to withdraw or craft!
         if (!hasDirect) {
            if (BackpackAdapter.hasPortableCraftingAvailable()) {
               if (BackpackAdapter.openPortableCrafting()) {
                  this.timer = this.craftDelay.get() + 3;
                  this.state = State.OPENING_BACKPACK;
                  this.chestWaitTicks = 0;
                  this.backpackTabWaitTicks = 0;
                  this.info("Opening backpack to retrieve crafting materials...");
                  return;
               }
            }
            this.error("Materials for (highlight)%s(default) are in backpack, but backpack could not be opened.", this.currentTask.item.getDescription().getString());
            this.cancelTask();
            return;
         }

         // All materials are in direct inventory, but recipe needs 3x3 table:
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

         // If materials are in direct inventory and 3x3 table is needed:
         if (hasDirect) {
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
         } else {
            // Materials are in backpack, but portable backpack could not be opened
            this.error("Materials for (highlight)%s(default) are in backpack, but backpack could not be opened.", this.currentTask.item.getDescription().getString());
            this.cancelTask();
         }
         return;
      }

      // Ingredients missing - use CraftPlanner to build full dependency tree
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

      // Missing ingredients not craftable from inventory - search chests
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
               this.info("Searching chest at [%d, %d, %d]...", this.targetChestPos.getX(), this.targetChestPos.getY(), this.targetChestPos.getZ());
            } else {
               this.warning("Chest at [%d, %d, %d] is out of reach (auto-walk disabled).", this.targetChestPos.getX(), this.targetChestPos.getY(), this.targetChestPos.getZ());
               this.cancelTask();
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
      this.error("Missing materials for %s: %s.", this.currentTask.item.getDescription().getString(), missingStr);
      this.cancelTask();
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
         this.info("Withdrew (highlight)%dx %s(default) from chest.", directTaken, this.currentTask.item.getDescription().getString());
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
               int expectedYield = currentResult.getCount();
               int beforeCount = CraftRecipeHelper.countInInventory(this.currentTask.item);
               InvUtils.shiftClick().slotId(info.resultSlot);
               int afterCount = CraftRecipeHelper.countInInventory(this.currentTask.item);
               int actuallyCrafted = afterCount - beforeCount;
               if (actuallyCrafted <= 0) {
                  if (menu.getSlot(info.resultSlot).getItem().isEmpty()) {
                     actuallyCrafted = expectedYield;
                  } else {
                     this.error("Inventory full: cannot collect crafted item from backpack.");
                     this.cancelTask();
                     return;
                  }
               }

               this.currentTask.remainingCount -= actuallyCrafted;
               this.info("Crafted (highlight)%dx %s(default) via backpack (remaining: %d).", actuallyCrafted, this.currentTask.item.getDescription().getString(), Math.max(0, this.currentTask.remainingCount));
               this.timer = this.craftDelay.get();

               if (this.currentTask.remainingCount <= 0) {
                  this.currentTask = null;
                  this.state = State.RESOLVING;
               }
               return;
            }

            // Populate backpack crafting grid
            placeBackpackGrid(menu, info, this.activeRecipe);
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
         int expectedYield = currentResult.getCount();
         int beforeCount = CraftRecipeHelper.countInInventory(this.currentTask.item);
         InvUtils.shiftClick().slotId(0);
         int afterCount = CraftRecipeHelper.countInInventory(this.currentTask.item);
         int actuallyCrafted = afterCount - beforeCount;
         if (actuallyCrafted <= 0) {
            if (menu.getSlot(0).getItem().isEmpty()) {
               actuallyCrafted = expectedYield;
            } else {
               this.error("Inventory full: cannot collect crafted item.");
               this.cancelTask();
               return;
            }
         }

         this.currentTask.remainingCount -= actuallyCrafted;
         this.info("Crafted (highlight)%dx %s(default) (remaining: %d).", actuallyCrafted, this.currentTask.item.getDescription().getString(), Math.max(0, this.currentTask.remainingCount));
         this.timer = this.craftDelay.get();

         if (this.currentTask.remainingCount <= 0) {
            this.currentTask = null;
            this.state = State.RESOLVING;
         }
         return;
      }

      // Place recipe into crafting grid via direct slot clicks
      placeGridManually(menu, this.activeRecipe, table3x3);

      this.timer = this.craftDelay.get() + 1;
   }

   private void placeBackpackGrid(AbstractContainerMenu menu, BackpackAdapter.BackpackCraftInfo info, RecipeHolder<CraftingRecipe> recipe) {
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
            if (ing.test(menu.getSlot(targetSlot).getItem())) {
               continue;
            }
            InvUtils.shiftClick().slotId(targetSlot);
            if (menu.getSlot(targetSlot).hasItem()) {
               this.error("Inventory/backpack full: unable to clear crafting grid slot.");
               this.cancelTask();
               return;
            }
         }

         int foundSource = -1;
         for (int s : sourceSlots) {
            ItemStack stack = menu.getSlot(s).getItem();
            if (ing.test(stack)) {
               foundSource = s;
               break;
            }
         }

         if (foundSource != -1) {
            this.mc.gameMode.handleInventoryMouseClick(menu.containerId, foundSource, 0, ClickType.PICKUP, this.mc.player);
            this.mc.gameMode.handleInventoryMouseClick(menu.containerId, targetSlot, 1, ClickType.PICKUP, this.mc.player);

            for (int j = i + 1; j < grid.length && (info.gridStart + j) <= info.gridEnd; j++) {
               if (grid[j] != null && !grid[j].isEmpty() && !menu.getSlot(info.gridStart + j).hasItem()) {
                  ItemStack carried = menu.getCarried();
                  if (carried.isEmpty()) break;
                  if (grid[j].test(carried)) {
                     this.mc.gameMode.handleInventoryMouseClick(menu.containerId, info.gridStart + j, 1, ClickType.PICKUP, this.mc.player);
                  }
               }
            }

            if (!menu.getCarried().isEmpty()) {
               this.mc.gameMode.handleInventoryMouseClick(menu.containerId, foundSource, 0, ClickType.PICKUP, this.mc.player);
               if (!menu.getCarried().isEmpty()) {
                  for (int s : sourceSlots) {
                     if (!menu.getSlot(s).hasItem()) {
                        this.mc.gameMode.handleInventoryMouseClick(menu.containerId, s, 0, ClickType.PICKUP, this.mc.player);
                        break;
                     }
                  }
               }
            }
         }
      }
   }

   private void placeGridManually(AbstractContainerMenu menu, RecipeHolder<CraftingRecipe> recipe, boolean table3x3) {
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
            if (ing.test(menu.getSlot(targetSlot).getItem())) {
               continue;
            }
            InvUtils.shiftClick().slotId(targetSlot);
            if (menu.getSlot(targetSlot).hasItem()) {
               this.error("Inventory full: unable to clear crafting grid slot.");
               this.cancelTask();
               return;
            }
         }

         int foundSource = -1;
         for (int s = invStart; s < menu.slots.size(); s++) {
            ItemStack stack = menu.getSlot(s).getItem();
            if (ing.test(stack)) {
               foundSource = s;
               break;
            }
         }

         if (foundSource != -1) {
            this.mc.gameMode.handleInventoryMouseClick(menu.containerId, foundSource, 0, ClickType.PICKUP, this.mc.player);
            this.mc.gameMode.handleInventoryMouseClick(menu.containerId, targetSlot, 1, ClickType.PICKUP, this.mc.player);

            for (int j = i + 1; j < grid.length; j++) {
               if (grid[j] != null && !grid[j].isEmpty() && !menu.getSlot(gridOffset + j).hasItem()) {
                  ItemStack carried = menu.getCarried();
                  if (carried.isEmpty()) break;
                  if (grid[j].test(carried)) {
                     this.mc.gameMode.handleInventoryMouseClick(menu.containerId, gridOffset + j, 1, ClickType.PICKUP, this.mc.player);
                  }
               }
            }

            if (!menu.getCarried().isEmpty()) {
               this.mc.gameMode.handleInventoryMouseClick(menu.containerId, foundSource, 0, ClickType.PICKUP, this.mc.player);
               if (!menu.getCarried().isEmpty()) {
                  for (int s = invStart; s < menu.slots.size(); s++) {
                     if (!menu.getSlot(s).hasItem()) {
                        this.mc.gameMode.handleInventoryMouseClick(menu.containerId, s, 0, ClickType.PICKUP, this.mc.player);
                        break;
                     }
                  }
               }
            }
         }
      }
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
