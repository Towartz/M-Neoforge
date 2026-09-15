package meteordevelopment.meteorclient.systems.modules.player.autocraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.orbit.EventHandler;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

public class CraftRecipeHelper {
   private static RecipeManager lastRecipeManager = null;
   private static int lastRecipeCount = -1;
   private static final Map<Item, List<RecipeHolder<CraftingRecipe>>> RECIPES_BY_RESULT = new HashMap<>();
   private static final Set<Item> ALL_CRAFTABLE_ITEMS = new LinkedHashSet<>();
   private static final Map<Ingredient, List<Item>> INGREDIENT_ITEMS_CACHE = new ConcurrentHashMap<>();
   private static boolean listenerRegistered = false;

   public static final Set<Item> CANONICAL_DEFAULTS = Set.of(
      Items.WHITE_WOOL, Items.WHITE_BED, Items.WHITE_CARPET,
      Items.OAK_PLANKS, Items.OAK_LOG, Items.OAK_WOOD,
      Items.GLASS, Items.TERRACOTTA, Items.CANDLE, Items.WHITE_CONCRETE_POWDER
   );

   private CraftRecipeHelper() {
   }

   public static List<Item> getItemsForIngredient(Ingredient ingredient) {
      if (ingredient == null || ingredient.isEmpty()) return Collections.emptyList();
      return INGREDIENT_ITEMS_CACHE.computeIfAbsent(ingredient, ing -> {
         ItemStack[] stacks = ing.getItems();
         if (stacks == null || stacks.length == 0) return Collections.emptyList();
         List<Item> items = new ArrayList<>(stacks.length);
         for (ItemStack st : stacks) {
            if (st != null && !st.isEmpty()) {
               Item it = st.getItem();
               if (!items.contains(it)) {
                  items.add(it);
               }
            }
         }
         return Collections.unmodifiableList(items);
      });
   }

   private static synchronized boolean ensureCache() {
      if (MeteorClient.mc.level == null) {
         clearCache();
         return false;
      }

      if (!listenerRegistered) {
         listenerRegistered = true;
         MeteorClient.EVENT_BUS.subscribe(CraftRecipeHelper.class);
      }

      RecipeManager manager = MeteorClient.mc.level.getRecipeManager();
      int currentCount = (manager != null) ? manager.getRecipes().size() : -1;
      if (manager != lastRecipeManager || currentCount != lastRecipeCount) {
         rebuildCache(manager);
      }
      return true;
   }

   private static synchronized void rebuildCache(RecipeManager manager) {
      RECIPES_BY_RESULT.clear();
      ALL_CRAFTABLE_ITEMS.clear();
      lastRecipeManager = manager;
      lastRecipeCount = (manager != null) ? manager.getRecipes().size() : -1;

      if (manager == null || MeteorClient.mc.level == null) return;

      var registryAccess = MeteorClient.mc.level.registryAccess();
      Set<ResourceLocation> seen = new HashSet<>();

      // 1. All standard crafting recipes registered under RecipeType.CRAFTING
      for (RecipeHolder<CraftingRecipe> holder : manager.getAllRecipesFor(RecipeType.CRAFTING)) {
         seen.add(holder.id());
         ItemStack result = holder.value().getResultItem(registryAccess);
         if (!result.isEmpty()) {
            Item item = result.getItem();
            RECIPES_BY_RESULT.computeIfAbsent(item, k -> new ArrayList<>()).add(holder);
            ALL_CRAFTABLE_ITEMS.add(item);
         }
      }

      // 2. Any additional recipes in RecipeManager whose value implements CraftingRecipe
      for (RecipeHolder<?> holder : manager.getRecipes()) {
         if (holder.value() instanceof CraftingRecipe craftingRecipe && !seen.contains(holder.id())) {
            seen.add(holder.id());
            ItemStack result = craftingRecipe.getResultItem(registryAccess);
            if (!result.isEmpty()) {
               Item item = result.getItem();
               @SuppressWarnings("unchecked")
               RecipeHolder<CraftingRecipe> castHolder = (RecipeHolder<CraftingRecipe>) holder;
               RECIPES_BY_RESULT.computeIfAbsent(item, k -> new ArrayList<>()).add(castHolder);
               ALL_CRAFTABLE_ITEMS.add(item);
            }
         }
      }
   }

   @EventHandler
   private static void onGameLeft(GameLeftEvent event) {
      clearCache();
   }

   @EventHandler
   private static void onPacketReceive(PacketEvent.Receive event) {
      if (event.packet instanceof ClientboundUpdateRecipesPacket) {
         clearCache();
      }
   }

   public static Item getCraftingRemainder(Item item) {
      if (item == null) return null;
      Item remainder = item.getCraftingRemainingItem();
      if (remainder != null && remainder != Items.AIR) {
         return remainder;
      }
      if (item == Items.MILK_BUCKET || item == Items.WATER_BUCKET || item == Items.LAVA_BUCKET) return Items.BUCKET;
      if (item == Items.HONEY_BOTTLE || item == Items.DRAGON_BREATH) return Items.GLASS_BOTTLE;
      if (item == Items.MUSHROOM_STEW || item == Items.BEETROOT_SOUP || item == Items.RABBIT_STEW || item == Items.SUSPICIOUS_STEW) return Items.BOWL;
      return null;
   }

   public static synchronized void clearCache() {
      RECIPES_BY_RESULT.clear();
      ALL_CRAFTABLE_ITEMS.clear();
      INGREDIENT_ITEMS_CACHE.clear();
      lastRecipeManager = null;
      lastRecipeCount = -1;
   }

   public static synchronized void reloadCache() {
      if (MeteorClient.mc.level != null) {
         rebuildCache(MeteorClient.mc.level.getRecipeManager());
      } else {
         clearCache();
      }
   }

   public static Set<Item> getAllCraftableItems() {
      if (!ensureCache()) {
         return Collections.emptySet();
      }
      return Collections.unmodifiableSet(ALL_CRAFTABLE_ITEMS);
   }

   public static List<RecipeHolder<CraftingRecipe>> getRecipesFor(Item item) {
      if (item == null || !ensureCache()) {
         return Collections.emptyList();
      }
      return RECIPES_BY_RESULT.getOrDefault(item, Collections.emptyList());
   }

   public static boolean is1to1Conversion(RecipeHolder<CraftingRecipe> holder) {
      if (holder == null) return false;
      if (getResultCount(holder) != 1) return false;
      int nonEmpty = 0;
      for (Ingredient ing : holder.value().getIngredients()) {
         if (!ing.isEmpty()) {
            nonEmpty++;
            if (nonEmpty > 1) return false;
         }
      }
      return nonEmpty == 1;
   }

   public static boolean isRecoloringRecipe(RecipeHolder<CraftingRecipe> holder) {
      if (holder == null || MeteorClient.mc.level == null) return false;
      ItemStack resultStack = holder.value().getResultItem(MeteorClient.mc.level.registryAccess());
      if (resultStack.isEmpty()) return false;
      Item resultItem = resultStack.getItem();
      ResourceLocation resId = BuiltInRegistries.ITEM.getKey(resultItem);
      String resPath = resId.getPath();

      String familySuffix = null;
      if (resPath.endsWith("_wool")) familySuffix = "_wool";
      else if (resPath.endsWith("_bed")) familySuffix = "_bed";
      else if (resPath.endsWith("_carpet")) familySuffix = "_carpet";
      else if (resPath.endsWith("_candle")) familySuffix = "_candle";
      else if (resPath.endsWith("_stained_glass")) familySuffix = "glass";
      else if (resPath.endsWith("_terracotta")) familySuffix = "terracotta";
      else if (resPath.endsWith("_concrete_powder")) familySuffix = "concrete_powder";
      else if (resPath.endsWith("_shulker_box")) familySuffix = "shulker_box";

      if (familySuffix == null) return false;

      for (Ingredient ing : holder.value().getIngredients()) {
         if (ing.isEmpty()) continue;
         for (Item inItem : getItemsForIngredient(ing)) {
            ResourceLocation inId = BuiltInRegistries.ITEM.getKey(inItem);
            String inPath = inId.getPath();
            if (inPath.endsWith(familySuffix) || inPath.equals(familySuffix) || inPath.equals("candle") || inPath.equals("glass") || inPath.equals("terracotta")) {
               return true;
            }
         }
      }

      return false;
   }

   public static boolean isDecompressionRecipe(RecipeHolder<CraftingRecipe> holder) {
      if (holder == null) return false;
      int yield = getResultCount(holder);
      if (yield <= 1) return false;
      Item source = getSingleIngredientItem(holder);
      return source != null;
   }

   public static List<RecipeHolder<CraftingRecipe>> getCandidateRecipes(Item item) {
      return getCandidateRecipes(item, null);
   }

   public static List<RecipeHolder<CraftingRecipe>> getCandidateRecipes(Item item, Map<Item, Integer> pool) {
      List<RecipeHolder<CraftingRecipe>> raw = getRecipesFor(item);
      if (raw.isEmpty()) return Collections.emptyList();

      Map<Item, Integer> workingPool = (pool != null) ? pool : getAvailableInventoryPool(false);

      List<RecipeHolder<CraftingRecipe>> candidates = new ArrayList<>();
      for (RecipeHolder<CraftingRecipe> r : raw) {
         // Rule 1: Decompression is ONLY considered if the source compressed block is already owned in pool!
         if (isDecompressionRecipe(r)) {
            Item src = getSingleIngredientItem(r);
            if (src == null || workingPool.getOrDefault(src, 0) <= 0) {
               continue;
            }
         }

         // Rule 2: 1:1 conversions and recoloring/dyeing are ONLY considered if the player already owns an input item in pool!
         if (is1to1Conversion(r) || isRecoloringRecipe(r)) {
            boolean hasInputInPool = false;
            for (Ingredient ing : r.value().getIngredients()) {
               if (ing.isEmpty()) continue;
               for (Item inIt : getItemsForIngredient(ing)) {
                  if (workingPool.getOrDefault(inIt, 0) > 0) {
                     hasInputInPool = true;
                     break;
                  }
               }
               if (hasInputInPool) break;
            }
            if (!hasInputInPool) {
               continue;
            }
         }

         candidates.add(r);
      }

      if (candidates.isEmpty()) {
         return Collections.emptyList();
      }

      if (candidates.size() == 1) return candidates;

      Map<RecipeHolder<CraftingRecipe>, Integer> scores = new HashMap<>(candidates.size());
      for (RecipeHolder<CraftingRecipe> r : candidates) {
         scores.put(r, computeRecipePriorityScore(r, workingPool));
      }

      candidates.sort((a, b) -> {
         int sA = scores.getOrDefault(a, 0);
         int sB = scores.getOrDefault(b, 0);
         if (sA != sB) {
            return Integer.compare(sB, sA);
         }
         return 0;
      });

      return candidates;
   }

   private static int computeRecipePriorityScore(RecipeHolder<CraftingRecipe> holder, Map<Item, Integer> pool) {
      if (holder == null) return -100000;
      int yield = getResultCount(holder);
      int score = yield;

      // 1. Direct satisfaction in pool
      boolean sat = canSatisfy(holder, 1, false, pool);
      if (sat) {
         score += 100000;
      } else {
         // 2. Decompression with owned storage block
         Item src = getSingleIngredientItem(holder);
         int count = (src != null && pool != null) ? pool.getOrDefault(src, 0) : (src != null ? countInInventory(src) : 0);
         if (isDecompressionRecipe(holder)) {
            if (count > 0) {
               score += 50000;
            } else {
               return -100000;
            }
         } else if (canSatisfy(holder, 1, true, pool)) {
            score += 30000;
         } else {
            // 3. Precursor score: check if ingredients can be crafted from pool
            int precursorScore = 0;
            for (Ingredient ing : holder.value().getIngredients()) {
               if (ing.isEmpty()) continue;
               int bestIngScore = 0;
               for (Item it : getItemsForIngredient(ing)) {
                  int s = getCraftabilityScore(it, pool, false);
                  if (s > bestIngScore) bestIngScore = s;
                  if (bestIngScore >= 10000) break;
               }
               precursorScore += bestIngScore;
            }
            score += Math.min(20000, precursorScore);
         }
      }

      // 4. Heavily penalize 1:1 conversion or recoloring recipes where player does NOT own input item
      if ((is1to1Conversion(holder) || isRecoloringRecipe(holder)) && !sat) {
         score -= 100000;
      }

      // 5. Cost-based selection: penalize total number of ingredients needed per unit of yield
      int ingredientCount = 0;
      for (Ingredient ing : holder.value().getIngredients()) {
         if (!ing.isEmpty()) ingredientCount++;
      }
      score -= (ingredientCount * 50);

      return score;
   }

   public static RecipeHolder<CraftingRecipe> findBestRecipe(Item item) {
      return findBestRecipe(item, null);
   }

   public static RecipeHolder<CraftingRecipe> findBestRecipe(Item item, Map<Item, Integer> pool) {
      List<RecipeHolder<CraftingRecipe>> candidates = getCandidateRecipes(item, pool);
      return candidates.isEmpty() ? null : candidates.get(0);
   }

   public static RecipeHolder<CraftingRecipe> findDecompressionRecipe(Item item) {
      return findDecompressionRecipe(item, null);
   }

   public static RecipeHolder<CraftingRecipe> findDecompressionRecipe(Item item, Map<Item, Integer> pool) {
      if (MeteorClient.mc.level == null || item == null) return null;

      RecipeHolder<CraftingRecipe> best = null;
      int bestCount = 0;

      for (RecipeHolder<CraftingRecipe> holder : getRecipesFor(item)) {
         int yield = getResultCount(holder);
         if (yield <= 1) continue; // Decompression always yields > 1 item

         Item source = getSingleIngredientItem(holder);
         if (source == null || source == item) continue;

         int haveSource = (pool != null)
            ? pool.getOrDefault(source, 0)
            : countInInventory(source);
         if (haveSource > 0 && haveSource > bestCount) {
            best = holder;
            bestCount = haveSource;
         }
      }

      return best;
   }

   /**
    * Strict decompression detection:
    * Must have EXACTLY 1 non-empty ingredient slot in the recipe, and all items in that slot must match.
    * This prevents slabs (3 planks), stairs (6 planks), etc. from being classified as decompression.
    */
   public static Item getSingleIngredientItem(RecipeHolder<CraftingRecipe> holder) {
      if (holder == null) return null;
      NonNullList<Ingredient> ingredients = holder.value().getIngredients();
      Item singleItem = null;
      int nonEmptyCount = 0;

      for (Ingredient ing : ingredients) {
         if (ing.isEmpty()) continue;
         nonEmptyCount++;
         ItemStack[] items = ing.getItems();
         if (items.length == 0) return null;

         Item it = items[0].getItem();
         if (singleItem == null) {
            singleItem = it;
         } else if (singleItem != it) {
            return null; // Multiple different items
         }
      }

      // Strict check: only 1 input slot is decompression!
      if (nonEmptyCount != 1) {
         return null;
      }

      return singleItem;
   }

   public static int countEquivalentInInventory(Item item) {
      return countEquivalentInInventory(item, 0);
   }

   private static int countEquivalentInInventory(Item item, int depth) {
      int direct = countInInventory(item);
      if (depth >= 2) return direct;

      RecipeHolder<CraftingRecipe> decomp = findDecompressionRecipe(item);
      if (decomp != null) {
         Item source = getSingleIngredientItem(decomp);
         if (source != null && source != item) {
            int sourceCount = countEquivalentInInventory(source, depth + 1);
            int yield = getResultCount(decomp);
            return direct + (sourceCount * yield);
         }
      }
      return direct;
   }

   public static boolean is2x2(RecipeHolder<CraftingRecipe> holder) {
      return holder != null && holder.value().canCraftInDimensions(2, 2);
   }

   public static int getResultCount(RecipeHolder<CraftingRecipe> holder) {
      if (holder == null || MeteorClient.mc.level == null) return 1;
      ItemStack result = holder.value().getResultItem(MeteorClient.mc.level.registryAccess());
      return result.isEmpty() ? 1 : Math.max(1, result.getCount());
   }

   public static boolean canSatisfy(RecipeHolder<CraftingRecipe> holder, int craftCount) {
      return canSatisfy(holder, craftCount, false, null);
   }

   public static boolean canSatisfy(RecipeHolder<CraftingRecipe> holder, int craftCount, boolean allowDecompression) {
      return canSatisfy(holder, craftCount, allowDecompression, null);
   }

   public static boolean canSatisfy(RecipeHolder<CraftingRecipe> holder, int craftCount, boolean allowDecompression, Map<Item, Integer> pool) {
      if (holder == null || craftCount <= 0 || MeteorClient.mc.level == null) return false;

      Map<Item, Integer> workingPool = (pool != null) ? new HashMap<>(pool) : getAvailableInventoryPool(false);

      for (Ingredient ingredient : holder.value().getIngredients()) {
         if (ingredient.isEmpty()) continue;
         int needed = craftCount;

         List<Item> matchingItems = getItemsForIngredient(ingredient);
         for (Map.Entry<Item, Integer> entry : workingPool.entrySet()) {
            if (entry.getValue() > 0 && matchingItems.contains(entry.getKey())) {
               int take = Math.min(needed, entry.getValue());
               entry.setValue(entry.getValue() - take);
               needed -= take;
               if (needed <= 0) break;
            }
         }

         if (needed > 0) {
            if (!allowDecompression) return false;

            int decompSurplus = 0;
            for (Item it : matchingItems) {
               if (pool != null) {
                  RecipeHolder<CraftingRecipe> decomp = findDecompressionRecipe(it, workingPool);
                  if (decomp != null) {
                     Item src = getSingleIngredientItem(decomp);
                     if (src != null) {
                        int srcCount = workingPool.getOrDefault(src, 0);
                        decompSurplus += srcCount * (getResultCount(decomp) - 1);
                     }
                  }
               } else {
                  decompSurplus = Math.max(decompSurplus, countEquivalentInInventory(it) - countInInventory(it));
               }
            }
            if (decompSurplus < needed) return false;
         }
      }
      return true;
   }

   public static boolean canSatisfyRecursive(RecipeHolder<CraftingRecipe> holder, int craftCount, int maxDepth) {
      return canSatisfyRecursive(holder, craftCount, maxDepth, new HashSet<>());
   }

   private static boolean canSatisfyRecursive(RecipeHolder<CraftingRecipe> holder, int craftCount, int maxDepth, Set<Item> visited) {
      if (holder == null || craftCount <= 0 || MeteorClient.mc.level == null) return false;
      if (canSatisfy(holder, craftCount, true)) return true;
      if (maxDepth <= 0) return false;

      ItemStack result = holder.value().getResultItem(MeteorClient.mc.level.registryAccess());
      Item resultItem = result.isEmpty() ? null : result.getItem();
      if (resultItem == null || !visited.add(resultItem)) {
         return false; // Prevent circular dependency
      }

      try {
         Map<Item, Integer> missing = getMissingItems(holder, craftCount, false);
         for (Map.Entry<Item, Integer> entry : missing.entrySet()) {
            Item missingItem = entry.getKey();
            int missingNeeded = entry.getValue();

            int equiv = countEquivalentInInventory(missingItem);
            int have = countInInventory(missingItem);
            if ((equiv - have) >= missingNeeded) {
               continue;
            }

            if (visited.contains(missingItem)) {
               return false;
            }

            RecipeHolder<CraftingRecipe> subRecipe = findBestRecipe(missingItem);
            if (subRecipe == null) return false;

            int subYield = getResultCount(subRecipe);
            int subCrafts = (int) Math.ceil((double) missingNeeded / subYield);
            if (!canSatisfyRecursive(subRecipe, subCrafts, maxDepth - 1, visited)) {
               return false;
            }
         }

         return true;
      } finally {
         if (resultItem != null) {
            visited.remove(resultItem);
         }
      }
   }

   public static Map<Item, Integer> getAvailableInventoryPool(boolean directOnly) {
      Map<Item, Integer> pool = new HashMap<>();
      if (MeteorClient.mc.player == null) return pool;

      // 1. Always include the player's 36 inventory slots (hotbar + main inventory)
      Inventory inv = MeteorClient.mc.player.getInventory();
      for (int i = 0; i < 36 && i < inv.getContainerSize(); i++) {
         ItemStack stack = inv.getItem(i);
         if (!stack.isEmpty()) {
            pool.put(stack.getItem(), pool.getOrDefault(stack.getItem(), 0) + stack.getCount());
         }
      }

      // 2. Include items currently in active crafting grid slots
      AbstractContainerMenu menu = MeteorClient.mc.player.containerMenu;
      if (menu instanceof CraftingMenu craftingMenu) {
         for (int i = 1; i <= 9 && i < craftingMenu.slots.size(); i++) {
            ItemStack stack = craftingMenu.slots.get(i).getItem();
            if (!stack.isEmpty()) {
               pool.put(stack.getItem(), pool.getOrDefault(stack.getItem(), 0) + stack.getCount());
            }
         }
      } else if (menu instanceof InventoryMenu invMenu) {
         for (int i = 1; i <= 4 && i < invMenu.slots.size(); i++) {
            ItemStack stack = invMenu.slots.get(i).getItem();
            if (!stack.isEmpty()) {
               pool.put(stack.getItem(), pool.getOrDefault(stack.getItem(), 0) + stack.getCount());
            }
         }
      } else if (BackpackAdapter.isBackpackMenu(menu)) {
         BackpackAdapter.BackpackCraftInfo info = BackpackAdapter.getBackpackCraftInfo(menu);
         if (info.valid && info.gridStart != -1 && info.gridEnd != -1) {
            for (int i = info.gridStart; i <= info.gridEnd && i < menu.slots.size(); i++) {
               ItemStack stack = menu.slots.get(i).getItem();
               if (!stack.isEmpty()) {
                  pool.put(stack.getItem(), pool.getOrDefault(stack.getItem(), 0) + stack.getCount());
               }
            }
         }
      }

      // 3. Include backpack storage items if not direct-only
      if (!directOnly) {
         for (ItemStack stack : BackpackAdapter.getAllBackpackItems()) {
            if (!stack.isEmpty()) {
               pool.put(stack.getItem(), pool.getOrDefault(stack.getItem(), 0) + stack.getCount());
            }
         }
      }

      return pool;
   }

   public static int getCraftabilityScore(Item item, Map<Item, Integer> pool, boolean directOnly) {
      if (item == null || MeteorClient.mc.level == null) return 0;

      Map<Item, Integer> workingPool = (pool != null) ? pool : getAvailableInventoryPool(directOnly);
      if (workingPool.isEmpty()) return 0;

      // 1. Direct availability in pool
      int inPool = workingPool.getOrDefault(item, 0);
      if (inPool > 0) {
         return 10000 + inPool;
      }

      // 2. Decompression available from pool
      RecipeHolder<CraftingRecipe> decomp = findDecompressionRecipe(item, workingPool);
      if (decomp != null) {
         Item source = getSingleIngredientItem(decomp);
         int sourceCount = (source != null) ? workingPool.getOrDefault(source, 0) : 0;
         if (sourceCount > 0) {
            return 5000 + (sourceCount * getResultCount(decomp));
         }
      }

      // 3. Recipes craftable directly from pool items
      List<RecipeHolder<CraftingRecipe>> recipes = getRecipesFor(item);
      if (recipes.isEmpty()) return 0;

      int bestDirectScore = 0;
      for (RecipeHolder<CraftingRecipe> recipe : recipes) {
         if (is1to1Conversion(recipe)) continue;

         boolean allIngsInPool = true;
         int matsCount = 0;
         for (Ingredient ing : recipe.value().getIngredients()) {
            if (ing.isEmpty()) continue;
            boolean ingFound = false;
            for (Item mItem : getItemsForIngredient(ing)) {
               int count = workingPool.getOrDefault(mItem, 0);
               if (count > 0) {
                  ingFound = true;
                  matsCount += count;
                  break;
               }
            }
            if (!ingFound) {
               allIngsInPool = false;
               break;
            }
         }

         if (allIngsInPool) {
            bestDirectScore = Math.max(bestDirectScore, 3000 + matsCount);
         }
      }

      if (bestDirectScore > 0) {
         return bestDirectScore;
      }

      // 4. Bounded single-level precursor check (e.g. Cherry Log in pool for Cherry Planks for Cherry Slab)
      // STRICT REQUIREMENT: EVERY NON-EMPTY INGREDIENT IN THE RECIPE MUST BE SATISFIED!
      int recipesChecked = 0;
      for (RecipeHolder<CraftingRecipe> recipe : recipes) {
         if (is1to1Conversion(recipe) || isRecoloringRecipe(recipe)) continue;
         recipesChecked++;
         if (recipesChecked > 3) break;

         boolean allIngredientsSatisfied = true;
         int matsCount = 0;

         for (Ingredient ing : recipe.value().getIngredients()) {
            if (ing.isEmpty()) continue;
            boolean ingSatisfied = false;

            // Check A: Directly in working pool
            for (Item mItem : getItemsForIngredient(ing)) {
               int count = workingPool.getOrDefault(mItem, 0);
               if (count > 0) {
                  ingSatisfied = true;
                  matsCount += count;
                  break;
               }
            }

            // Check B: Decompression from owned block in working pool
            if (!ingSatisfied) {
               for (Item mItem : getItemsForIngredient(ing)) {
                  RecipeHolder<CraftingRecipe> subDecomp = findDecompressionRecipe(mItem, workingPool);
                  if (subDecomp != null) {
                     Item dSrc = getSingleIngredientItem(subDecomp);
                     int dCount = (dSrc != null) ? workingPool.getOrDefault(dSrc, 0) : 0;
                     if (dCount > 0) {
                        ingSatisfied = true;
                        matsCount += dCount;
                        break;
                     }
                  }
               }
            }

            // Check C: Direct craftable via single-level precursor
            if (!ingSatisfied) {
               for (Item mItem : getItemsForIngredient(ing)) {
                  for (RecipeHolder<CraftingRecipe> subRec : getRecipesFor(mItem)) {
                     if (is1to1Conversion(subRec) || isRecoloringRecipe(subRec)) continue;
                     if (canSatisfy(subRec, 1, false, workingPool)) {
                        ingSatisfied = true;
                        matsCount += 1;
                        break;
                     }
                  }
                  if (ingSatisfied) break;
               }
            }

            if (!ingSatisfied) {
               allIngredientsSatisfied = false;
               break;
            }
         }

         if (allIngredientsSatisfied && matsCount > 0) {
            return 1000 + matsCount;
         }
      }

      return 0;
   }

   public static List<Item> getCandidateItemsForIngredient(Ingredient ingredient, boolean directOnly, Map<Item, Integer> pool) {
      if (ingredient == null || ingredient.isEmpty()) return Collections.emptyList();
      List<Item> matching = getItemsForIngredient(ingredient);
      if (matching.isEmpty()) return Collections.emptyList();
      if (matching.size() == 1) return matching;

      List<Item> candidates = new ArrayList<>(matching);

      // Precompute scores ONCE per candidate
      Map<Item, Integer> scoreMap = new HashMap<>(candidates.size());
      for (Item it : candidates) {
         scoreMap.put(it, getCraftabilityScore(it, pool, directOnly));
      }

      candidates.sort((a, b) -> {
         int scoreA = scoreMap.getOrDefault(a, 0);
         int scoreB = scoreMap.getOrDefault(b, 0);
         if (scoreA != scoreB) {
            return Integer.compare(scoreB, scoreA); // descending
         }
         // Tie-breaker 1: Canonical baseline items preferred over colored/mod variants
         boolean canonA = CANONICAL_DEFAULTS.contains(a);
         boolean canonB = CANONICAL_DEFAULTS.contains(b);
         if (canonA != canonB) return canonA ? -1 : 1;

         // Tie-breaker 2: Prefer vanilla "minecraft" items over mod items
         ResourceLocation idA = BuiltInRegistries.ITEM.getKey(a);
         ResourceLocation idB = BuiltInRegistries.ITEM.getKey(b);
         boolean mcA = idA.getNamespace().equals("minecraft");
         boolean mcB = idB.getNamespace().equals("minecraft");
         if (mcA != mcB) return mcA ? -1 : 1;

         // Tie-breaker 3: Shorter path
         if (idA.getPath().length() != idB.getPath().length()) {
            return Integer.compare(idA.getPath().length(), idB.getPath().length());
         }
         return idA.getPath().compareTo(idB.getPath());
      });

      return candidates;
   }

   public static Item getRepresentativeItem(Ingredient ingredient, boolean directOnly, Map<Item, Integer> pool) {
      List<Item> candidates = getCandidateItemsForIngredient(ingredient, directOnly, pool);
      return candidates.isEmpty() ? null : candidates.get(0);
   }

   public static Item getRepresentativeItem(Ingredient ingredient, boolean directOnly) {
      return getRepresentativeItem(ingredient, directOnly, getAvailableInventoryPool(directOnly));
   }

   public static Map<Item, Integer> getRequiredItems(RecipeHolder<CraftingRecipe> holder, int craftCount) {
      Map<Item, Integer> map = new HashMap<>();
      if (holder == null || craftCount <= 0) return map;

      Map<Item, Integer> pool = getAvailableInventoryPool(false);

      for (Ingredient ingredient : holder.value().getIngredients()) {
         if (ingredient.isEmpty()) continue;
         int needed = craftCount;

         for (Map.Entry<Item, Integer> entry : pool.entrySet()) {
            if (entry.getValue() > 0 && ingredient.test(entry.getKey().getDefaultInstance())) {
               int take = Math.min(needed, entry.getValue());
               entry.setValue(entry.getValue() - take);
               map.put(entry.getKey(), map.getOrDefault(entry.getKey(), 0) + take);
               needed -= take;
               if (needed <= 0) break;
            }
         }

         if (needed > 0) {
            Item rep = getRepresentativeItem(ingredient, false, pool);
            if (rep != null) {
               map.put(rep, map.getOrDefault(rep, 0) + needed);
            }
         }
      }

      return map;
   }

   public static Map<Item, Integer> getMissingItems(RecipeHolder<CraftingRecipe> holder, int craftCount) {
      return getMissingItems(holder, craftCount, false);
   }

   public static Map<Item, Integer> getMissingFromDirectInventory(RecipeHolder<CraftingRecipe> holder, int craftCount) {
      return getMissingItems(holder, craftCount, true);
   }

   public static Map<Item, Integer> getMissingItems(RecipeHolder<CraftingRecipe> holder, int craftCount, boolean directOnly) {
      Map<Item, Integer> missing = new HashMap<>();
      if (holder == null || craftCount <= 0) return missing;

      Map<Item, Integer> pool = getAvailableInventoryPool(directOnly);

      for (Ingredient ingredient : holder.value().getIngredients()) {
         if (ingredient.isEmpty()) continue;
         int needed = craftCount;

         for (Map.Entry<Item, Integer> entry : pool.entrySet()) {
            if (entry.getValue() > 0 && ingredient.test(entry.getKey().getDefaultInstance())) {
               int take = Math.min(needed, entry.getValue());
               entry.setValue(entry.getValue() - take);
               needed -= take;
               if (needed <= 0) break;
            }
         }

         if (needed > 0) {
            Item rep = getRepresentativeItem(ingredient, directOnly, pool);
            if (rep != null) {
               missing.put(rep, missing.getOrDefault(rep, 0) + needed);
            }
         }
      }

      return missing;
   }

   public static int countInDirectInventory(Item item) {
      if (MeteorClient.mc.player == null || item == null) return 0;
      int count = 0;

      // 1. Always count from player's 36 inventory slots (hotbar + main inventory)
      Inventory inv = MeteorClient.mc.player.getInventory();
      for (int i = 0; i < 36 && i < inv.getContainerSize(); i++) {
         ItemStack stack = inv.getItem(i);
         if (stack.is(item)) {
            count += stack.getCount();
         }
      }

      // 2. Count from active crafting grid slots
      AbstractContainerMenu menu = MeteorClient.mc.player.containerMenu;
      if (menu instanceof CraftingMenu craftingMenu) {
         for (int i = 1; i <= 9 && i < craftingMenu.slots.size(); i++) {
            ItemStack stack = craftingMenu.slots.get(i).getItem();
            if (stack.is(item)) {
               count += stack.getCount();
            }
         }
      } else if (menu instanceof InventoryMenu invMenu) {
         for (int i = 1; i <= 4 && i < invMenu.slots.size(); i++) {
            ItemStack stack = invMenu.slots.get(i).getItem();
            if (stack.is(item)) {
               count += stack.getCount();
            }
         }
      } else if (BackpackAdapter.isBackpackMenu(menu)) {
         BackpackAdapter.BackpackCraftInfo info = BackpackAdapter.getBackpackCraftInfo(menu);
         if (info.valid && info.gridStart != -1 && info.gridEnd != -1) {
            for (int i = info.gridStart; i <= info.gridEnd && i < menu.slots.size(); i++) {
               ItemStack stack = menu.slots.get(i).getItem();
               if (stack.is(item)) {
                  count += stack.getCount();
               }
            }
         }
      }

      return count;
   }

   public static int countIngredientInDirectInventory(Ingredient ingredient) {
      if (MeteorClient.mc.player == null || ingredient == null || ingredient.isEmpty()) return 0;
      int count = 0;

      // 1. Always count from player's 36 inventory slots (hotbar + main inventory)
      Inventory inv = MeteorClient.mc.player.getInventory();
      for (int i = 0; i < 36 && i < inv.getContainerSize(); i++) {
         ItemStack stack = inv.getItem(i);
         if (ingredient.test(stack)) {
            count += stack.getCount();
         }
      }

      // 2. Count from active crafting grid slots
      AbstractContainerMenu menu = MeteorClient.mc.player.containerMenu;
      if (menu instanceof CraftingMenu craftingMenu) {
         for (int i = 1; i <= 9 && i < craftingMenu.slots.size(); i++) {
            ItemStack stack = craftingMenu.slots.get(i).getItem();
            if (ingredient.test(stack)) {
               count += stack.getCount();
            }
         }
      } else if (menu instanceof InventoryMenu invMenu) {
         for (int i = 1; i <= 4 && i < invMenu.slots.size(); i++) {
            ItemStack stack = invMenu.slots.get(i).getItem();
            if (ingredient.test(stack)) {
               count += stack.getCount();
            }
         }
      } else if (BackpackAdapter.isBackpackMenu(menu)) {
         BackpackAdapter.BackpackCraftInfo info = BackpackAdapter.getBackpackCraftInfo(menu);
         if (info.valid && info.gridStart != -1 && info.gridEnd != -1) {
            for (int i = info.gridStart; i <= info.gridEnd && i < menu.slots.size(); i++) {
               ItemStack stack = menu.slots.get(i).getItem();
               if (ingredient.test(stack)) {
                  count += stack.getCount();
               }
            }
         }
      }

      return count;
   }

   public static int countInInventory(Item item) {
      return countInDirectInventory(item) + BackpackAdapter.countInAllBackpacks(item);
   }

   public static int countIngredientInInventory(Ingredient ingredient) {
      return countIngredientInDirectInventory(ingredient) + BackpackAdapter.countIngredientInAllBackpacks(ingredient);
   }

   public static boolean hasAllInDirectInventory(RecipeHolder<CraftingRecipe> holder, int craftCount) {
      if (holder == null || craftCount <= 0 || MeteorClient.mc.player == null) return false;

      Map<Item, Integer> pool = getAvailableInventoryPool(true);

      for (Ingredient ingredient : holder.value().getIngredients()) {
         if (ingredient.isEmpty()) continue;
         int needed = craftCount;

         for (Map.Entry<Item, Integer> entry : pool.entrySet()) {
            if (entry.getValue() > 0 && ingredient.test(entry.getKey().getDefaultInstance())) {
               int take = Math.min(needed, entry.getValue());
               entry.setValue(entry.getValue() - take);
               needed -= take;
               if (needed <= 0) break;
            }
         }

         if (needed > 0) {
            return false;
         }
      }

      return true;
   }

   public static boolean canSatisfyDirect(RecipeHolder<CraftingRecipe> holder, int craftCount) {
      return hasAllInDirectInventory(holder, craftCount);
   }

   public static boolean canCraftBatch(RecipeHolder<CraftingRecipe> holder, int count, Map<Item, Integer> pool) {
      if (holder == null || count <= 0 || pool == null || pool.isEmpty()) return false;
      Ingredient[] grid = getGridIngredients(holder, !is2x2(holder));
      Map<Item, Integer> tempPool = new HashMap<>(pool);

      for (Ingredient ing : grid) {
         if (ing == null || ing.isEmpty()) continue;
         // In Minecraft crafting tables, a single grid slot can only hold items of ONE item type!
         Item chosen = null;
         for (Map.Entry<Item, Integer> entry : tempPool.entrySet()) {
            if (entry.getValue() >= count && ing.test(entry.getKey().getDefaultInstance())) {
               chosen = entry.getKey();
               break;
            }
         }
         if (chosen == null) return false;
         tempPool.put(chosen, tempPool.get(chosen) - count);
      }
      return true;
   }

   public static int calculateMaxCraftsFromPool(RecipeHolder<CraftingRecipe> holder, Map<Item, Integer> pool) {
      if (holder == null || pool == null || pool.isEmpty()) return 0;
      Ingredient[] grid = getGridIngredients(holder, !is2x2(holder));
      int maxStack = 64;
      for (Ingredient ing : grid) {
         if (ing != null && !ing.isEmpty()) {
            ItemStack[] items = ing.getItems();
            if (items != null && items.length > 0) {
               maxStack = Math.min(maxStack, items[0].getMaxStackSize());
            }
         }
      }
      if (MeteorClient.mc.level != null) {
         ItemStack result = holder.value().getResultItem(MeteorClient.mc.level.registryAccess());
         if (!result.isEmpty()) {
            maxStack = Math.min(maxStack, result.getMaxStackSize());
         }
      }

      int low = 1, high = maxStack, best = 0;
      while (low <= high) {
         int mid = (low + high) >>> 1;
         if (canCraftBatch(holder, mid, pool)) {
            best = mid;
            low = mid + 1;
         } else {
            high = mid - 1;
         }
      }
      return best;
   }

   public static int calculateMaxCrafts(RecipeHolder<CraftingRecipe> holder, boolean directOnly) {
      if (holder == null || MeteorClient.mc.player == null) return 0;
      Map<Item, Integer> pool = getAvailableInventoryPool(directOnly);
      return calculateMaxCraftsFromPool(holder, pool);
   }

   /**
    * Calculates the maximum number of crafts of the given recipe that can be performed
    * using only the items currently available in the player's direct inventory.
    * Uses a monotonic binary search over [0, 36 * 64].
    */
   public static int getDirectCraftsPossible(RecipeHolder<CraftingRecipe> holder) {
      if (holder == null || MeteorClient.mc.player == null) return 0;
      if (!hasAllInDirectInventory(holder, 1)) return 0;

      int low = 1;
      int high = 36 * 64;
      int best = 1;

      while (low <= high) {
         int mid = (low + high) >>> 1;
         if (hasAllInDirectInventory(holder, mid)) {
            best = mid;
            low = mid + 1;
         } else {
            high = mid - 1;
         }
      }
      return best;
   }

   /**
    * Returns the number of empty slots in the player's direct inventory (hotbar + main storage, slots 0..35).
    */
   public static int getEmptyInventorySlots() {
      if (MeteorClient.mc.player == null) return 0;
      AbstractContainerMenu menu = MeteorClient.mc.player.containerMenu;
      if (menu != null && !(menu instanceof InventoryMenu)) {
         int empty = 0;
         for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory && slot.getContainerSlot() < 36 && slot.getItem().isEmpty()) {
               empty++;
            }
         }
         return empty;
      }
      Inventory inv = MeteorClient.mc.player.getInventory();
      int empty = 0;
      for (int i = 0; i < 36 && i < inv.getContainerSize(); i++) {
         if (inv.getItem(i).isEmpty()) {
            empty++;
         }
      }
      return empty;
   }

   /**
    * Returns how many more units of the given item can fit into the player's direct inventory.
    */
   public static int getFreeSpaceFor(Item item) {
      if (MeteorClient.mc.player == null || item == null) return 0;
      int maxStack = item.getDefaultInstance().getMaxStackSize();
      int freeSpace = 0;
      AbstractContainerMenu menu = MeteorClient.mc.player.containerMenu;
      if (menu != null && !(menu instanceof InventoryMenu)) {
         for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory && slot.getContainerSlot() < 36) {
               ItemStack stack = slot.getItem();
               if (stack.isEmpty()) {
                  freeSpace += maxStack;
               } else if (stack.is(item)) {
                  freeSpace += Math.max(0, maxStack - stack.getCount());
               }
            }
         }
         return freeSpace;
      }

      Inventory inv = MeteorClient.mc.player.getInventory();
      for (int i = 0; i < 36 && i < inv.getContainerSize(); i++) {
         ItemStack stack = inv.getItem(i);
         if (stack.isEmpty()) {
            freeSpace += maxStack;
         } else if (stack.is(item)) {
            freeSpace += Math.max(0, maxStack - stack.getCount());
         }
      }
      return freeSpace;
   }

   /**
    * Returns the array of Ingredients for grid positions:
    * For 3x3: indices 0..8 (row 0: 0,1,2; row 1: 3,4,5; row 2: 6,7,8)
    * For 2x2: indices 0..3 (row 0: 0,1; row 1: 2,3)
    */
   public static Ingredient[] getGridIngredients(RecipeHolder<CraftingRecipe> holder, boolean table3x3) {
      CraftingRecipe recipe = holder.value();
      if (table3x3) {
         Ingredient[] grid = new Ingredient[9];
         for (int i = 0; i < 9; i++) grid[i] = Ingredient.EMPTY;

         if (recipe instanceof ShapedRecipe shaped) {
            int w = shaped.pattern.width();
            int h = shaped.pattern.height();
            NonNullList<Ingredient> ingredients = shaped.pattern.ingredients();
            for (int r = 0; r < h; r++) {
               for (int c = 0; c < w; c++) {
                  int srcIdx = r * w + c;
                  if (srcIdx < ingredients.size()) {
                     grid[r * 3 + c] = ingredients.get(srcIdx);
                  }
               }
            }
         } else if (recipe instanceof ShapelessRecipe shapeless) {
            NonNullList<Ingredient> ingredients = shapeless.getIngredients();
            for (int i = 0; i < Math.min(9, ingredients.size()); i++) {
               grid[i] = ingredients.get(i);
            }
         } else {
            NonNullList<Ingredient> ingredients = recipe.getIngredients();
            for (int i = 0; i < Math.min(9, ingredients.size()); i++) {
               grid[i] = ingredients.get(i);
            }
         }
         return grid;
      } else {
         Ingredient[] grid = new Ingredient[4];
         for (int i = 0; i < 4; i++) grid[i] = Ingredient.EMPTY;

         if (recipe instanceof ShapedRecipe shaped) {
            int w = shaped.pattern.width();
            int h = shaped.pattern.height();
            NonNullList<Ingredient> ingredients = shaped.pattern.ingredients();
            for (int r = 0; r < Math.min(2, h); r++) {
               for (int c = 0; c < Math.min(2, w); c++) {
                  int srcIdx = r * w + c;
                  if (srcIdx < ingredients.size()) {
                     grid[r * 2 + c] = ingredients.get(srcIdx);
                  }
               }
            }
         } else {
            NonNullList<Ingredient> ingredients = recipe.getIngredients();
            for (int i = 0; i < Math.min(4, ingredients.size()); i++) {
               grid[i] = ingredients.get(i);
            }
         }
         return grid;
      }
   }
}
