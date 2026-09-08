package meteordevelopment.meteorclient.utils.world;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class OreDropHelper {
   private static final Map<Block, Set<Item>> DROP_CACHE = new ConcurrentHashMap<>();
   private static final Map<Block, Set<Item>> VANILLA_ORE_DROPS = new HashMap<>();

   static {
      // Coal
      addVanilla(Blocks.COAL_ORE, Items.COAL);
      addVanilla(Blocks.DEEPSLATE_COAL_ORE, Items.COAL);

      // Iron
      addVanilla(Blocks.IRON_ORE, Items.RAW_IRON, Items.IRON_INGOT);
      addVanilla(Blocks.DEEPSLATE_IRON_ORE, Items.RAW_IRON, Items.IRON_INGOT);

      // Copper
      addVanilla(Blocks.COPPER_ORE, Items.RAW_COPPER, Items.COPPER_INGOT);
      addVanilla(Blocks.DEEPSLATE_COPPER_ORE, Items.RAW_COPPER, Items.COPPER_INGOT);

      // Gold
      addVanilla(Blocks.GOLD_ORE, Items.RAW_GOLD, Items.GOLD_NUGGET, Items.GOLD_INGOT);
      addVanilla(Blocks.DEEPSLATE_GOLD_ORE, Items.RAW_GOLD, Items.GOLD_NUGGET, Items.GOLD_INGOT);
      addVanilla(Blocks.NETHER_GOLD_ORE, Items.GOLD_NUGGET, Items.RAW_GOLD, Items.GOLD_INGOT);

      // Redstone
      addVanilla(Blocks.REDSTONE_ORE, Items.REDSTONE);
      addVanilla(Blocks.DEEPSLATE_REDSTONE_ORE, Items.REDSTONE);

      // Emerald
      addVanilla(Blocks.EMERALD_ORE, Items.EMERALD);
      addVanilla(Blocks.DEEPSLATE_EMERALD_ORE, Items.EMERALD);

      // Lapis
      addVanilla(Blocks.LAPIS_ORE, Items.LAPIS_LAZULI);
      addVanilla(Blocks.DEEPSLATE_LAPIS_ORE, Items.LAPIS_LAZULI);

      // Diamond
      addVanilla(Blocks.DIAMOND_ORE, Items.DIAMOND);
      addVanilla(Blocks.DEEPSLATE_DIAMOND_ORE, Items.DIAMOND);

      // Nether Quartz
      addVanilla(Blocks.NETHER_QUARTZ_ORE, Items.QUARTZ);

      // Ancient Debris
      addVanilla(Blocks.ANCIENT_DEBRIS, Items.ANCIENT_DEBRIS, Items.NETHERITE_SCRAP);

      // Amethyst
      addVanilla(Blocks.AMETHYST_CLUSTER, Items.AMETHYST_SHARD);
   }

   private static void addVanilla(Block block, Item... items) {
      Set<Item> set = VANILLA_ORE_DROPS.computeIfAbsent(block, b -> new HashSet<>());
      Item blockItem = block.asItem();
      if (blockItem != null && blockItem != Items.AIR) {
         set.add(blockItem);
      }
      Collections.addAll(set, items);
   }

   public static Set<Item> getPossibleDrops(Block block) {
      if (block == null || block == Blocks.AIR) {
         return Collections.emptySet();
      }

      return DROP_CACHE.computeIfAbsent(block, OreDropHelper::computePossibleDrops);
   }

   private static Set<Item> computePossibleDrops(Block block) {
      Set<Item> drops = new HashSet<>();

      // 1. Direct block item (covers Silk Touch or blocks dropping themselves)
      Item blockItem = block.asItem();
      if (blockItem != null && blockItem != Items.AIR) {
         drops.add(blockItem);
      }

      // 2. Pre-mapped vanilla drops
      Set<Item> vanilla = VANILLA_ORE_DROPS.get(block);
      if (vanilla != null) {
         drops.addAll(vanilla);
      }

      // 3. Modded or heuristic resolution
      ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
      if (id != null) {
         String namespace = id.getNamespace();
         String path = id.getPath().toLowerCase(Locale.ROOT);
         String base = ChunkScannerEngine.getBaseOrePath(path);

         if (base.endsWith("_ore")) {
            base = base.substring(0, base.length() - "_ore".length());
         }
         if (base.startsWith("ore_")) {
            base = base.substring("ore_".length());
         }

         if (!base.isEmpty()) {
            List<String> candidatePaths = List.of(
               "raw_" + base,
               base + "_raw",
               base,
               base + "_ingot",
               base + "_gem",
               base + "_crystal",
               base + "_nugget",
               base + "_dust",
               base + "_chunk",
               base + "_shard"
            );

            for (Item item : BuiltInRegistries.ITEM) {
               ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
               if (itemId == null || item == Items.AIR) continue;

               String itemNamespace = itemId.getNamespace();
               String itemPath = itemId.getPath().toLowerCase(Locale.ROOT);

               // Prioritize same namespace or common namespaces
               if (itemNamespace.equals(namespace) || itemNamespace.equals("minecraft") || itemNamespace.equals("c") || itemNamespace.equals("forge")) {
                  for (String candidate : candidatePaths) {
                     if (itemPath.equals(candidate) || itemPath.endsWith("/" + candidate)) {
                        drops.add(item);
                        break;
                     }
                  }
               }
            }
         }
      }

      return Collections.unmodifiableSet(drops);
   }

   public static void registerLearnedDrop(Block block, Item item) {
      if (block == null || item == null || item == Items.AIR) return;
      Set<Item> set = DROP_CACHE.computeIfAbsent(block, k -> new HashSet<>());
      if (!(set instanceof HashSet)) {
         set = new HashSet<>(set);
         DROP_CACHE.put(block, set);
      }
      set.add(item);
   }

   public static boolean isDropOf(ItemStack stack, Block block) {
      if (stack == null || stack.isEmpty() || block == null) return false;
      return getPossibleDrops(block).contains(stack.getItem());
   }

   public static boolean isDropOfAny(ItemStack stack, Collection<Block> blocks) {
      if (stack == null || stack.isEmpty() || blocks == null || blocks.isEmpty()) return false;
      Item item = stack.getItem();
      for (Block b : blocks) {
         if (getPossibleDrops(b).contains(item)) {
            return true;
         }
      }
      return false;
   }

   public static List<ItemEntity> findNearbyTargetDrops(ClientLevel level, Vec3 center, double radius, Collection<Block> blocks) {
      if (level == null || center == null || blocks == null || blocks.isEmpty() || radius <= 0) {
         return Collections.emptyList();
      }

      AABB box = new AABB(
         center.x - radius, center.y - radius, center.z - radius,
         center.x + radius, center.y + radius, center.z + radius
      );

      List<ItemEntity> result = new ArrayList<>();
      double rSq = radius * radius;

      for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, box)) {
         if (entity == null || !entity.isAlive()) continue;
         if (entity.position().distanceToSqr(center) <= rSq) {
            ItemStack stack = entity.getItem();
            if (isDropOfAny(stack, blocks)) {
               result.add(entity);
            }
         }
      }

      return result;
   }
}
