package meteordevelopment.meteorclient.systems.modules.player.autocraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class CraftItemResolver {
   private CraftItemResolver() {
   }

   public static Item resolveSingle(String query) {
      if (query == null || query.isBlank()) {
         return null;
      }

      query = query.trim().toLowerCase(Locale.ROOT);

      // 1. Exact ResourceLocation
      ResourceLocation rl = ResourceLocation.tryParse(query);
      if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
         return BuiltInRegistries.ITEM.get(rl);
      }

      // 2. Default minecraft namespace
      ResourceLocation mcRl = ResourceLocation.fromNamespaceAndPath("minecraft", query);
      if (BuiltInRegistries.ITEM.containsKey(mcRl)) {
         return BuiltInRegistries.ITEM.get(mcRl);
      }

      // 3. Normalized string (spaces & hyphens to underscores, common colloquial aliases)
      String normalized = query.replace(" ", "_").replace("-", "_");
      if (normalized.equals("workbench") || normalized.equals("table")) {
         normalized = "crafting_table";
      } else if (normalized.equals("torches")) {
         normalized = "torch";
      }

      ResourceLocation normRl = ResourceLocation.fromNamespaceAndPath("minecraft", normalized);
      if (BuiltInRegistries.ITEM.containsKey(normRl)) {
         return BuiltInRegistries.ITEM.get(normRl);
      }

      // 4. Exact path match across all registered items (including mods)
      for (Item item : BuiltInRegistries.ITEM) {
         ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
         if (id.getPath().equalsIgnoreCase(normalized)) {
            return item;
         }
      }

      // 5. Two-word inverted match (e.g. "pickaxe diamond" -> "diamond_pickaxe")
      String[] parts = normalized.split("_");
      if (parts.length == 2) {
         String inverted = parts[1] + "_" + parts[0];
         for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id.getPath().equalsIgnoreCase(inverted)) {
               return item;
            }
         }
      }

      // 6. Dynamic search across all registered items (vanilla & mods),
      // prioritizing current inventory craftability, word-token precision, namespace, and length.
      Map<Item, Integer> pool = CraftRecipeHelper.getAvailableInventoryPool(false);
      List<Item> matches = new ArrayList<>();
      final String norm = normalized;

      for (Item item : BuiltInRegistries.ITEM) {
         ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
         String path = id.getPath().toLowerCase(Locale.ROOT);
         if (path.contains(norm) || id.toString().toLowerCase(Locale.ROOT).contains(norm)) {
            matches.add(item);
         }
      }

      if (!matches.isEmpty()) {
         matches.sort((a, b) -> {
            int craftA = CraftRecipeHelper.getCraftabilityScore(a, pool, false);
            int craftB = CraftRecipeHelper.getCraftabilityScore(b, pool, false);
            boolean canCraftA = craftA > 0;
            boolean canCraftB = craftB > 0;
            if (canCraftA != canCraftB) return canCraftA ? -1 : 1;
            if (canCraftA && craftA != craftB) return Integer.compare(craftB, craftA);

            ResourceLocation idA = BuiltInRegistries.ITEM.getKey(a);
            ResourceLocation idB = BuiltInRegistries.ITEM.getKey(b);
            String pathA = idA.getPath().toLowerCase(Locale.ROOT);
            String pathB = idB.getPath().toLowerCase(Locale.ROOT);

            // Word token match priority (e.g. "_bed" or "bed_" vs substring like "bedrock")
            boolean wordA = pathA.equals(norm) || pathA.endsWith("_" + norm) || pathA.startsWith(norm + "_") || pathA.contains("_" + norm + "_");
            boolean wordB = pathB.equals(norm) || pathB.endsWith("_" + norm) || pathB.startsWith(norm + "_") || pathB.contains("_" + norm + "_");
            if (wordA != wordB) return wordA ? -1 : 1;

            // Minecraft namespace priority
            boolean mcA = idA.getNamespace().equals("minecraft");
            boolean mcB = idB.getNamespace().equals("minecraft");
            if (mcA != mcB) return mcA ? -1 : 1;

            // Shorter path priority
            if (pathA.length() != pathB.length()) {
               return Integer.compare(pathA.length(), pathB.length());
            }
            return pathA.compareTo(pathB);
         });
         return matches.get(0);
      }

      return null;
   }

   public static List<Item> resolveBundle(String query) {
      if (query == null || query.isBlank()) {
         return Collections.emptyList();
      }

      String lower = query.trim().toLowerCase(Locale.ROOT).replace(" ", "_").replace("-", "_");

      // Check Armor bundle
      if (lower.contains("armor")) {
         String material = extractMaterial(lower, "armor");
         if (material != null) {
            return getArmorSet(material);
         }
      }

      // Check Tools bundle
      if (lower.contains("tool") || lower.contains("tools")) {
         String material = extractMaterial(lower, "tool");
         if (material == null) {
            material = extractMaterial(lower, "tools");
         }
         if (material != null) {
            return getToolsSet(material);
         }
      }

      // Single item fallback
      Item single = resolveSingle(query);
      if (single != null) {
         return List.of(single);
      }

      return Collections.emptyList();
   }

   private static String extractMaterial(String text, String keyword) {
      String stripped = text.replace(keyword, "").replace("_", "").trim();
      return stripped.isEmpty() ? "diamond" : stripped;
   }

   public static List<Item> getArmorSet(String material) {
      List<Item> items = new ArrayList<>();
      addIfResolved(items, material + "_helmet");
      addIfResolved(items, material + "_chestplate");
      addIfResolved(items, material + "_leggings");
      addIfResolved(items, material + "_boots");
      return items;
   }

   public static List<Item> getToolsSet(String material) {
      List<Item> items = new ArrayList<>();
      addIfResolved(items, material + "_pickaxe");
      addIfResolved(items, material + "_axe");
      addIfResolved(items, material + "_shovel");
      addIfResolved(items, material + "_sword");
      addIfResolved(items, material + "_hoe");
      return items;
   }

   private static void addIfResolved(List<Item> list, String name) {
      Item item = resolveSingle(name);
      if (item != null) {
         list.add(item);
      }
   }
}
