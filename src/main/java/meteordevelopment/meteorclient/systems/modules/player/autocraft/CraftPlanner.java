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
      public final int deficitNeeded;
      public final Map<Item, Integer> ingredientsConsumed;

      public CraftStep(Item resultItem, RecipeHolder<CraftingRecipe> recipe, int craftCount, int yieldProduced, int deficitNeeded, Map<Item, Integer> ingredientsConsumed) {
         this.resultItem = resultItem;
         this.recipe = recipe;
         this.craftCount = craftCount;
         this.yieldProduced = yieldProduced;
         this.deficitNeeded = deficitNeeded;
         this.ingredientsConsumed = ingredientsConsumed;
      }

      public CraftStep(Item resultItem, RecipeHolder<CraftingRecipe> recipe, int craftCount, int yieldProduced, Map<Item, Integer> ingredientsConsumed) {
         this(resultItem, recipe, craftCount, yieldProduced, yieldProduced, ingredientsConsumed);
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

   private static final int MAX_DEPTH = 16;

   public static CraftPlan createPlan(Item targetItem, int targetCount) {
      return createPlan(targetItem, targetCount, null);
   }

   public static CraftPlan createPlan(Item targetItem, int targetCount, RecipeHolder<CraftingRecipe> preferredRootRecipe) {
      if (targetItem == null || targetCount <= 0 || MeteorClient.mc.level == null) {
         return new CraftPlan(targetItem, targetCount, Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), false);
      }

      Map<Item, Integer> virtualInv = CraftRecipeHelper.getAvailableInventoryPool(false);
      Map<Item, Integer> startingInv = new HashMap<>(virtualInv);
      List<CraftStep> steps = new ArrayList<>();
      Map<Item, Integer> missingRaw = new LinkedHashMap<>();
      Set<Item> activePath = new HashSet<>();

      boolean satisfied = planItem(targetItem, targetCount, virtualInv, steps, missingRaw, activePath, 0, preferredRootRecipe);

      // Determine which raw materials were actually consumed from starting inventory
      Map<Item, Integer> rawMaterialsNeeded = new LinkedHashMap<>();
      for (Map.Entry<Item, Integer> entry : startingInv.entrySet()) {
         if (entry.getKey() == targetItem) continue;
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

   private static class GroupedIngredient {
      final Ingredient ingredient;
      int count;

      GroupedIngredient(Ingredient ingredient, int count) {
         this.ingredient = ingredient;
         this.count = count;
      }
   }

   private static boolean areIngredientsEquivalent(Ingredient a, Ingredient b) {
      if (a == b) return true;
      if (a == null || b == null) return false;
      List<Item> listA = CraftRecipeHelper.getItemsForIngredient(a);
      List<Item> listB = CraftRecipeHelper.getItemsForIngredient(b);
      if (listA == listB) return true;
      if (listA.size() != listB.size()) return false;
      if (listA.isEmpty()) return true;
      if (listA.size() == 1) return listA.get(0) == listB.get(0);
      return listA.containsAll(listB) && listB.containsAll(listA);
   }

   private static boolean planItem(Item item, int neededCount, Map<Item, Integer> virtualInv, List<CraftStep> steps,
                                   Map<Item, Integer> missingRaw, Set<Item> activePath, int depth,
                                   RecipeHolder<CraftingRecipe> preferredRecipe) {
      if (neededCount <= 0) return true;

      // 1. Take as much as possible from virtual inventory / surplus (intermediate sub-ingredients only)
      if (depth > 0) {
         int have = virtualInv.getOrDefault(item, 0);
         int taken = Math.min(have, neededCount);
         if (taken > 0) {
            virtualInv.put(item, have - taken);
            neededCount -= taken;
         }
         if (neededCount <= 0) return true;
      }

      // 2. Prevent recursion cycles and deep recursion
      if (depth > MAX_DEPTH) {
         missingRaw.put(item, missingRaw.getOrDefault(item, 0) + neededCount);
         return false;
      }

      // If item is already in active recursion path, it's a circular dependency!
      // Return false WITHOUT adding item to missingRaw (it is not a base raw material, it's a loop).
      if (!activePath.add(item)) {
         return false;
      }

      try {
         // Gather candidate recipes in priority order
         List<RecipeHolder<CraftingRecipe>> candidates = new ArrayList<>();

         if (depth == 0 && preferredRecipe != null) {
            candidates.add(preferredRecipe);
         }

         // Check decompression first (e.g. Iron Block -> 9 Iron Ingots)
         RecipeHolder<CraftingRecipe> decomp = CraftRecipeHelper.findDecompressionRecipe(item, virtualInv);
         if (decomp != null) {
            Item source = CraftRecipeHelper.getSingleIngredientItem(decomp);
            if (source != null && virtualInv.getOrDefault(source, 0) > 0 && !activePath.contains(source) && !candidates.contains(decomp)) {
               candidates.add(decomp);
            }
         }

         for (RecipeHolder<CraftingRecipe> cand : CraftRecipeHelper.getCandidateRecipes(item, virtualInv)) {
            if (!candidates.contains(cand)) {
               candidates.add(cand);
            }
         }

         if (candidates.isEmpty()) {
            // No craft recipe available: item is a missing base raw material
            missingRaw.put(item, missingRaw.getOrDefault(item, 0) + neededCount);
            return false;
         }

         boolean anySatisfied = false;
         Map<Item, Integer> bestVirtualInv = null;
         List<CraftStep> bestSteps = null;
         Map<Item, Integer> bestMissingRaw = null;
         int bestMissingScore = Integer.MAX_VALUE;

         int testedCount = 0;
         int maxToTest = (depth == 0) ? 3 : 2;

         for (RecipeHolder<CraftingRecipe> recipe : candidates) {
            if (testedCount >= maxToTest) break;
            testedCount++;

            Map<Item, Integer> snapInv = new HashMap<>(virtualInv);
            List<CraftStep> snapSteps = new ArrayList<>(steps);
            Map<Item, Integer> snapMissing = new LinkedHashMap<>(missingRaw);

            boolean success = planWithRecipe(recipe, item, neededCount, snapInv, snapSteps, snapMissing, activePath, depth);

            if (success && snapMissing.isEmpty()) {
               virtualInv.clear();
               virtualInv.putAll(snapInv);
               steps.clear();
               steps.addAll(snapSteps);
               missingRaw.clear();
               anySatisfied = true;
               break;
            }

            int score = 0;
            for (int count : snapMissing.values()) score += count;
            if (CraftRecipeHelper.is1to1Conversion(recipe)) score += 5000;

            if (score < bestMissingScore) {
               bestMissingScore = score;
               bestVirtualInv = snapInv;
               bestSteps = snapSteps;
               bestMissingRaw = snapMissing;
            }
         }

         if (!anySatisfied) {
            if (bestVirtualInv != null) {
               virtualInv.clear();
               virtualInv.putAll(bestVirtualInv);
               steps.clear();
               steps.addAll(bestSteps);
               missingRaw.clear();
               missingRaw.putAll(bestMissingRaw);
            }
            return false;
         }

         return true;
      } finally {
         activePath.remove(item);
      }
   }

   private static boolean planWithRecipe(RecipeHolder<CraftingRecipe> recipe, Item item, int neededCount,
                                         Map<Item, Integer> virtualInv, List<CraftStep> steps,
                                         Map<Item, Integer> missingRaw, Set<Item> activePath, int depth) {
      int yield = CraftRecipeHelper.getResultCount(recipe);
      int craftsNeeded = (int) Math.ceil((double) neededCount / yield);
      int totalProduced = craftsNeeded * yield;

      boolean allIngredientsSatisfied = true;
      Map<Item, Integer> stepConsumed = new LinkedHashMap<>();

      // 4. Group equivalent ingredients in the recipe to prevent per-slot fragmentation
      List<GroupedIngredient> groupedIngredients = new ArrayList<>();
      for (Ingredient ingredient : recipe.value().getIngredients()) {
         if (ingredient.isEmpty()) continue;
         boolean merged = false;
         for (GroupedIngredient gi : groupedIngredients) {
            if (areIngredientsEquivalent(gi.ingredient, ingredient)) {
               gi.count++;
               merged = true;
               break;
            }
         }
         if (!merged) {
            groupedIngredients.add(new GroupedIngredient(ingredient, 1));
         }
      }

      // 5. Resolve each grouped ingredient required by the recipe in batch
      for (GroupedIngredient gi : groupedIngredients) {
         int totalNeeded = gi.count * craftsNeeded;
         int needed = totalNeeded;

         // Check if virtual inventory has an item matching this ingredient
         List<Item> matchingItems = CraftRecipeHelper.getItemsForIngredient(gi.ingredient);
         for (Map.Entry<Item, Integer> entry : virtualInv.entrySet()) {
            if (entry.getValue() > 0 && matchingItems.contains(entry.getKey())) {
               int take = Math.min(needed, entry.getValue());
               entry.setValue(entry.getValue() - take);
               stepConsumed.put(entry.getKey(), stepConsumed.getOrDefault(entry.getKey(), 0) + take);
               needed -= take;
               if (needed <= 0) break;
            }
         }

         if (needed > 0) {
            // Deficit: recursively plan and produce the needed matching ingredient in batch
            List<Item> candidates = CraftRecipeHelper.getCandidateItemsForIngredient(gi.ingredient, false, virtualInv);
            if (candidates.isEmpty()) {
               Item missingItem = !matchingItems.isEmpty() ? matchingItems.get(0) : item;
               missingRaw.put(missingItem, missingRaw.getOrDefault(missingItem, 0) + needed);
               allIngredientsSatisfied = false;
               continue;
            }

            boolean candidateSatisfied = false;
            Map<Item, Integer> bestCandidateInv = null;
            List<CraftStep> bestCandidateSteps = null;
            Map<Item, Integer> bestCandidateMissing = null;
            int bestMissingScore = Integer.MAX_VALUE;
            Item chosenRep = null;

            // Limit candidates to test to prevent combinatorial explosion:
            // 1. Test only candidates with positive craftability score (materials available in inventory)
            // 2. Cap at top 2 viable candidates
            // 3. If none has positive score, test ONLY the single top candidate as fallback
            List<Item> toTest = new ArrayList<>();
            for (Item cand : candidates) {
               if (cand == null || cand == Items.AIR) continue;
               int score = CraftRecipeHelper.getCraftabilityScore(cand, virtualInv, false);
               if (score > 0) {
                  toTest.add(cand);
                  if (toTest.size() >= 2) break;
               }
            }
            if (toTest.isEmpty()) {
               toTest.add(candidates.get(0));
            }

            for (Item cand : toTest) {
               if (cand == null || cand == Items.AIR || activePath.contains(cand)) continue;

               Map<Item, Integer> snapInv = new HashMap<>(virtualInv);
               List<CraftStep> snapSteps = new ArrayList<>(steps);
               Map<Item, Integer> snapMissing = new LinkedHashMap<>(missingRaw);

               boolean subSuccess = planItem(cand, needed, snapInv, snapSteps, snapMissing, activePath, depth + 1, null);

               int repHave = snapInv.getOrDefault(cand, 0);
               int repTake = Math.min(needed, repHave);
               if (subSuccess && repTake >= needed && snapMissing.isEmpty()) {
                  // Perfect satisfaction with zero missing materials!
                  snapInv.put(cand, repHave - repTake);
                  virtualInv.clear();
                  virtualInv.putAll(snapInv);
                  steps.clear();
                  steps.addAll(snapSteps);
                  missingRaw.clear();
                  stepConsumed.put(cand, stepConsumed.getOrDefault(cand, 0) + repTake);
                  candidateSatisfied = true;
                  chosenRep = cand;
                  break;
               }

               int score = 0;
               for (int count : snapMissing.values()) score += count;
               if (score < bestMissingScore) {
                  bestMissingScore = score;
                  bestCandidateInv = snapInv;
                  bestCandidateSteps = snapSteps;
                  bestCandidateMissing = snapMissing;
                  chosenRep = cand;
               }
            }

            if (!candidateSatisfied) {
               allIngredientsSatisfied = false;
               if (bestCandidateInv != null) {
                  virtualInv.clear();
                  virtualInv.putAll(bestCandidateInv);
                  steps.clear();
                  steps.addAll(bestCandidateSteps);
                  missingRaw.clear();
                  missingRaw.putAll(bestCandidateMissing);
                  if (chosenRep != null) {
                     int repHave = virtualInv.getOrDefault(chosenRep, 0);
                     int repTake = Math.min(needed, repHave);
                     if (repTake > 0) {
                        virtualInv.put(chosenRep, repHave - repTake);
                        stepConsumed.put(chosenRep, stepConsumed.getOrDefault(chosenRep, 0) + repTake);
                     }
                  }
               }
            }
         }
      }

      // 6. Add this step to the execution list
      steps.add(new CraftStep(item, recipe, craftsNeeded, totalProduced, neededCount, stepConsumed));

      // 7. Deposit all produced items into virtual inventory so caller can consume what it needs
      virtualInv.put(item, virtualInv.getOrDefault(item, 0) + totalProduced);

      // 8. Credit byproducts / return items (e.g. empty buckets from milk, glass bottles from honey)
      for (Map.Entry<Item, Integer> consumed : stepConsumed.entrySet()) {
         Item remainder = CraftRecipeHelper.getCraftingRemainder(consumed.getKey());
         if (remainder != null && remainder != Items.AIR) {
            virtualInv.put(remainder, virtualInv.getOrDefault(remainder, 0) + consumed.getValue());
         }
      }

      return allIngredientsSatisfied;
   }

   private static List<CraftStep> optimizeSteps(List<CraftStep> steps) {
      if (steps.size() <= 1) return steps;

      List<CraftStep> result = new ArrayList<>(steps);
      boolean mergedAny = true;

      while (mergedAny) {
         mergedAny = false;
         for (int i = 0; i < result.size(); i++) {
            CraftStep a = result.get(i);
            for (int j = i + 1; j < result.size(); j++) {
               CraftStep b = result.get(j);
               if (a.recipe.equals(b.recipe) && a.resultItem == b.resultItem) {
                  // Check if any intermediate step between i and j produces an ingredient that b consumes
                  boolean dependsOnIntermediate = false;
                  for (int k = i + 1; k < j; k++) {
                     CraftStep mid = result.get(k);
                     if (b.ingredientsConsumed.containsKey(mid.resultItem)) {
                        dependsOnIntermediate = true;
                        break;
                     }
                  }

                  if (!dependsOnIntermediate) {
                     // b can safely be merged into a
                     Map<Item, Integer> mergedConsumed = new LinkedHashMap<>(a.ingredientsConsumed);
                     for (Map.Entry<Item, Integer> e : b.ingredientsConsumed.entrySet()) {
                        mergedConsumed.put(e.getKey(), mergedConsumed.getOrDefault(e.getKey(), 0) + e.getValue());
                     }
                     CraftStep merged = new CraftStep(a.resultItem, a.recipe,
                        a.craftCount + b.craftCount,
                        a.yieldProduced + b.yieldProduced,
                        a.deficitNeeded + b.deficitNeeded,
                        mergedConsumed);
                     result.set(i, merged);
                     result.remove(j);
                     mergedAny = true;
                     break;
                  }
               }
            }
            if (mergedAny) break;
         }
      }

      return result;
   }
}
