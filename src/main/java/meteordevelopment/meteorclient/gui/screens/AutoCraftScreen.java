package meteordevelopment.meteorclient.gui.screens;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import meteordevelopment.meteorclient.gui.tabs.WindowTabScreen;
import meteordevelopment.meteorclient.gui.tabs.builtin.AutoCraftTab;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WView;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.AutoCraft;
import meteordevelopment.meteorclient.systems.modules.player.autocraft.BackpackAdapter;
import meteordevelopment.meteorclient.systems.modules.player.autocraft.CraftPlanner;
import meteordevelopment.meteorclient.systems.modules.player.autocraft.CraftRecipeHelper;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

public class AutoCraftScreen extends WindowTabScreen {
   public enum CategoryFilter {
      ALL("All"),
      TOOLS("Tools"),
      ARMOR("Armor"),
      FOOD("Food"),
      BLOCKS("Blocks"),
      MODDED("Modded");

      public final String title;

      CategoryFilter(String title) {
         this.title = title;
      }
   }

   private final AutoCraft module;
   private final List<Item> craftableItems = new ArrayList<>();
   private CategoryFilter category = CategoryFilter.ALL;
   private String filterText = "";
   private Item selectedItem = null;
   private RecipeHolder<CraftingRecipe> selectedRecipe = null;
   private int recipeIndex = 0;
   private int craftQuantity = 1;

   private static final int ITEMS_PER_PAGE = 50;
   private int currentPage = 0;
   private int totalPages = 1;
   private WLabel pageLabel;
   private WButton prevBtn;
   private WButton nextBtn;

   private WTable itemTable;
   private WVerticalList detailsContainer;

   public AutoCraftScreen(GuiTheme theme, Tab tab) {
      super(theme, tab != null ? tab : getOrCreateTab());
      this.module = Modules.get().get(AutoCraft.class);
   }

   public AutoCraftScreen(GuiTheme theme, AutoCraft module) {
      super(theme, getOrCreateTab());
      this.module = module != null ? module : Modules.get().get(AutoCraft.class);
   }

   private static Tab getOrCreateTab() {
      for (Tab t : Tabs.get()) {
         if (t instanceof AutoCraftTab) return t;
      }
      return new AutoCraftTab();
   }

   @Override
   public void initWidgets() {
      loadCraftableItems();

      this.window.minWidth = 540.0;
      if (this.window.view != null) {
         this.window.view.maxHeight = Math.max(260.0, (double)Utils.getWindowHeight() - this.theme.scale(110.0));
      }

      // 1. Top Status Bar: Portable status and active task
      WHorizontalList statusBar = this.add(this.theme.horizontalList()).expandX().widget();

      String portableText;
      if (BackpackAdapter.isWearingBackpack()) {
         portableText = "Portable: Traveler's Backpack (Equipped) [Ready]";
      } else if (BackpackAdapter.findBackpackInInventory().found()) {
         portableText = "Portable: Backpack in Inventory [Ready]";
      } else {
         portableText = "Portable: Crafting Table Required";
      }
      statusBar.add(this.theme.label(portableText));

      if (this.module != null && this.module.getCraftState() != AutoCraft.State.IDLE) {
         statusBar.add(this.theme.label(" | " + this.module.getStatusString()));
         WButton cancelBtn = statusBar.add(this.theme.button("Cancel Task")).widget();
         cancelBtn.action = () -> {
            this.module.cancelTask();
            this.reload();
         };
      }

      this.add(this.theme.horizontalSeparator()).expandX();

      // 2. Search Bar (Row 1 - Full Width)
      WHorizontalList searchRow = this.add(this.theme.horizontalList()).expandX().widget();
      searchRow.add(this.theme.label("Search:"));
      WTextBox searchBox = searchRow.add(this.theme.textBox(this.filterText, "Item name or @mod (e.g. diamond, chest, @create)...")).expandX().widget();
      searchBox.action = () -> {
         this.filterText = searchBox.get().trim().toLowerCase(Locale.ROOT);
         this.currentPage = 0;
         updateItemTable();
      };

      // 3. Category Filter Pills (Row 2 - Compact, fits within ~360px)
      WHorizontalList catRow = this.add(this.theme.horizontalList()).expandX().widget();
      catRow.spacing = 4.0;
      for (CategoryFilter cat : CategoryFilter.values()) {
         String btnText = (this.category == cat ? "> " : "") + cat.title;
         WButton catBtn = catRow.add(this.theme.button(btnText)).widget();
         catBtn.action = () -> {
            this.category = cat;
            this.currentPage = 0;
            this.reload();
         };
      }

      this.add(this.theme.horizontalSeparator()).expandX();

      // 4. Main Balanced 2-Column Split
      WHorizontalList mainSplit = this.add(this.theme.horizontalList()).expandX().widget();
      mainSplit.spacing = 8.0;

      // Left Column: Item Browser
      WVerticalList leftCol = mainSplit.add(this.theme.verticalList()).widget();
      leftCol.minWidth = 240.0;

      WHorizontalList leftHeader = leftCol.add(this.theme.horizontalList()).expandX().widget();
      leftHeader.add(this.theme.label("Craftable Items:"));
      WButton refreshBtn = leftHeader.add(this.theme.button("↻")).widget();
      refreshBtn.tooltip = "Refresh Recipes";
      refreshBtn.action = () -> {
         CraftRecipeHelper.reloadCache();
         loadCraftableItems();
         this.currentPage = 0;
         updateItemTable();
      };

      WView itemScroll = leftCol.add(this.theme.view()).widget();
      itemScroll.maxHeight = 220.0;
      itemScroll.minWidth = 240.0;
      this.itemTable = itemScroll.add(this.theme.table()).widget();

      // Pagination Controls
      WHorizontalList pageNav = leftCol.add(this.theme.horizontalList()).expandX().widget();
      pageNav.spacing = 4.0;
      this.prevBtn = pageNav.add(this.theme.button("<")).widget();
      this.prevBtn.action = () -> {
         if (this.currentPage > 0) {
            this.currentPage--;
            updateItemTable();
         }
      };
      this.pageLabel = pageNav.add(this.theme.label("Page 1/1")).widget();
      this.nextBtn = pageNav.add(this.theme.button(">")).widget();
      this.nextBtn.action = () -> {
         if (this.currentPage < this.totalPages - 1) {
            this.currentPage++;
            updateItemTable();
         }
      };

      updateItemTable();

      // Right Column: Recipe Details & Action
      WVerticalList rightCol = mainSplit.add(this.theme.verticalList()).expandX().widget();
      rightCol.minWidth = 270.0;
      rightCol.add(this.theme.label("Recipe & Workshop:"));

      this.detailsContainer = rightCol.add(this.theme.verticalList()).expandX().widget();
      updateDetails();
   }

   private void loadCraftableItems() {
      this.craftableItems.clear();
      this.craftableItems.addAll(CraftRecipeHelper.getAllCraftableItems());
   }

   private void updateItemTable() {
      if (this.itemTable == null) return;
      this.itemTable.clear();

      List<Item> matched = new ArrayList<>();
      for (Item item : this.craftableItems) {
         if (matchesCategory(item) && matchesSearch(item)) {
            matched.add(item);
         }
      }

      String query = extractItemQuery(this.filterText);
      String modFilter = extractModFilter(this.filterText);
      matched.sort((a, b) -> compareRelevance(a, b, query, modFilter));

      int totalItems = matched.size();
      this.totalPages = Math.max(1, (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE));
      if (this.currentPage >= this.totalPages) {
         this.currentPage = this.totalPages - 1;
      }
      if (this.currentPage < 0) {
         this.currentPage = 0;
      }

      if (this.pageLabel != null) {
         this.pageLabel.set("Page " + (this.currentPage + 1) + "/" + this.totalPages + " (" + totalItems + ")");
      }

      int start = this.currentPage * ITEMS_PER_PAGE;
      int end = Math.min(totalItems, start + ITEMS_PER_PAGE);

      for (int i = start; i < end; i++) {
         Item item = matched.get(i);
         String name = item.getDescription().getString();
         String displayName = name.length() > 16 ? name.substring(0, 14) + "..." : name;

         this.itemTable.add(this.theme.item(item.getDefaultInstance()));

         WButton selectBtn = this.itemTable.add(this.theme.button(displayName)).widget();
         selectBtn.tooltip = name;
         selectBtn.action = () -> {
            this.selectedItem = item;
            this.selectedRecipe = CraftRecipeHelper.findBestRecipe(item);
            this.recipeIndex = 0;
            updateDetails();
         };

         // Compact Craftability Dot: fast check without creating full recursive dependency plan
         RecipeHolder<CraftingRecipe> recipe = CraftRecipeHelper.findBestRecipe(item);
         boolean canCraft = recipe != null && (CraftRecipeHelper.canSatisfy(recipe, 1, true) || CraftRecipeHelper.canSatisfyRecursive(recipe, 1, 1));
         WLabel dot = this.itemTable.add(this.theme.label(canCraft ? " ●" : " ○")).widget();
         if (canCraft) {
            dot.color = new Color(50, 255, 50, 255);
         } else {
            dot.color = new Color(160, 160, 160, 180);
         }

         this.itemTable.row();
      }

      if (totalItems == 0) {
         this.itemTable.add(this.theme.label("No items found."));
      }
   }

   private String extractModFilter(String filter) {
      if (filter.startsWith("@")) {
         int space = filter.indexOf(' ');
         if (space != -1) {
            return filter.substring(1, space).trim().toLowerCase(Locale.ROOT);
         }
         return filter.substring(1).trim().toLowerCase(Locale.ROOT);
      }
      return "";
   }

   private String extractItemQuery(String filter) {
      if (filter.startsWith("@")) {
         int space = filter.indexOf(' ');
         if (space != -1) {
            return filter.substring(space + 1).trim().toLowerCase(Locale.ROOT);
         }
         return "";
      }
      return filter.trim().toLowerCase(Locale.ROOT);
   }

   private int calculateRelevanceScore(Item item, String query, String modFilter) {
      if (query.isEmpty()) {
         return 0;
      }

      ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
      String name = item.getDescription().getString().toLowerCase(Locale.ROOT);
      String path = id.getPath().toLowerCase(Locale.ROOT);

      // 1. Exact match on display name or path
      if (name.equals(query) || path.equals(query)) {
         return 0;
      }

      // 2. Prefix match
      if (name.startsWith(query) || path.startsWith(query)) {
         return 10;
      }

      // 3. Word boundary match in display name
      if ((" " + name + " ").contains(" " + query + " ")) {
         return 20;
      }

      // 4. Substring in path
      if (path.contains(query)) {
         return 30;
      }

      // 5. Substring in display name
      if (name.contains(query)) {
         return 35;
      }

      return 50;
   }

   private int getModPriority(Item item) {
      ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
      String ns = id.getNamespace();
      if (ns.equals("minecraft")) return 0;
      if (ns.equals("create")) return 1;
      return 2;
   }

   private int compareRelevance(Item a, Item b, String query, String modFilter) {
      int scoreA = calculateRelevanceScore(a, query, modFilter);
      int scoreB = calculateRelevanceScore(b, query, modFilter);
      if (scoreA != scoreB) {
         return Integer.compare(scoreA, scoreB);
      }

      if (!modFilter.isEmpty()) {
         ResourceLocation idA = BuiltInRegistries.ITEM.getKey(a);
         ResourceLocation idB = BuiltInRegistries.ITEM.getKey(b);
         boolean exactModA = idA.getNamespace().equalsIgnoreCase(modFilter);
         boolean exactModB = idB.getNamespace().equalsIgnoreCase(modFilter);
         if (exactModA != exactModB) {
            return exactModA ? -1 : 1;
         }
      } else {
         int modA = getModPriority(a);
         int modB = getModPriority(b);
         if (modA != modB) {
            return Integer.compare(modA, modB);
         }
      }

      String nameA = a.getDescription().getString();
      String nameB = b.getDescription().getString();
      if (nameA.length() != nameB.length()) {
         return Integer.compare(nameA.length(), nameB.length());
      }

      return nameA.compareToIgnoreCase(nameB);
   }

   private boolean matchesSearch(Item item) {
      if (this.filterText.isEmpty()) return true;
      ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
      String namespace = id.getNamespace().toLowerCase(Locale.ROOT);
      String path = id.getPath().toLowerCase(Locale.ROOT);
      String name = item.getDescription().getString().toLowerCase(Locale.ROOT);

      if (this.filterText.startsWith("@")) {
         String modFilter = extractModFilter(this.filterText);
         String itemQuery = extractItemQuery(this.filterText);

         if (!namespace.contains(modFilter)) return false;
         if (itemQuery.isEmpty()) return true;

         return name.contains(itemQuery) || path.contains(itemQuery);
      }

      return name.contains(this.filterText) || path.contains(this.filterText) || id.toString().toLowerCase(Locale.ROOT).contains(this.filterText);
   }

   private boolean matchesCategory(Item item) {
      if (this.category == CategoryFilter.ALL) return true;

      ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
      String path = id.getPath().toLowerCase(Locale.ROOT);

      return switch (this.category) {
         case TOOLS -> path.contains("pickaxe") || path.contains("sword") || path.contains("axe") || path.contains("shovel") || path.contains("hoe") || path.contains("bow") || path.contains("mace") || path.contains("shield");
         case ARMOR -> path.contains("helmet") || path.contains("chestplate") || path.contains("leggings") || path.contains("boots") || path.contains("cap") || path.contains("tunic") || path.contains("pants");
         case FOOD -> item.components().has(DataComponents.FOOD) || path.contains("soup") || path.contains("bread") || path.contains("cake") || path.contains("pie") || path.contains("apple") || path.contains("cooked") || path.contains("stew");
         case BLOCKS -> item instanceof BlockItem || path.contains("brick") || path.contains("plank") || path.contains("door") || path.contains("slab") || path.contains("stair") || path.contains("chest") || path.contains("glass") || path.contains("torch") || path.contains("lantern");
         case MODDED -> !id.getNamespace().equals("minecraft");
         default -> true;
      };
   }

   private void updateDetails() {
      if (this.detailsContainer == null) return;
      this.detailsContainer.clear();

      if (this.selectedItem == null) {
         this.detailsContainer.add(this.theme.label("Select an item to view recipe & craft."));
         return;
      }

      // Title & Preview Row
      WHorizontalList titleRow = this.detailsContainer.add(this.theme.horizontalList()).widget();
      titleRow.add(this.theme.item(this.selectedItem.getDefaultInstance()));
      titleRow.add(this.theme.label(this.selectedItem.getDescription().getString(), true));
      titleRow.add(this.theme.label(" [" + BuiltInRegistries.ITEM.getKey(this.selectedItem).getNamespace() + "]"));

      List<RecipeHolder<CraftingRecipe>> candidates = CraftRecipeHelper.getCandidateRecipes(this.selectedItem);
      if (candidates.isEmpty()) {
         this.selectedRecipe = null;
      } else {
         if (this.selectedRecipe == null || !candidates.contains(this.selectedRecipe)) {
            this.selectedRecipe = candidates.get(0);
            this.recipeIndex = 0;
         } else {
            this.recipeIndex = Math.max(0, candidates.indexOf(this.selectedRecipe));
         }
      }

      if (this.selectedRecipe == null) {
         this.detailsContainer.add(this.theme.label("No crafting recipe available for this item."));
         return;
      }

      // Recipe Selector Row if multiple recipes exist
      if (candidates.size() > 1) {
         WHorizontalList navRow = this.detailsContainer.add(this.theme.horizontalList()).widget();
         navRow.add(this.theme.label("Recipe:"));

         WButton prevBtn = navRow.add(this.theme.button("<")).widget();
         prevBtn.action = () -> {
            if (candidates.isEmpty()) return;
            this.recipeIndex = (this.recipeIndex - 1 + candidates.size()) % candidates.size();
            this.selectedRecipe = candidates.get(this.recipeIndex);
            updateDetails();
         };

         navRow.add(this.theme.label((this.recipeIndex + 1) + " of " + candidates.size()));

         WButton nextBtn = navRow.add(this.theme.button(">")).widget();
         nextBtn.action = () -> {
            if (candidates.isEmpty()) return;
            this.recipeIndex = (this.recipeIndex + 1) % candidates.size();
            this.selectedRecipe = candidates.get(this.recipeIndex);
            updateDetails();
         };
      }

      boolean is2x2 = CraftRecipeHelper.is2x2(this.selectedRecipe);
      int yield = CraftRecipeHelper.getResultCount(this.selectedRecipe);
      this.detailsContainer.add(this.theme.label("Grid: " + (is2x2 ? "2x2 (Inventory)" : "3x3 (Table / Backpack)") + " | Yield: " + yield));

      // 3x3 Recipe Grid Visual
      WTable gridTable = this.detailsContainer.add(this.theme.table()).widget();
      Ingredient[] grid = CraftRecipeHelper.getGridIngredients(this.selectedRecipe, !is2x2);

      int dim = is2x2 ? 2 : 3;
      for (int r = 0; r < dim; r++) {
         for (int c = 0; c < dim; c++) {
            Ingredient ing = grid[r * dim + c];
            if (ing != null && !ing.isEmpty()) {
               ItemStack[] matching = ing.getItems();
               if (matching.length > 0) {
                  gridTable.add(this.theme.item(matching[0]));
               } else {
                  gridTable.add(this.theme.label("[?]"));
               }
            } else {
               gridTable.add(this.theme.label("[   ]"));
            }
         }
         gridTable.row();
      }

      // Compute full craft tree plan once
      CraftPlanner.CraftPlan plan = CraftPlanner.createPlan(this.selectedItem, this.craftQuantity, this.selectedRecipe);

      // Required Materials Checklist
      this.detailsContainer.add(this.theme.label("Ingredients:"));
      WTable matsTable = this.detailsContainer.add(this.theme.table()).widget();

      int craftsNeeded = (int) Math.ceil((double) this.craftQuantity / yield);
      var required = CraftRecipeHelper.getRequiredItems(this.selectedRecipe, craftsNeeded);

      for (var entry : required.entrySet()) {
         Item reqItem = entry.getKey();
         int reqCount = entry.getValue();
         int haveCount = CraftRecipeHelper.countInInventory(reqItem);
         int bagCount = BackpackAdapter.countInAllBackpacks(reqItem);

         matsTable.add(this.theme.item(reqItem.getDefaultInstance()));
         matsTable.add(this.theme.label(reqItem.getDescription().getString()));
         matsTable.add(this.theme.label("Need: " + reqCount));

         String haveText = "Have: " + haveCount;
         if (bagCount > 0) {
            haveText += " (" + bagCount + " in backpack)";
         }

         int equivCount = CraftRecipeHelper.countEquivalentInInventory(reqItem);
         int decompSurplus = equivCount - haveCount;
         if (haveCount < reqCount && decompSurplus > 0) {
            RecipeHolder<CraftingRecipe> decomp = CraftRecipeHelper.findDecompressionRecipe(reqItem);
            Item source = decomp != null ? CraftRecipeHelper.getSingleIngredientItem(decomp) : null;
            String sourceName = source != null ? source.getDescription().getString() : "Block";
            haveText += String.format(" (+%d from %s)", decompSurplus, sourceName);
         }

         boolean canSatisfyIng = (haveCount >= reqCount || equivCount >= reqCount);
         if (!canSatisfyIng) {
            boolean producedInPlan = false;
            if (plan != null && plan.steps != null) {
               for (CraftPlanner.CraftStep step : plan.steps) {
                  if (step.resultItem == reqItem) {
                     producedInPlan = true;
                     break;
                  }
               }
            }
            if (producedInPlan || (plan != null && plan.isSatisfied && !plan.missingRawMaterials.containsKey(reqItem))) {
               canSatisfyIng = true;
               haveText += " (Craftable)";
            }
         }

         WLabel haveWidget = matsTable.add(this.theme.label(haveText)).widget();
         if (canSatisfyIng) {
            haveWidget.color = new Color(50, 255, 50, 255);
         } else {
            haveWidget.color = new Color(255, 75, 75, 255);
         }
         matsTable.row();
      }

      // Craft Tree & Base Materials (if intermediate steps exist)
      if (plan != null && plan.steps.size() > 1) {
         this.detailsContainer.add(this.theme.horizontalSeparator()).expandX();
         this.detailsContainer.add(this.theme.label("Craft Tree (" + plan.steps.size() + " Steps):", true));
         WTable treeTable = this.detailsContainer.add(this.theme.table()).widget();
         int stepIdx = 1;
         for (CraftPlanner.CraftStep step : plan.steps) {
            treeTable.add(this.theme.label(stepIdx + "."));
            treeTable.add(this.theme.item(step.resultItem.getDefaultInstance()));
            String stepDesc = step.craftCount + "x craft -> " + step.yieldProduced + "x " + step.resultItem.getDescription().getString();
            treeTable.add(this.theme.label(stepDesc));
            treeTable.row();
            stepIdx++;
         }

         if (!plan.rawMaterialsNeeded.isEmpty() || !plan.missingRawMaterials.isEmpty()) {
            this.detailsContainer.add(this.theme.label("Base Materials Summary:"));
            WTable baseTable = this.detailsContainer.add(this.theme.table()).widget();
            for (var entry : plan.rawMaterialsNeeded.entrySet()) {
               baseTable.add(this.theme.item(entry.getKey().getDefaultInstance()));
               baseTable.add(this.theme.label(entry.getKey().getDescription().getString()));
               baseTable.add(this.theme.label("Need: " + entry.getValue()));
               baseTable.row();
            }
            for (var entry : plan.missingRawMaterials.entrySet()) {
               baseTable.add(this.theme.item(entry.getKey().getDefaultInstance()));
               baseTable.add(this.theme.label(entry.getKey().getDescription().getString()));
               WLabel missLabel = baseTable.add(this.theme.label("Missing: " + entry.getValue())).widget();
               missLabel.color = new Color(255, 75, 75, 255);
               baseTable.row();
            }
         }
      }

      // Quantity Row
      WHorizontalList qtyRow = this.detailsContainer.add(this.theme.horizontalList()).widget();
      qtyRow.add(this.theme.label("Quantity:"));

      WButton minusBtn = qtyRow.add(this.theme.button("-")).widget();
      minusBtn.action = () -> {
         if (this.craftQuantity > 1) {
            this.craftQuantity--;
            updateDetails();
         }
      };

      WIntEdit intEdit = qtyRow.add(this.theme.intEdit(this.craftQuantity, 1, 512, 1, 64)).widget();
      intEdit.action = () -> {
         this.craftQuantity = intEdit.get();
         updateDetails();
      };

      WButton plusBtn = qtyRow.add(this.theme.button("+")).widget();
      plusBtn.action = () -> {
         this.craftQuantity++;
         updateDetails();
      };

      // Big Craft Action Button
      WButton craftBtn = this.detailsContainer.add(this.theme.button("Craft " + this.craftQuantity + "x " + this.selectedItem.getDescription().getString())).expandX().widget();
      craftBtn.action = () -> {
         if (this.module != null) {
            this.module.queueCraft(this.selectedItem, this.selectedRecipe, this.craftQuantity);
         }
         this.parent = null;
         this.onClose();
         MeteorClient.mc.setScreen(null);
      };
   }
}
