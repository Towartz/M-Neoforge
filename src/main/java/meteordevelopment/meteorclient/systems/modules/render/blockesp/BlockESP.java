package meteordevelopment.meteorclient.systems.modules.render.blockesp;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockDataSetting;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.GenericSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.RainbowColors;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.Dimension;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;

public class BlockESP extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgShading = this.settings.createGroup("Shading");

   private final Setting<List<Block>> blocks = this.sgGeneral
      .add(new BlockListSetting.Builder().name("blocks").description("Blocks to search for.").onChanged(blocks1 -> {
         if (this.isActive() && Utils.canUpdate()) {
            this.onActivate();
         }
      }).build());
   private final Setting<ESPBlockData> defaultBlockConfig = this.sgGeneral
      .add(
         new GenericSetting.Builder<ESPBlockData>()
            .name("default-block-config")
            .description("Default block config.")
            .defaultValue(
               new ESPBlockData(ShapeMode.Both, new SettingColor(0, 255, 200), new SettingColor(0, 255, 200, 45), true, new SettingColor(0, 255, 200, 125))
            )
            .build()
      );
   private final Setting<Map<Block, ESPBlockData>> blockConfigs = this.sgGeneral
      .add(
         new BlockDataSetting.Builder<ESPBlockData>()
            .name("block-configs")
            .description("Config for each block.")
            .defaultData(this.defaultBlockConfig)
            .build()
      );
   private final Setting<Boolean> tracers = this.sgGeneral
      .add(new BoolSetting.Builder().name("tracers").description("Render tracer lines.").defaultValue(Boolean.valueOf(false)).build());

   public enum ShadingMode {
      Camera,
      Directional,
      Flat
   }

   public enum CullMode {
      None,
      Backfaces
   }

   // Shading & Clarity
   public final Setting<ShadingMode> shadingMode = this.sgShading
      .add(
         new EnumSetting.Builder<ShadingMode>()
            .name("shading-mode")
            .description("Face lighting mode: Camera (dynamic view-aware), Directional (fixed 3D angles), or Flat.")
            .defaultValue(ShadingMode.Camera)
            .build()
      );

   public final Setting<Double> bottomBrightness = this.sgShading
      .add(
         new DoubleSetting.Builder()
            .name("bottom-brightness")
            .description("Brightness multiplier for bottom faces to ensure clear visibility.")
            .defaultValue(1.0)
            .min(0.2)
            .sliderMax(2.0)
            .build()
      );

   public final Setting<Boolean> highlightBottom = this.sgShading
      .add(
         new BoolSetting.Builder()
            .name("highlight-bottom")
            .description("Keeps bottom faces vivid and un-culled when viewed from above.")
            .defaultValue(true)
            .build()
      );

   public final Setting<CullMode> cullMode = this.sgShading
      .add(
         new EnumSetting.Builder<CullMode>()
            .name("cull-mode")
            .description("Culls faces pointing away from camera. None preserves all exterior faces.")
            .defaultValue(CullMode.None)
            .build()
      );

   public final Setting<Integer> fillOpacity = this.sgShading
      .add(
         new IntSetting.Builder()
            .name("fill-opacity")
            .description("Global opacity override for shaded faces. 0 to use individual block color alpha.")
            .defaultValue(45)
            .range(0, 255)
            .sliderMax(255)
            .build()
      );

   public final Setting<Boolean> distanceFade = this.sgShading
      .add(
         new BoolSetting.Builder()
            .name("distance-fade")
            .description("Smoothly dims distant blocks to eliminate visual clutter.")
            .defaultValue(true)
            .build()
      );

   public final Setting<Double> fadeDistance = this.sgShading
      .add(
         new DoubleSetting.Builder()
            .name("fade-distance")
            .description("Distance in blocks at which shaded ESP begins to fade.")
            .defaultValue(128.0)
            .min(8.0)
            .sliderMax(384.0)
            .visible(this.distanceFade::get)
            .build()
      );

   public final Setting<Boolean> depthCompensation = this.sgShading
      .add(
         new BoolSetting.Builder()
            .name("depth-compensation")
            .description("Reduces vertical distance penalty so bedrock/deepslate ores stay visible from the surface.")
            .defaultValue(true)
            .visible(this.distanceFade::get)
            .build()
      );

   public final Setting<Integer> minOpacity = this.sgShading
      .add(
         new IntSetting.Builder()
            .name("min-opacity")
            .description("Minimum opacity floor for distant blocks so they never completely vanish.")
            .defaultValue(35)
            .range(5, 255)
            .sliderMax(255)
            .visible(this.distanceFade::get)
            .build()
      );

   public final Setting<Boolean> innerGrid = this.sgShading
      .add(
         new BoolSetting.Builder()
            .name("inner-grid")
            .description("Renders subtle inner wireframe grid lines between blocks in a vein for sharp block distinction.")
            .defaultValue(true)
            .build()
      );

   public final Setting<Boolean> mergeOreVariants = this.sgShading
      .add(
         new BoolSetting.Builder()
            .name("merge-ore-variants")
            .description("Merges regular and deepslate ore variants across Y=0 into continuous clusters.")
            .defaultValue(true)
            .onChanged(v -> {
               if (this.isActive() && Utils.canUpdate()) {
                  this.onActivate();
               }
            })
            .build()
      );
   private final MutableBlockPos blockPos = new MutableBlockPos();
   private final Long2ObjectMap<ESPChunk> chunks = new Long2ObjectOpenHashMap();
   private final Set<ESPGroup> groups = new ReferenceOpenHashSet();
   private final ExecutorService workerThread = Executors.newSingleThreadExecutor();
   private Dimension lastDimension;

   public BlockESP() {
      super(Categories.Render, "block-esp", "Renders specified blocks through walls.", "search");
      RainbowColors.register(this::onTickRainbow);
   }

   @Override
   public void onActivate() {
      synchronized (this.chunks) {
         this.chunks.clear();
         this.groups.clear();
      }

      for (ChunkAccess chunk : Utils.chunks()) {
         this.searchChunk(chunk);
      }

      this.lastDimension = PlayerUtils.getDimension();
   }

   @Override
   public void onDeactivate() {
      synchronized (this.chunks) {
         this.chunks.clear();
         this.groups.clear();
      }
   }

   private void onTickRainbow() {
      if (this.isActive()) {
         this.defaultBlockConfig.get().tickRainbow();

         for (ESPBlockData blockData : this.blockConfigs.get().values()) {
            blockData.tickRainbow();
         }
      }
   }

   public boolean isTarget(Block block) {
      if (block == null) return false;
      List<Block> targetList = this.blocks.get();
      if (targetList == null || targetList.isEmpty()) return false;
      if (targetList.contains(block)) return true;
      ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
      for (Block target : targetList) {
         if (BuiltInRegistries.BLOCK.getKey(target).equals(id)) {
            return true;
         }
      }
      if (this.mergeOreVariants.get() && id != null) {
         String path = id.getPath();
         String altPath = path.startsWith("deepslate_") ? path.substring(10) : "deepslate_" + path;
         ResourceLocation altId = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), altPath);
         for (Block target : targetList) {
            if (BuiltInRegistries.BLOCK.getKey(target).equals(altId)) {
               return true;
            }
         }
      }
      return false;
   }

   ESPBlockData getBlockData(Block block) {
      if (block == null) return this.defaultBlockConfig.get();
      Map<Block, ESPBlockData> configs = this.blockConfigs.get();
      ESPBlockData blockData = configs.get(block);
      if (blockData != null) return blockData;
      ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
      for (Map.Entry<Block, ESPBlockData> entry : configs.entrySet()) {
         if (BuiltInRegistries.BLOCK.getKey(entry.getKey()).equals(id)) {
            return entry.getValue();
         }
      }
      if (this.mergeOreVariants.get() && id != null) {
         String path = id.getPath();
         String altPath = path.startsWith("deepslate_") ? path.substring(10) : "deepslate_" + path;
         ResourceLocation altId = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), altPath);
         for (Map.Entry<Block, ESPBlockData> entry : configs.entrySet()) {
            if (BuiltInRegistries.BLOCK.getKey(entry.getKey()).equals(altId)) {
               return entry.getValue();
            }
         }
      }
      return this.defaultBlockConfig.get();
   }

   private void updateChunk(int x, int z) {
      ESPChunk chunk = (ESPChunk)this.chunks.get(ChunkPos.asLong(x, z));
      if (chunk != null) {
         chunk.update();
      }
   }

   private void updateBlock(int x, int y, int z) {
      ESPChunk chunk = (ESPChunk)this.chunks.get(ChunkPos.asLong(x >> 4, z >> 4));
      if (chunk != null) {
         chunk.update(x, y, z);
      }
   }

   public ESPBlock getBlock(int x, int y, int z) {
      ESPChunk chunk = (ESPChunk)this.chunks.get(ChunkPos.asLong(x >> 4, z >> 4));
      return chunk == null ? null : chunk.get(x, y, z);
   }

   public ESPGroup newGroup(Block block) {
      synchronized (this.chunks) {
         ESPGroup group = new ESPGroup(block);
         this.groups.add(group);
         return group;
      }
   }

   public void removeGroup(ESPGroup group) {
      synchronized (this.chunks) {
         this.groups.remove(group);
      }
   }

   @EventHandler
   private void onChunkData(ChunkDataEvent event) {
      this.searchChunk(event.chunk());
   }

   private void searchChunk(ChunkAccess chunk) {
      this.workerThread.submit(() -> {
         if (this.isActive()) {
            ESPChunk schunk = ESPChunk.searchChunk(chunk, this.blocks.get());
            if (schunk.size() > 0) {
               synchronized (this.chunks) {
                  this.chunks.put(chunk.getPos().toLong(), schunk);
                  schunk.update();
                  this.updateChunk(chunk.getPos().x - 1, chunk.getPos().z);
                  this.updateChunk(chunk.getPos().x + 1, chunk.getPos().z);
                  this.updateChunk(chunk.getPos().x, chunk.getPos().z - 1);
                  this.updateChunk(chunk.getPos().x, chunk.getPos().z + 1);
               }
            }
         }
      });
   }

   @EventHandler
   private void onBlockUpdate(BlockUpdateEvent event) {
      int bx = event.pos.getX();
      int by = event.pos.getY();
      int bz = event.pos.getZ();
      int chunkX = bx >> 4;
      int chunkZ = bz >> 4;
      boolean newIsTarget = this.isTarget(event.newState.getBlock());
      boolean oldIsTarget = this.isTarget(event.oldState.getBlock());
      boolean added = newIsTarget && !oldIsTarget;
      boolean removed = !added && !newIsTarget && oldIsTarget;
      long key = ChunkPos.asLong(chunkX, chunkZ);
      if (added || removed) {
         this.workerThread.submit(() -> {
            synchronized (this.chunks) {
               ESPChunk chunk = (ESPChunk)this.chunks.get(key);
               if (chunk == null) {
                  chunk = new ESPChunk(chunkX, chunkZ);
                  if (chunk.shouldBeDeleted()) {
                     return;
                  }

                  this.chunks.put(key, chunk);
               }

               this.blockPos.set(bx, by, bz);
               if (added) {
                  chunk.add(this.blockPos);
               } else {
                  chunk.remove(this.blockPos);
               }

               for (int x = -1; x < 2; x++) {
                  for (int z = -1; z < 2; z++) {
                     for (int y = -1; y < 2; y++) {
                        if (x != 0 || y != 0 || z != 0) {
                           this.updateBlock(bx + x, by + y, bz + z);
                        }
                     }
                  }
               }
            }
         });
      }
   }

   @EventHandler
   private void onPostTick(TickEvent.Post event) {
      Dimension dimension = PlayerUtils.getDimension();
      if (this.lastDimension != dimension) {
         this.onActivate();
      }

      this.lastDimension = dimension;
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      synchronized (this.chunks) {
         Iterator<ESPChunk> it = this.chunks.values().iterator();

         while (it.hasNext()) {
            ESPChunk chunk = it.next();
            if (chunk.shouldBeDeleted()) {
               this.workerThread.submit(() -> {
                  ObjectIterator var1 = chunk.blocks.values().iterator();

                  while (var1.hasNext()) {
                     ESPBlock block = (ESPBlock)var1.next();
                     block.group.remove(block, false);
                     block.loaded = false;
                  }
               });
               it.remove();
            } else {
               chunk.render(event);
            }
         }

         if (this.tracers.get()) {
            for (ESPGroup group : this.groups) {
               group.render(event);
            }
         }
      }
   }

   @Override
   public String getInfoString() {
      return "%s groups".formatted(this.groups.size());
   }
}
