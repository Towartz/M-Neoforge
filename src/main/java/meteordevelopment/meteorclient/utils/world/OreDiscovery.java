package meteordevelopment.meteorclient.utils.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import meteordevelopment.meteorclient.utils.misc.Names;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class OreDiscovery {
   private static final List<Block> DISCOVERED_ORES = new ArrayList<>();
   private static boolean scanned = false;

   private static final java.util.Map<Block, Boolean> IS_ORE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

   private static final net.minecraft.tags.TagKey<Block> C_ORES = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "ores"));
   private static final net.minecraft.tags.TagKey<Block> FORGE_ORES = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("forge", "ores"));
   private static final net.minecraft.tags.TagKey<Block> C_ORES_IN_GROUND = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "ores_in_ground"));

   private OreDiscovery() {
   }

   public static boolean isOre(Block block) {
      if (block == null || block == Blocks.AIR) {
         return false;
      }

      Boolean cached = IS_ORE_CACHE.get(block);
      if (cached != null) {
         return cached;
      }

      boolean result = computeIsOre(block);
      IS_ORE_CACHE.put(block, result);
      return result;
   }

   private static boolean computeIsOre(Block block) {
      // 1. Tag check (vanilla BlockTags and conventional c:ores / forge:ores / c:ores_in_ground)
      try {
         var state = block.defaultBlockState();
         if (state.is(C_ORES) || state.is(FORGE_ORES) || state.is(C_ORES_IN_GROUND)) {
            return true;
         }
         if (state.is(BlockTags.COAL_ORES) || state.is(BlockTags.IRON_ORES)
            || state.is(BlockTags.COPPER_ORES) || state.is(BlockTags.GOLD_ORES)
            || state.is(BlockTags.REDSTONE_ORES) || state.is(BlockTags.LAPIS_ORES)
            || state.is(BlockTags.DIAMOND_ORES) || state.is(BlockTags.EMERALD_ORES)
            || block == Blocks.ANCIENT_DEBRIS) {
            return true;
         }
      } catch (Throwable ignored) {
      }

      // 2. ResourceLocation path heuristics
      ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
      if (id != null) {
         String path = id.getPath().toLowerCase(Locale.ROOT);
         if (path.endsWith("_ore") || path.startsWith("ore_") || path.contains("_ore_")
            || path.equals("ancient_debris")
            || (path.contains("debris") && !path.contains("brick") && !path.contains("tile") && !path.contains("pillar"))
            || (path.startsWith("raw_") && path.endsWith("_block"))
            || path.endsWith("_cluster") || path.equals("budding_amethyst")) {
            return true;
         }
      }

      // 3. Display name check
      try {
         String name = Names.get(block);
         if (name != null) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.endsWith(" ore") || lower.startsWith("ore ") || lower.contains(" ore ")
               || lower.endsWith(" cluster") || (lower.contains("raw ") && lower.endsWith(" block"))) {
               return true;
            }
         }
      } catch (Throwable ignored) {
      }

      return false;
   }

   public static synchronized List<Block> getOres() {
      if (!scanned || DISCOVERED_ORES.isEmpty()) {
         scanOres();
      }
      return Collections.unmodifiableList(DISCOVERED_ORES);
   }

   public static synchronized void scanOres() {
      IS_ORE_CACHE.clear();
      DISCOVERED_ORES.clear();
      for (Block block : BuiltInRegistries.BLOCK) {
         if (isOre(block) && !DISCOVERED_ORES.contains(block)) {
            DISCOVERED_ORES.add(block);
         }
      }
      scanned = true;
   }

   public static Block findOre(String query) {
      if (query == null || query.isEmpty()) {
         return null;
      }
      String clean = query.trim().toLowerCase(Locale.ROOT);
      if (clean.startsWith("minecraft:")) {
         clean = clean.substring(10);
      }

      for (Block ore : getOres()) {
         ResourceLocation id = BuiltInRegistries.BLOCK.getKey(ore);
         if (id != null) {
            if (id.toString().equalsIgnoreCase(clean) || id.getPath().equalsIgnoreCase(clean)) {
               return ore;
            }
         }
         String name = Names.get(ore);
         if (name != null && name.equalsIgnoreCase(clean)) {
            return ore;
         }
      }
      return null;
   }

   public static int applyTo(Collection<Block> targetCollection) {
      int added = 0;
      for (Block ore : getOres()) {
         if (!targetCollection.contains(ore)) {
            targetCollection.add(ore);
            added++;
         }
      }
      return added;
   }
}
