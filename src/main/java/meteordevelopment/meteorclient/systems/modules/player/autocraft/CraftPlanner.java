package meteordevelopment.meteorclient.systems.modules.player.autocraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

public class CraftPlanner {
   public static class CraftStep {
      public final Item resultItem;
      public final RecipeHolder<CraftingRecipe> recipe;
      public final int craftCount;
      public final int yieldProduced;
      public final Map<Item, Integer> ingredientsConsumed;

      public CraftStep(Item resultItem, RecipeHolder<CraftingRecipe> recipe, int craftCount, int yieldProduced, Map<Item, Integer> ingredientsConsumed) {
         this.resultItem = resultItem;
         this.recipe = recipe;
         this.craftCount = craftCount;
         this.yieldProduced = yieldProduced;
         this.ingredientsConsumed = ingredientsConsumed;
      }
   }

   public static class CraftPlan {
      public final Item targetItem;
      public final int targetCount;
      public final List<CraftStep> steps;
      public final Map<Item, Integer> rawMaterialsNeeded;
      public final Map<Item, Integer> missingRawMaterials;
      public final boolean isSatisfied;

      public CraftPlan(Item targetItem, int targetCount, List<CraftStep> steps, Map<Item, Integer> rawMaterialsNeeded, Map<Item, Integer> missingRawMaterials, boolean isSatisfied) {
         this.targetItem = targetItem;
         this.targetCount = targetCount;
         this.steps = steps;
         this.rawMaterialsNeeded = rawMaterialsNeeded;
         this.missingRawMaterials = missingRawMaterials;
         this.isSatisfied = isSatisfied;
      }

      public boolean requires3x3Table() {
         for (CraftStep step : steps) {
            if (!CraftRecipeHelper.is2x2(step.recipe)) {
               return true;
            }
         }
         return false;
      }
   }

   public static CraftPlan createPlan(Item targetItem, int targetCount) {
      if (targetItem == null || targetCount <= 0 || MeteorClient.mc.level == null) {
         return new CraftPlan(targetItem, targetCount, Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), false);
      }

      Map<Item, Integer> virtualInv = CraftRecipeHelper.getAvailableInventoryPool(false);
      Map<Item, Integer> startingInv = new HashMap<>(virtualInv);
      List<CraftStep> steps = new ArrayList<>();
      Map<Item, Integer> missingRaw = new LinkedHashMap<>();
      Set<Item> activePath = new HashSet<>();

      boolean satisfied = planItem(targetItem, targetCount, virtualInv, steps, missingRaw, activePath, 0);

      // Determine which raw materials were actually consumed from starting inventory
      Map<Item, Integer> rawMaterialsNeeded = new LinkedHashMap<>();
      for (Map.Entry<Item, Integer> entry : startingInv.entrySet()) {
         int start = entry.getValue();
         int remaining = virtualInv.getOrDefault(entry.getKey(), 0);
         if (start > remaining) {
            rawMaterialsNeeded.put(entry.getKey(), start - remaining);
         }
      }

      // Merge sequential steps of the same recipe if applicable
      List<CraftStep> mergedSteps = optimizeSteps(steps);

      return new CraftPlan(targetItem, targetCount, mergedSteps, rawMaterialsNeeded, missingRaw, satisfied && missingRaw.isEmpty());
   }

   private static boolean planItem(Item item, int neededCount, Map<Item, Integer> virtualInv, List<CraftStep> steps,
                                   Map<Item, Integer> missingRaw, Set<Item> activePath, int depth) {
      if (neededCount <= 0) return true;

      // 1. Take as much as possible from virtual inventory / surplus
      int have = virtualInv.getOrDefault(item, 0);
      int taken = Math.min(have, neededCount);
      if (taken > 0) {
         virtualInv.put(item, have - taken);
         neededCount -= taken;
      }
      if (neededCount <= 0) return true;

      // 2. Prevent recursion cycles and deep recursion
      if (depth > 6 || !activePath.add(item)) {
         missingRaw.put(item, missingRaw.getOrDefault(item, 0) + neededCount);
         return false;
      }

      try {
         // 3. Find the best recipe
         RecipeHolder<CraftingRecipe> recipe = null;

         // Check decompression first (e.g. Iron Block -> 9 Iron Ingots)
         RecipeHolder<CraftingRecipe> decomp = CraftRecipeHelper.findDecompressionRecipe(item, virtualInv);
         if (decomp != null) {
            Item source = CraftRecipeHelper.getSingleIngredientItem(decomp);
            if (source != null && virtualInv.getOrDefault(source, 0) > 0) {
               recipe = decomp;
            }
         }

         if (recipe == null) {
            recipe = CraftRecipeHelper.findBestRecipe(item);
         }

         if (recipe == null) {
            // No craft recipe available: item is a missing base raw material
            missingRaw.put(item, missingRaw.getOrDefault(item, 0) + neededCount);
            return false;
         }

         int yield = CraftRecipeHelper.getResultCount(recipe);
         int craftsNeeded = (int) Math.ceil((double) neededCount / yield);
         int totalProduced = craftsNeeded * yield;

         boolean allIngredientsSatisfied = true;
         Map<Item, Integer> stepConsumed = new LinkedHashMap<>();

         // 4. Resolve each ingredient required by the recipe
         for (Ingredient ingredient : recipe.value().getIngredients()) {
            if (ingredient.isEmpty()) continue;
            int needed = craftsNeeded;

            // Check if virtual inventory has an item matching this ingredient
            for (Map.Entry<Item, Integer> entry : virtualInv.entrySet()) {
               if (entry.getValue() > 0 && ingredient.test(entry.getKey().getDefaultInstance())) {
                  int take = Math.min(needed, entry.getValue());
                  entry.setValue(entry.getValue() - take);
                  stepConsumed.put(entry.getKey(), stepConsumed.getOrDefault(entry.getKey(), 0) + take);
                  needed -= take;
                  if (needed <= 0) break;
               }
            }

            if (needed > 0) {
               // Deficit: recursively plan and produce the needed matching ingredient in batch
               Item rep = CraftRecipeHelper.getRepresentativeItem(ingredient, false);
               if (rep == null || rep == Items.AIR) {
                  ItemStack[] matching = ingredient.getItems();
                  Item missingItem = (matching != null && matching.length > 0) ? matching[0].getItem() : item;
                  missingRaw.put(missingItem, missingRaw.getOrDefault(missingItem, 0) + needed);
                  allIngredientsSatisfied = false;
                  continue;
               }

               boolean subSuccess = planItem(rep, needed, virtualInv, steps, missingRaw, activePath, depth + 1);
               if (!subSuccess) {
                  allIngredientsSatisfied = false;
               }

               // Consume the produced item from virtual inventory
               int repHave = virtualInv.getOrDefault(rep, 0);
               int repTake = Math.min(needed, repHave);
               if (repTake > 0) {
                  virtualInv.put(rep, repHave - repTake);
                  stepConsumed.put(rep, stepConsumed.getOrDefault(rep, 0) + repTake);
               }
               if (repTake < needed) {
                  allIngredientsSatisfied = false;
               }
            }
         }

         // 5. Add this step to the execution list
         steps.add(new CraftStep(item, recipe, craftsNeeded, totalProduced, stepConsumed));

         // 6. Deposit all produced items into virtual inventory so caller can consume what it needs
         virtualInv.put(item, virtualInv.getOrDefault(item, 0) + totalProduced);

         return allIngredientsSatisfied;
      } finally {
         activePath.remove(item);
      }
   }

   private static List<CraftStep> optimizeSteps(List<CraftStep> steps) {
      if (steps.size() <= 1) return steps;

      List<CraftStep> optimized = new ArrayList<>();
      CraftStep current = null;

      for (CraftStep step : steps) {
         if (current == null) {
            current = step;
            continue;
         }

         if (current.recipe.equals(step.recipe) && current.resultItem == step.resultItem) {
            Map<Item, Integer> mergedConsumed = new LinkedHashMap<>(current.ingredientsConsumed);
            for (Map.Entry<Item, Integer> e : step.ingredientsConsumed.entrySet()) {
               mergedConsumed.put(e.getKey(), mergedConsumed.getOrDefault(e.getKey(), 0) + e.getValue());
            }
            current = new CraftStep(current.resultItem, current.recipe, current.craftCount + step.craftCount,
               current.yieldProduced + step.yieldProduced, mergedConsumed);
         } else {
            optimized.add(current);
            current = step;
         }
      }

      if (current != null) {
         optimized.add(current);
      }

      return optimized;
   }
}
