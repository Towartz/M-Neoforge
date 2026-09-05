package meteordevelopment.meteorclient.utils.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import meteordevelopment.meteorclient.systems.modules.world.ChunkScanner.ScanMode;
import meteordevelopment.meteorclient.utils.misc.Names;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

public class ChunkScannerEngine {

   public static class DiscoveredBlockEntry {
      public final Block block;
      public final ItemStack icon;
      public final String displayName;
      public final String modName;
      public final boolean isOre;
      public final boolean isCustomTarget;
      public int count = 0;
      public int minY = Integer.MAX_VALUE;
      public int maxY = Integer.MIN_VALUE;
      public BlockPos nearestPos = null;
      public double nearestDistSq = Double.MAX_VALUE;
      public final List<BlockPos> positions = new ArrayList<>();

      public DiscoveredBlockEntry(Block block, ItemStack icon, String displayName, String modName, boolean isOre, boolean isCustomTarget) {
         this.block = block;
         this.icon = icon;
         this.displayName = displayName != null ? displayName : block.getName().getString();
         this.modName = modName;
         this.isOre = isOre;
         this.isCustomTarget = isCustomTarget;
      }

      public DiscoveredBlockEntry(Block block, ItemStack icon, String displayName, String modName) {
         this(block, icon, displayName, modName, true, false);
      }

      public void addBlock(BlockPos pos, BlockPos playerPos) {
         this.count++;
         this.positions.add(pos);
         if (pos.getY() < this.minY) this.minY = pos.getY();
         if (pos.getY() > this.maxY) this.maxY = pos.getY();

         if (playerPos != null) {
            double distSq = pos.distSqr(playerPos);
            if (distSq < this.nearestDistSq) {
               this.nearestDistSq = distSq;
               this.nearestPos = pos;
            }
         } else if (this.nearestPos == null) {
            this.nearestPos = pos;
         }
      }

      public double getDistanceSq(BlockPos playerPos) {
         if (this.nearestPos == null) return Double.MAX_VALUE;
         if (playerPos == null) return this.nearestDistSq;
         return (double)this.nearestPos.distSqr(playerPos);
      }

      public double getDistance(BlockPos playerPos) {
         double dSq = getDistanceSq(playerPos);
         return dSq == Double.MAX_VALUE ? Double.MAX_VALUE : Math.sqrt(dSq);
      }

      public String getDistanceString(BlockPos playerPos) {
         if (this.nearestPos == null) return "N/A";
         double d = getDistance(playerPos);
         return String.format(Locale.ROOT, "%.1fm", d);
      }

      public String getDeltaString(BlockPos playerPos) {
         if (this.nearestPos == null || playerPos == null) return "";
         int dx = this.nearestPos.getX() - playerPos.getX();
         int dy = this.nearestPos.getY() - playerPos.getY();
         int dz = this.nearestPos.getZ() - playerPos.getZ();
         return String.format("dX:%+d dY:%+d dZ:%+d", dx, dy, dz);
      }

      public String getBearingString(BlockPos playerPos) {
         if (this.nearestPos == null || playerPos == null) return "";
         int dx = this.nearestPos.getX() - playerPos.getX();
         int dy = this.nearestPos.getY() - playerPos.getY();
         int dz = this.nearestPos.getZ() - playerPos.getZ();

         String vertical;
         if (dy > 3) vertical = "Above (" + dy + " up)";
         else if (dy < -3) vertical = "Below (" + (-dy) + " down)";
         else vertical = "Level";

         String horizontal = "";
         if (dz < -3) horizontal += "North";
         else if (dz > 3) horizontal += "South";

         if (dx > 3) horizontal += (horizontal.isEmpty() ? "" : "-") + "East";
         else if (dx < -3) horizontal += (horizontal.isEmpty() ? "" : "-") + "West";

         if (horizontal.isEmpty()) return vertical;
         return vertical.equals("Level") ? horizontal : vertical + " " + horizontal;
      }
   }

   public static class ChunkScanResult {
      public final ChunkPos chunkPos;
      public final String biomeName;
      public final int totalBlocks;
      public final int totalOres;
      public final int totalCustom;
      public final List<DiscoveredBlockEntry> entries;

      public ChunkScanResult(ChunkPos chunkPos, String biomeName, int totalBlocks, int totalOres, int totalCustom, List<DiscoveredBlockEntry> entries) {
         this.chunkPos = chunkPos;
         this.biomeName = biomeName;
         this.totalBlocks = totalBlocks;
         this.totalOres = totalOres;
         this.totalCustom = totalCustom;
         this.entries = Collections.unmodifiableList(entries);
      }

      public ChunkScanResult(ChunkPos chunkPos, String biomeName, int totalOres, List<DiscoveredBlockEntry> entries) {
         this(chunkPos, biomeName, totalOres, totalOres, 0, entries);
      }

      public DiscoveredBlockEntry getClosestEntry(BlockPos playerPos) {
         if (this.entries.isEmpty()) return null;
         DiscoveredBlockEntry closest = null;
         double minDSq = Double.MAX_VALUE;
         for (DiscoveredBlockEntry entry : this.entries) {
            double dSq = entry.getDistanceSq(playerPos);
            if (dSq < minDSq) {
               minDSq = dSq;
               closest = entry;
            }
         }
         return closest;
      }
   }

   public static String getModName(String namespace) {
      if (namespace == null || namespace.isEmpty() || namespace.equalsIgnoreCase("minecraft")) {
         return "Minecraft";
      }
      if (namespace.equalsIgnoreCase("tfmg")) return "TFMG";
      if (namespace.equalsIgnoreCase("cgs")) return "CGS";
      if (namespace.equalsIgnoreCase("createpropulsion")) return "Create Propulsion";
      if (namespace.equalsIgnoreCase("expandeddelight")) return "Expanded Delight";
      if (namespace.equalsIgnoreCase("farmersdelight")) return "Farmer's Delight";

      String[] parts = namespace.split("[_-]");
      StringBuilder sb = new StringBuilder();
      for (String part : parts) {
         if (!part.isEmpty()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
         }
      }
      return sb.toString();
   }

   public static ChunkScanResult scanChunk(LevelChunk chunk, BlockPos playerPos) {
      return scanChunk(chunk, playerPos, ScanMode.Both, null);
   }

   public static ChunkScanResult scanChunk(LevelChunk chunk, BlockPos playerPos, ScanMode scanMode, List<Block> customBlocksList) {
      if (chunk == null) return null;
      ChunkPos chunkPos = chunk.getPos();
      Map<Block, DiscoveredBlockEntry> entries = new HashMap<>();
      int totalBlocks = 0;
      int totalOres = 0;
      int totalCustom = 0;

      boolean includeOres = (scanMode == null || scanMode == ScanMode.Both || scanMode == ScanMode.Ores);
      boolean includeCustom = (scanMode == ScanMode.Both || scanMode == ScanMode.Custom)
         && customBlocksList != null && !customBlocksList.isEmpty();

      Set<Block> customSet = includeCustom ? new HashSet<>(customBlocksList) : Collections.emptySet();
      Set<ResourceLocation> customIds = new HashSet<>();
      if (includeCustom) {
         for (Block b : customBlocksList) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
            if (id != null && !id.getPath().equals("air")) {
               customIds.add(id);
            }
         }
      }

      LevelChunkSection[] sections = chunk.getSections();
      int minBuildHeight = chunk.getMinBuildHeight();

      for (int s = 0; s < sections.length; s++) {
         LevelChunkSection section = sections[s];
         if (section == null || section.hasOnlyAir()) continue;

         boolean sectionHasTargets = section.maybeHas(state -> {
            Block b = state.getBlock();
            if (includeCustom) {
               if (customSet.contains(b)) return true;
               ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
               if (id != null && customIds.contains(id)) return true;
            }
            if (includeOres && OreDiscovery.isOre(b)) return true;
            return false;
         });

         if (!sectionHasTargets) continue;

         int sectionBaseY = minBuildHeight + (s << 4);
         PalettedContainer<BlockState> container = section.getStates();

         for (int y = 0; y < 16; y++) {
            int worldY = sectionBaseY + y;
            for (int z = 0; z < 16; z++) {
               for (int x = 0; x < 16; x++) {
                  BlockState state = container.get(x, y, z);
                  Block block = state.getBlock();
                  boolean isCustom = false;
                  if (includeCustom) {
                     if (customSet.contains(block)) {
                        isCustom = true;
                     } else {
                        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
                        if (id != null && customIds.contains(id)) {
                           isCustom = true;
                        }
                     }
                  }
                  boolean isOre = includeOres && OreDiscovery.isOre(block);

                  if (isCustom || isOre) {
                     totalBlocks++;
                     if (isOre) totalOres++;
                     if (isCustom) totalCustom++;

                     int worldX = (chunkPos.x << 4) + x;
                     int worldZ = (chunkPos.z << 4) + z;
                     BlockPos pos = new BlockPos(worldX, worldY, worldZ);

                     final boolean finalIsOre = isOre;
                     final boolean finalIsCustom = isCustom;

                     DiscoveredBlockEntry entry = entries.computeIfAbsent(block, b -> {
                        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
                        String mod = getModName(id != null ? id.getNamespace() : "minecraft");
                        String name = Names.get(b);
                        ItemStack stack = b.asItem().getDefaultInstance();
                        return new DiscoveredBlockEntry(b, stack, name, mod, finalIsOre, finalIsCustom);
                     });

                     entry.addBlock(pos, playerPos);
                  }
               }
            }
         }
      }

      List<DiscoveredBlockEntry> list = new ArrayList<>(entries.values());
      list.sort(Comparator.comparingInt((DiscoveredBlockEntry e) -> e.count).reversed());

      String biomeName = "";
      try {
         biomeName = chunk.getNoiseBiome(0, 0, 0).unwrapKey().map(k -> k.location().getPath()).orElse("Unknown");
      } catch (Throwable ignored) {
      }

      return new ChunkScanResult(chunkPos, biomeName, totalBlocks, totalOres, totalCustom, list);
   }

   public static ChunkScanResult mergeResults(ChunkPos centerPos, List<ChunkScanResult> results, BlockPos playerPos) {
      if (results == null || results.isEmpty()) {
         return new ChunkScanResult(centerPos, "Unknown", 0, 0, 0, Collections.emptyList());
      }
      if (results.size() == 1) {
         return results.get(0);
      }

      Map<Block, DiscoveredBlockEntry> merged = new HashMap<>();
      int totalBlocks = 0;
      int totalOres = 0;
      int totalCustom = 0;
      String biomeName = results.get(0).biomeName;

      for (ChunkScanResult res : results) {
         totalBlocks += res.totalBlocks;
         totalOres += res.totalOres;
         totalCustom += res.totalCustom;

         for (DiscoveredBlockEntry entry : res.entries) {
            DiscoveredBlockEntry target = merged.computeIfAbsent(entry.block, b ->
               new DiscoveredBlockEntry(entry.block, entry.icon, entry.displayName, entry.modName, entry.isOre, entry.isCustomTarget)
            );
            for (BlockPos pos : entry.positions) {
               target.addBlock(pos, playerPos);
            }
         }
      }

      List<DiscoveredBlockEntry> list = new ArrayList<>(merged.values());
      list.sort(Comparator.comparingInt((DiscoveredBlockEntry e) -> e.count).reversed());

      return new ChunkScanResult(centerPos, biomeName, totalBlocks, totalOres, totalCustom, list);
   }

   public static String getBaseOrePath(String path) {
      if (path == null) return "";
      String p = path.toLowerCase(Locale.ROOT);
      if (p.startsWith("deepslate_")) {
         p = p.substring("deepslate_".length());
      } else if (p.startsWith("nether_")) {
         p = p.substring("nether_".length());
      } else if (p.startsWith("end_")) {
         p = p.substring("end_".length());
      }
      return p;
   }

   public static boolean isSameOreFamily(Block a, Block b) {
      if (a == null || b == null) return false;
      if (a == b) return true;
      ResourceLocation idA = BuiltInRegistries.BLOCK.getKey(a);
      ResourceLocation idB = BuiltInRegistries.BLOCK.getKey(b);
      if (idA == null || idB == null) return false;
      if (!idA.getNamespace().equals(idB.getNamespace())) return false;
      return getBaseOrePath(idA.getPath()).equals(getBaseOrePath(idB.getPath()));
   }

   public static List<Block> getFamilyBlocks(DiscoveredBlockEntry entry, List<DiscoveredBlockEntry> allEntries) {
      List<Block> family = new ArrayList<>();
      if (entry == null) return family;
      family.add(entry.block);
      if (allEntries == null) return family;

      for (DiscoveredBlockEntry other : allEntries) {
         if (other.block != entry.block && isSameOreFamily(entry.block, other.block)) {
            if (!family.contains(other.block)) {
               family.add(other.block);
            }
         }
      }
      return family;
   }
}
