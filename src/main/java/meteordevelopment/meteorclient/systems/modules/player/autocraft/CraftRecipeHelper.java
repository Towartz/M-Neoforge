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
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

public class CraftRecipeHelper {
   private static RecipeManager lastRecipeManager = null;
   private static final Map<Item, List<RecipeHolder<CraftingRecipe>>> RECIPES_BY_RESULT = new HashMap<>();
   private static final Set<Item> ALL_CRAFTABLE_ITEMS = new LinkedHashSet<>();
   private static boolean listenerRegistered = false;

   private CraftRecipeHelper() {
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
      if (manager != lastRecipeManager) {
         rebuildCache(manager);
      }
      return true;
   }

   private static synchronized void rebuildCache(RecipeManager manager) {
      RECIPES_BY_RESULT.clear();
      ALL_CRAFTABLE_ITEMS.clear();
      lastRecipeManager = manager;

      if (manager == null || MeteorClient.mc.level == null) return;

      var registryAccess = MeteorClient.mc.level.registryAccess();
      for (RecipeHolder<CraftingRecipe> holder : manager.getAllRecipesFor(RecipeType.CRAFTING)) {
         ItemStack result = holder.value().getResultItem(registryAccess);
         if (!result.isEmpty()) {
            Item item = result.getItem();
            RECIPES_BY_RESULT.computeIfAbsent(item, k -> new ArrayList<>()).add(holder);
            ALL_CRAFTABLE_ITEMS.add(item);
         }
      }
   }

   @EventHandler
   private static void onGameLeft(GameLeftEvent event) {
      clearCache();
   }

   public static synchronized void clearCache() {
      RECIPES_BY_RESULT.clear();
      ALL_CRAFTABLE_ITEMS.clear();
      lastRecipeManager = null;
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
      if (raw.size() == 1) return raw;

      List<RecipeHolder<CraftingRecipe>> candidates = new ArrayList<>(raw);
      candidates.sort((a, b) -> {
         // 1. Direct satisfaction in pool/inventory (highest priority)
         boolean satA = (pool != null) ? canSatisfy(a, 1, false, pool) : canSatisfy(a, 1, false);
         boolean satB = (pool != null) ? canSatisfy(b, 1, false, pool) : canSatisfy(b, 1, false);
         if (satA != satB) return satA ? -1 : 1;

         // 2. Decompression with owned storage block
         Item srcA = getSingleIngredientItem(a);
         Item srcB = getSingleIngredientItem(b);
         int countA = (srcA != null) ? ((pool != null) ? pool.getOrDefault(srcA, 0) : countInInventory(srcA)) : 0;
         int countB = (srcB != null) ? ((pool != null) ? pool.getOrDefault(srcB, 0) : countInInventory(srcB)) : 0;
         boolean ownsDecompA = isDecompressionRecipe(a) && countA > 0;
         boolean ownsDecompB = isDecompressionRecipe(b) && countB > 0;
         if (ownsDecompA != ownsDecompB) return ownsDecompA ? -1 : 1;

         // 3. Decompression satisfaction
         boolean decompSatA = (pool != null) ? canSatisfy(a, 1, true, pool) : canSatisfy(a, 1, true);
         boolean decompSatB = (pool != null) ? canSatisfy(b, 1, true, pool) : canSatisfy(b, 1, true);
         if (decompSatA != decompSatB) return decompSatA ? -1 : 1;

         // 4. Heavily penalize 1:1 conversion recipes where player does NOT own the input item
         boolean unownedConvA = is1to1Conversion(a) && !satA;
         boolean unownedConvB = is1to1Conversion(b) && !satB;
         if (unownedConvA != unownedConvB) {
            return unownedConvA ? 1 : -1; // unowned conversion pushed to the back
         }

         // 5. Prefer higher yield
         int yieldA = getResultCount(a);
         int yieldB = getResultCount(b);
         if (yieldA != yieldB) return Integer.compare(yieldB, yieldA);

         return 0;
      });

      return candidates;
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

         for (Map.Entry<Item, Integer> entry : workingPool.entrySet()) {
            if (entry.getValue() > 0 && ingredient.test(entry.getKey().getDefaultInstance())) {
               int take = Math.min(needed, entry.getValue());
               entry.setValue(entry.getValue() - take);
               needed -= take;
               if (needed <= 0) break;
            }
         }

         if (needed > 0) {
            if (!allowDecompression) return false;

            int decompSurplus = 0;
            ItemStack[] matching = ingredient.getItems();
            if (matching != null) {
               for (ItemStack stack : matching) {
                  Item it = stack.getItem();
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

      Inventory inv = MeteorClient.mc.player.getInventory();
      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack stack = inv.getItem(i);
         if (!stack.isEmpty()) {
            pool.put(stack.getItem(), pool.getOrDefault(stack.getItem(), 0) + stack.getCount());
         }
      }

      // Include open crafting table grid slots
      if (MeteorClient.mc.player.containerMenu instanceof CraftingMenu craftingMenu) {
         for (int i = 1; i <= 9 && i < craftingMenu.slots.size(); i++) {
            ItemStack stack = craftingMenu.slots.get(i).getItem();
            if (!stack.isEmpty()) {
               pool.put(stack.getItem(), pool.getOrDefault(stack.getItem(), 0) + stack.getCount());
            }
         }
      } else if (MeteorClient.mc.player.containerMenu instanceof InventoryMenu invMenu) {
         for (int i = 1; i <= 4 && i < invMenu.slots.size(); i++) {
            ItemStack stack = invMenu.slots.get(i).getItem();
            if (!stack.isEmpty()) {
               pool.put(stack.getItem(), pool.getOrDefault(stack.getItem(), 0) + stack.getCount());
            }
         }
      }

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
      if (item == null) return 0;

      // 1. Direct availability in pool
      int inPool = (pool != null)
         ? pool.getOrDefault(item, 0)
         : (directOnly ? countInDirectInventory(item) : countInInventory(item));
      if (inPool > 0) {
         return 10000 + inPool;
      }

      // 2. Decompression available from pool
      RecipeHolder<CraftingRecipe> decomp = findDecompressionRecipe(item, pool);
      if (decomp != null) {
         Item source = getSingleIngredientItem(decomp);
         int sourceCount = (source != null)
            ? ((pool != null) ? pool.getOrDefault(source, 0) : (directOnly ? countInDirectInventory(source) : countInInventory(source)))
            : 0;
         if (sourceCount > 0) {
            return 5000 + (sourceCount * getResultCount(decomp));
         }
      }

      // 3. Recipes craftable directly from pool items
      List<RecipeHolder<CraftingRecipe>> recipes = getRecipesFor(item);
      int bestDirectScore = 0;
      for (RecipeHolder<CraftingRecipe> recipe : recipes) {
         if (is1to1Conversion(recipe)) continue;

         boolean allIngsInPool = true;
         int matsCount = 0;
         for (Ingredient ing : recipe.value().getIngredients()) {
            if (ing.isEmpty()) continue;
            boolean ingFound = false;
            ItemStack[] matching = ing.getItems();
            if (matching != null) {
               for (ItemStack st : matching) {
                  Item mItem = st.getItem();
                  int count = (pool != null)
                     ? pool.getOrDefault(mItem, 0)
                     : (directOnly ? countInDirectInventory(mItem) : countInInventory(mItem));
                  if (count > 0) {
                     ingFound = true;
                     matsCount += count;
                     break;
                  }
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

      // 4. Fast single-level precursor check (e.g. Cherry Log in pool for Cherry Planks for Cherry Slab)
      for (RecipeHolder<CraftingRecipe> recipe : recipes) {
         if (is1to1Conversion(recipe)) continue;
         for (Ingredient ing : recipe.value().getIngredients()) {
            if (ing.isEmpty()) continue;
            ItemStack[] matching = ing.getItems();
            if (matching != null) {
               for (ItemStack st : matching) {
                  Item mItem = st.getItem();
                  for (RecipeHolder<CraftingRecipe> subRec : getRecipesFor(mItem)) {
                     if (is1to1Conversion(subRec)) continue;
                     for (Ingredient subIng : subRec.value().getIngredients()) {
                        if (subIng.isEmpty()) continue;
                        ItemStack[] subMatching = subIng.getItems();
                        if (subMatching != null) {
                           for (ItemStack subSt : subMatching) {
                              int subCount = (pool != null)
                                 ? pool.getOrDefault(subSt.getItem(), 0)
                                 : (directOnly ? countInDirectInventory(subSt.getItem()) : countInInventory(subSt.getItem()));
                              if (subCount > 0) {
                                 return 1000 + subCount; // Early exit on first precursor match!
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return 0;
   }

   public static List<Item> getCandidateItemsForIngredient(Ingredient ingredient, boolean directOnly, Map<Item, Integer> pool) {
      if (ingredient == null || ingredient.isEmpty()) return Collections.emptyList();
      ItemStack[] matching = ingredient.getItems();
      if (matching == null || matching.length == 0) return Collections.emptyList();

      List<Item> candidates = new ArrayList<>();
      for (ItemStack st : matching) {
         if (st != null && !st.isEmpty()) {
            Item it = st.getItem();
            if (!candidates.contains(it)) {
               candidates.add(it);
            }
         }
      }

      if (candidates.size() <= 1) return candidates;

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
         return 0;
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
      Inventory inv = MeteorClient.mc.player.getInventory();
      int count = 0;
      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack stack = inv.getItem(i);
         if (stack.is(item)) {
            count += stack.getCount();
         }
      }
      if (MeteorClient.mc.player.containerMenu instanceof CraftingMenu craftingMenu) {
         for (int i = 1; i <= 9 && i < craftingMenu.slots.size(); i++) {
            ItemStack stack = craftingMenu.slots.get(i).getItem();
            if (stack.is(item)) {
               count += stack.getCount();
            }
         }
      } else if (MeteorClient.mc.player.containerMenu instanceof InventoryMenu invMenu) {
         for (int i = 1; i <= 4 && i < invMenu.slots.size(); i++) {
            ItemStack stack = invMenu.slots.get(i).getItem();
            if (stack.is(item)) {
               count += stack.getCount();
            }
         }
      }
      return count;
   }

   public static int countIngredientInDirectInventory(Ingredient ingredient) {
      if (MeteorClient.mc.player == null || ingredient == null || ingredient.isEmpty()) return 0;
      Inventory inv = MeteorClient.mc.player.getInventory();
      int count = 0;
      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack stack = inv.getItem(i);
         if (ingredient.test(stack)) {
            count += stack.getCount();
         }
      }
      if (MeteorClient.mc.player.containerMenu instanceof CraftingMenu craftingMenu) {
         for (int i = 1; i <= 9 && i < craftingMenu.slots.size(); i++) {
            ItemStack stack = craftingMenu.slots.get(i).getItem();
            if (ingredient.test(stack)) {
               count += stack.getCount();
            }
         }
      } else if (MeteorClient.mc.player.containerMenu instanceof InventoryMenu invMenu) {
         for (int i = 1; i <= 4 && i < invMenu.slots.size(); i++) {
            ItemStack stack = invMenu.slots.get(i).getItem();
            if (ingredient.test(stack)) {
               count += stack.getCount();
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
      Inventory inv = MeteorClient.mc.player.getInventory();
      int empty = 0;
      for (int i = 0; i < 36; i++) {
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
      Inventory inv = MeteorClient.mc.player.getInventory();
      int maxStack = item.getDefaultInstance().getMaxStackSize();
      int freeSpace = 0;
      for (int i = 0; i < 36; i++) {
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
