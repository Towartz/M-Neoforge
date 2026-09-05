package meteordevelopment.meteorclient.systems.modules.render.blockesp;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

public class ESPChunk {
   private final int x;
   private final int z;
   public Long2ObjectMap<ESPBlock> blocks;

   public ESPChunk(int x, int z) {
      this.x = x;
      this.z = z;
   }

   public ESPBlock get(int x, int y, int z) {
      return this.blocks == null ? null : (ESPBlock)this.blocks.get(ESPBlock.getKey(x, y, z));
   }

   public void add(BlockPos blockPos, boolean update) {
      ESPBlock block = new ESPBlock(blockPos.getX(), blockPos.getY(), blockPos.getZ());
      if (this.blocks == null) {
         this.blocks = new Long2ObjectOpenHashMap(64);
      }

      this.blocks.put(ESPBlock.getKey(blockPos), block);
      if (update) {
         block.update();
      }
   }

   public void add(BlockPos blockPos) {
      this.add(blockPos, true);
   }

   public void remove(BlockPos blockPos) {
      if (this.blocks != null) {
         ESPBlock block = (ESPBlock)this.blocks.remove(ESPBlock.getKey(blockPos));
         if (block != null) {
            block.group.remove(block);
         }
      }
   }

   public void update() {
      if (this.blocks != null) {
         ObjectIterator var1 = this.blocks.values().iterator();

         while (var1.hasNext()) {
            ESPBlock block = (ESPBlock)var1.next();
            block.update();
         }
      }
   }

   public void update(int x, int y, int z) {
      if (this.blocks != null) {
         ESPBlock block = (ESPBlock)this.blocks.get(ESPBlock.getKey(x, y, z));
         if (block != null) {
            block.update();
         }
      }
   }

   public int size() {
      return this.blocks == null ? 0 : this.blocks.size();
   }

   public boolean shouldBeDeleted() {
      int viewDist = Utils.getRenderDistance() + 1;
      int chunkX = SectionPos.blockToSectionCoord(MeteorClient.mc.player.blockPosition().getX());
      int chunkZ = SectionPos.blockToSectionCoord(MeteorClient.mc.player.blockPosition().getZ());
      return this.x > chunkX + viewDist || this.x < chunkX - viewDist || this.z > chunkZ + viewDist || this.z < chunkZ - viewDist;
   }

   public void render(Render3DEvent event) {
      if (this.blocks != null) {
         ObjectIterator var2 = this.blocks.values().iterator();

         while (var2.hasNext()) {
            ESPBlock block = (ESPBlock)var2.next();
            block.render(event);
         }
      }
   }

   public static ESPChunk searchChunk(ChunkAccess chunk, List<Block> blocks) {
      ESPChunk schunk = new ESPChunk(chunk.getPos().x, chunk.getPos().z);
      if (schunk.shouldBeDeleted() || blocks == null || blocks.isEmpty()) {
         return schunk;
      }

      Set<Block> blockSet = new HashSet<>(blocks);
      Set<ResourceLocation> blockIds = new HashSet<>();
      for (Block b : blocks) {
         ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
         if (id != null && !id.getPath().equals("air")) {
            blockIds.add(id);
         }
      }

      BlockESP blockEsp = Modules.get().get(BlockESP.class);
      if (blockEsp != null && blockEsp.mergeOreVariants.get()) {
         Set<ResourceLocation> extraIds = new HashSet<>();
         for (ResourceLocation id : blockIds) {
            String path = id.getPath();
            String altPath = path.startsWith("deepslate_") ? path.substring(10) : "deepslate_" + path;
            ResourceLocation altId = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), altPath);
            if (BuiltInRegistries.BLOCK.containsKey(altId)) {
               extraIds.add(altId);
               blockSet.add(BuiltInRegistries.BLOCK.get(altId));
            }
         }
         blockIds.addAll(extraIds);
      }

      LevelChunkSection[] sections = chunk.getSections();
      if (sections == null) return schunk;

      int minBuildHeight = chunk.getMinBuildHeight();
      ChunkPos chunkPos = chunk.getPos();
      MutableBlockPos blockPos = new MutableBlockPos();

      for (int s = 0; s < sections.length; s++) {
         LevelChunkSection section = sections[s];
         if (section == null || section.hasOnlyAir()) continue;

         boolean maybeHas = section.maybeHas(state -> {
            Block b = state.getBlock();
            if (blockSet.contains(b)) return true;
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
            return id != null && blockIds.contains(id);
         });
         if (!maybeHas) continue;

         int sectionBaseY = minBuildHeight + (s << 4);
         PalettedContainer<BlockState> container = section.getStates();

         for (int y = 0; y < 16; y++) {
            int worldY = sectionBaseY + y;
            for (int z = 0; z < 16; z++) {
               int worldZ = (chunkPos.z << 4) + z;
               for (int x = 0; x < 16; x++) {
                  BlockState state = container.get(x, y, z);
                  Block b = state.getBlock();
                  boolean match = blockSet.contains(b);
                  if (!match) {
                     ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
                     match = (id != null && blockIds.contains(id));
                  }
                  if (match) {
                     blockPos.set((chunkPos.x << 4) + x, worldY, worldZ);
                     schunk.add(blockPos, false);
                  }
               }
            }
         }
      }

      return schunk;
   }
}
