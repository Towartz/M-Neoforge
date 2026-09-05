package meteordevelopment.meteorclient.systems.modules.world;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.process.IMineProcess;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.screens.ChunkScannerScreen;
import meteordevelopment.meteorclient.gui.screens.settings.BlockListSettingScreen;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.ChunkScannerEngine;
import meteordevelopment.meteorclient.utils.world.ChunkScannerEngine.ChunkScanResult;
import meteordevelopment.meteorclient.utils.world.ChunkScannerEngine.DiscoveredBlockEntry;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;

public class ChunkScanner extends Module {
   public enum ScanMode {
      Both("Ores & Custom"),
      Custom("Custom Only"),
      Ores("Ores Only");

      public final String title;

      ScanMode(String title) {
         this.title = title;
      }

      public ScanMode next() {
         ScanMode[] vals = values();
         return vals[(this.ordinal() + 1) % vals.length];
      }

      @Override
      public String toString() {
         return this.title;
      }
   }

   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgTargeting = this.settings.createGroup("Targeting");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final SettingGroup sgMining = this.settings.createGroup("Mining Supervisor");

   // Targeting Settings
   public final Setting<ScanMode> scanMode = this.sgTargeting
      .add(new EnumSetting.Builder<ScanMode>()
         .name("scan-mode")
         .description("Which blocks to discover: ores, custom blocks, or both.")
         .defaultValue(ScanMode.Both)
         .onChanged(m -> this.forceScan())
         .build());

   public final Setting<List<Block>> customBlocks = this.sgTargeting
      .add(new BlockListSetting.Builder()
         .name("custom-blocks")
         .description("Specific vanilla or modded blocks to target in chunks.")
         .defaultValue(new ArrayList<>())
         .onChanged(b -> this.forceScan())
         .build());

   public final Setting<Integer> scanRadius = this.sgTargeting
      .add(new IntSetting.Builder()
         .name("scan-radius")
         .description("Radius in chunks around the player to search (0 = current chunk, 1 = 3x3 chunks, 2 = 5x5 chunks).")
         .defaultValue(0)
         .min(0)
         .sliderMax(3)
         .onChanged(r -> this.forceScan())
         .build());

   // General Settings
   private final Setting<Boolean> chatNotify = this.sgGeneral
      .add(new BoolSetting.Builder().name("chat-notify").description("Sends a chat notification when entering a chunk with targets.").defaultValue(false).build());

   // Render Settings
   private final Setting<Boolean> renderChunk = this.sgRender
      .add(new BoolSetting.Builder().name("render-chunk-boundary").description("Renders bounding box of current chunk.").defaultValue(true).build());

   private final Setting<ShapeMode> chunkShape = this.sgRender
      .add(new EnumSetting.Builder<ShapeMode>().name("chunk-shape").description("How chunk boundaries are rendered.").defaultValue(ShapeMode.Lines).build());

   private final Setting<SettingColor> chunkSideColor = this.sgRender
      .add(new ColorSetting.Builder().name("chunk-side-color").description("Chunk boundary side color.").defaultValue(new SettingColor(0, 200, 255, 25)).build());

   private final Setting<SettingColor> chunkLineColor = this.sgRender
      .add(new ColorSetting.Builder().name("chunk-line-color").description("Chunk boundary line color.").defaultValue(new SettingColor(0, 200, 255, 200)).build());

   private final Setting<Boolean> renderOres = this.sgRender
      .add(new BoolSetting.Builder().name("render-ores").description("Renders 3D boxes around discovered ores in the chunk.").defaultValue(false).build());

   private final Setting<SettingColor> oreLineColor = this.sgRender
      .add(new ColorSetting.Builder().name("ore-line-color").description("Color of ore boxes.").defaultValue(new SettingColor(255, 215, 0, 180)).build());

   private final Setting<SettingColor> customLineColor = this.sgRender
      .add(new ColorSetting.Builder().name("custom-block-color").description("Color of custom targeted block boxes.").defaultValue(new SettingColor(0, 230, 255, 200)).build());

   private final Setting<Boolean> tracers = this.sgRender
      .add(new BoolSetting.Builder().name("tracers").description("Renders tracers to the nearest discovered ore vein.").defaultValue(false).build());

   private final Setting<SettingColor> tracerColor = this.sgRender
      .add(new ColorSetting.Builder().name("tracer-color").description("Color of tracers.").defaultValue(new SettingColor(255, 215, 0, 220)).build());

   private final Setting<SettingColor> customTracerColor = this.sgRender
      .add(new ColorSetting.Builder().name("custom-tracer-color").description("Color of tracers to custom targeted blocks.").defaultValue(new SettingColor(0, 230, 255, 220)).build());

   // Mining Supervisor & Baritone Settings
   private final Setting<Boolean> supervisor = this.sgMining
      .add(new BoolSetting.Builder().name("active-supervisor").description("Continuously supervises Baritone until all target ores in chunk are cleared.").defaultValue(true).build());

   private final Setting<Boolean> mineAllVariants = this.sgMining
      .add(new BoolSetting.Builder().name("auto-bundle-variants").description("Automatically targets both normal and deepslate ore variants together.").defaultValue(true).build());

   private final Setting<Boolean> currentChunkOnly = this.sgMining
      .add(new BoolSetting.Builder().name("current-chunk-only").description("Restricts mining strictly to ores within the current chunk boundaries.").defaultValue(true).build());

   private final Setting<Boolean> blacklistClosestOnFailure = this.sgMining
      .add(new BoolSetting.Builder().name("blacklist-on-failure").description("Baritone blacklists an ore if a path calculation fails.").defaultValue(false).build());

   private final Setting<Boolean> allowOnlyExposedOres = this.sgMining
      .add(new BoolSetting.Builder().name("only-exposed-ores").description("Only mine ores that are directly exposed to air.").defaultValue(false).build());

   private final Setting<Boolean> legitMine = this.sgMining
      .add(new BoolSetting.Builder().name("legit-mine").description("Mine ores legitimately by exploring rather than digging straight in.").defaultValue(false).build());

   private final Setting<Integer> maxOreLocations = this.sgMining
      .add(new IntSetting.Builder().name("max-ore-locations").description("Maximum ore locations to queue into Baritone memory.").defaultValue(2048).min(32).sliderRange(32, 4096).build());

   private final Setting<Integer> minY = this.sgMining
      .add(new IntSetting.Builder().name("min-y-level").description("Minimum Y-level to mine down to.").defaultValue(-64).min(-64).max(320).sliderRange(-64, 320).build());

   private final Setting<Integer> maxY = this.sgMining
      .add(new IntSetting.Builder().name("max-y-level").description("Maximum Y-level to mine up to.").defaultValue(320).min(-64).max(320).sliderRange(-64, 320).build());

   private final Setting<Boolean> collectDrops = this.sgMining
      .add(new BoolSetting.Builder().name("collect-dropped-items").description("Scans and collects dropped items while mining.").defaultValue(true).build());

   private ChunkPos lastChunkPos = null;
   private ChunkScanResult lastResult = null;
   private Block highlightedBlock = null;

   // Active Mining Supervisor State (Sequential Queue)
   private boolean isMiningChunk = false;
   private final List<Block> activeTargets = new ArrayList<>();
   private final List<List<Block>> sequentialTargetQueue = new ArrayList<>();
   private ChunkPos targetChunkPos = null;
   private int miningStuckTicks = 0;

   public ChunkScanner() {
      super(Categories.World, "chunk-scanner", "Auto-discovers all vanilla and modded ores in the current chunk.");
   }

   @Override
   public void onActivate() {
      this.forceScan();
   }

   @Override
   public void onDeactivate() {
      this.stopMining();
      this.highlightedBlock = null;
   }

   public boolean isMining() {
      return this.isMiningChunk;
   }

   public boolean getAutoBundleVariants() {
      return this.mineAllVariants.get();
   }

   public int getRemainingQueueSize() {
      return this.sequentialTargetQueue.size();
   }

   public void gotoBlock(BlockPos pos) {
      if (pos == null || BaritoneAPI.getProvider() == null) return;
      IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
      baritone.getPathingBehavior().cancelEverything();
      baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(pos));
      this.info("Pathfinding to [%d, %d, %d]...", pos.getX(), pos.getY(), pos.getZ());
   }

   public void startMining(List<Block> targets) {
      if (targets == null || targets.isEmpty() || this.mc.player == null) return;
      if (BaritoneAPI.getProvider() == null) {
         this.error("Baritone is not available.");
         return;
      }

      this.stopMining();
      this.activeTargets.clear();
      this.activeTargets.addAll(targets);
      this.sequentialTargetQueue.clear();
      this.isMiningChunk = this.supervisor.get();
      this.targetChunkPos = this.mc.player.chunkPosition();
      this.miningStuckTicks = 0;

      this.applyBaritoneSettings();

      IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
      baritone.getPathingBehavior().cancelEverything();

      // Guide Baritone to nearest ore coordinate in chunk first
      this.guideToNearestRemainingInChunk(baritone);

      baritone.getMineProcess().mine(0, this.activeTargets.toArray(new Block[0]));
      this.info("Started chunk mining on (highlight)%d(default) target block types.", this.activeTargets.size());
   }

   public void startSequentialMining(List<DiscoveredBlockEntry> entries) {
      if (entries == null || entries.isEmpty() || this.mc.player == null) return;
      if (BaritoneAPI.getProvider() == null) {
         this.error("Baritone is not available.");
         return;
      }

      this.stopMining();

      // Sort entries by proximity to player so we mine nearest first
      BlockPos pPos = this.mc.player.blockPosition();
      List<DiscoveredBlockEntry> sorted = new ArrayList<>(entries);
      sorted.sort(Comparator.comparingDouble(e -> e.getDistance(pPos)));

      this.sequentialTargetQueue.clear();
      List<Block> seenBlocks = new ArrayList<>();

      for (DiscoveredBlockEntry entry : sorted) {
         List<Block> family = this.getAutoBundleVariants()
            ? ChunkScannerEngine.getFamilyBlocks(entry, entries)
            : List.of(entry.block);

         boolean alreadyQueued = false;
         for (Block b : family) {
            if (seenBlocks.contains(b)) {
               alreadyQueued = true;
               break;
            }
         }

         if (!alreadyQueued) {
            seenBlocks.addAll(family);
            this.sequentialTargetQueue.add(family);
         }
      }

      if (!this.sequentialTargetQueue.isEmpty()) {
         this.targetChunkPos = this.mc.player.chunkPosition();
         this.isMiningChunk = true;
         this.info("Started chunk mining (%d ore veins queued).", this.sequentialTargetQueue.size());
         this.advanceSequentialMining();
      }
   }

   private void advanceSequentialMining() {
      if (this.sequentialTargetQueue.isEmpty()) {
         this.info("Finished mining all target ores in chunk [%d, %d]!",
            this.targetChunkPos != null ? this.targetChunkPos.x : 0,
            this.targetChunkPos != null ? this.targetChunkPos.z : 0);
         this.stopMining();
         return;
      }

      List<Block> nextTargets = this.sequentialTargetQueue.remove(0);
      this.activeTargets.clear();
      this.activeTargets.addAll(nextTargets);
      this.miningStuckTicks = 0;

      this.applyBaritoneSettings();

      IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
      baritone.getPathingBehavior().cancelEverything();

      // Guide to nearest block in current chunk
      this.guideToNearestRemainingInChunk(baritone);

      baritone.getMineProcess().mine(0, this.activeTargets.toArray(new Block[0]));
   }

   private void guideToNearestRemainingInChunk(IBaritone baritone) {
      if (this.mc.player == null) return;
      this.forceScan();
      BlockPos nearest = null;
      double minD = Double.MAX_VALUE;
      BlockPos playerPos = this.mc.player.blockPosition();

      if (this.lastResult != null) {
         for (DiscoveredBlockEntry e : this.lastResult.entries) {
            if (this.activeTargets.contains(e.block) && e.nearestPos != null) {
               double d = e.getDistanceSq(playerPos);
               if (d < minD) {
                  minD = d;
                  nearest = e.nearestPos;
               }
            }
         }
      }

      if (nearest != null) {
         baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(nearest));
      }
   }

   private void applyBaritoneSettings() {
      Settings s = BaritoneAPI.getSettings();
      s.blacklistClosestOnFailure.value = this.blacklistClosestOnFailure.get();
      s.allowOnlyExposedOres.value = this.allowOnlyExposedOres.get();
      s.legitMine.value = this.legitMine.get();
      s.mineMaxOreLocationsCount.value = this.maxOreLocations.get();
      s.minYLevelWhileMining.value = this.minY.get();
      s.maxYLevelWhileMining.value = this.maxY.get();
      s.mineScanDroppedItems.value = this.collectDrops.get();
   }

   public void stopMining() {
      if (this.isMiningChunk) {
         this.isMiningChunk = false;
         this.activeTargets.clear();
         this.sequentialTargetQueue.clear();
         this.targetChunkPos = null;
         this.miningStuckTicks = 0;
         if (BaritoneAPI.getProvider() != null) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
         }
         this.info("Stopped chunk mining.");
      }
   }

   public void gotoSurface() {
      if (this.isMiningChunk) {
         this.stopMining();
      }
      GotoSurface module = meteordevelopment.meteorclient.systems.modules.Modules.get().get(GotoSurface.class);
      if (module != null && !module.isActive()) {
         module.toggle();
      }
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.player == null || this.mc.level == null) return;
      ChunkPos currentPos = this.mc.player.chunkPosition();

      if (this.lastChunkPos == null || !this.lastChunkPos.equals(currentPos)) {
         this.lastChunkPos = currentPos;
         this.forceScan();

         if (this.chatNotify.get() && this.lastResult != null && this.lastResult.totalBlocks > 0) {
            StringBuilder sb = new StringBuilder();
            int shown = 0;
            for (DiscoveredBlockEntry entry : this.lastResult.entries) {
               if (shown > 0) sb.append(", ");
               sb.append(entry.displayName).append(" x").append(entry.count);
               shown++;
               if (shown >= 4) break;
            }
            String typeWord = this.lastResult.totalCustom > 0 ? (this.lastResult.totalOres > 0 ? "targets" : "custom blocks") : "ores";
            this.info("Chunk [%d, %d]: found (highlight)%d(default) %s (%s).",
               currentPos.x, currentPos.z, this.lastResult.totalBlocks, typeWord, sb.toString());
         }
      }

      // Active Mining Supervisor (Sequential & Chunk-Bounded)
      if (this.supervisor.get() && this.isMiningChunk && !this.activeTargets.isEmpty() && BaritoneAPI.getProvider() != null) {
         IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
         boolean isMining = baritone.getPathingControlManager().mostRecentInControl().orElse(null) instanceof IMineProcess;
         boolean isPathing = baritone.getPathingBehavior().isPathing();

         if (!isMining && !isPathing) {
            this.miningStuckTicks++;
            if (this.miningStuckTicks >= 10) { // 0.5s pause detected
               this.miningStuckTicks = 0;
               this.forceScan();

               List<BlockPos> remaining = new ArrayList<>();
               if (this.lastResult != null) {
                  for (DiscoveredBlockEntry entry : this.lastResult.entries) {
                     if (this.activeTargets.contains(entry.block)) {
                        remaining.addAll(entry.positions);
                     }
                  }
               }

               if (!remaining.isEmpty()) {
                  // Find nearest remaining ore block in this chunk
                  BlockPos playerPos = this.mc.player.blockPosition();
                  BlockPos nearest = null;
                  double minDist = Double.MAX_VALUE;
                  for (BlockPos pos : remaining) {
                     double d = pos.distSqr(playerPos);
                     if (d < minDist) {
                        minDist = d;
                        nearest = pos;
                     }
                  }

                  if (nearest != null) {
                     baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(nearest));
                     baritone.getMineProcess().mine(0, this.activeTargets.toArray(new Block[0]));
                  }
               } else {
                  // Current target ore in this chunk is completely mined!
                  if (!this.sequentialTargetQueue.isEmpty()) {
                     this.advanceSequentialMining();
                  } else {
                     this.info("Finished mining all target ores in chunk [%d, %d]!",
                        this.targetChunkPos != null ? this.targetChunkPos.x : currentPos.x,
                        this.targetChunkPos != null ? this.targetChunkPos.z : currentPos.z);
                     this.isMiningChunk = false;
                     this.activeTargets.clear();
                     this.targetChunkPos = null;
                  }
               }
            }
         } else {
            this.miningStuckTicks = 0;
         }
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (this.mc.player == null || this.mc.level == null) return;
      ChunkPos pos = this.mc.player.chunkPosition();

      // Render chunk boundaries
      if (this.renderChunk.get()) {
         double minX = pos.getMinBlockX();
         double minZ = pos.getMinBlockZ();
         double maxX = pos.getMaxBlockX() + 1;
         double maxZ = pos.getMaxBlockZ() + 1;
         double minY = this.mc.level.getMinBuildHeight();
         double maxY = this.mc.level.getMaxBuildHeight();

         event.renderer.box(minX, minY, minZ, maxX, maxY, maxZ,
            this.chunkSideColor.get(), this.chunkLineColor.get(), this.chunkShape.get(), 0);
      }

      // Render ore & custom target boxes & tracers
      if (this.lastResult != null && (this.renderOres.get() || this.tracers.get() || this.highlightedBlock != null)) {
         for (DiscoveredBlockEntry entry : this.lastResult.entries) {
            if (this.highlightedBlock != null && entry.block != this.highlightedBlock) {
               continue;
            }

            Color boxColor = entry.isCustomTarget ? this.customLineColor.get() : this.oreLineColor.get();
            Color tColor = entry.isCustomTarget ? this.customTracerColor.get() : this.tracerColor.get();

            if (this.renderOres.get() || this.highlightedBlock != null) {
               for (BlockPos bPos : entry.positions) {
                  event.renderer.box(bPos, boxColor, boxColor, ShapeMode.Lines, 0);
               }
            }

            if (this.tracers.get() && entry.nearestPos != null) {
               event.renderer.line(
                  this.mc.player.getX(), this.mc.player.getEyeY(), this.mc.player.getZ(),
                  entry.nearestPos.getX() + 0.5, entry.nearestPos.getY() + 0.5, entry.nearestPos.getZ() + 0.5,
                  tColor
               );
            }
         }
      }
   }

   public void forceScan() {
      if (this.mc.player == null || this.mc.level == null) return;
      ChunkPos currentPos = this.mc.player.chunkPosition();
      int radius = this.scanRadius.get();
      BlockPos playerPos = this.mc.player.blockPosition();

      if (radius <= 0) {
         LevelChunk chunk = this.mc.level.getChunkSource().getChunk(currentPos.x, currentPos.z, false);
         if (chunk != null) {
            this.lastResult = ChunkScannerEngine.scanChunk(chunk, playerPos, this.scanMode.get(), this.customBlocks.get());
         }
      } else {
         List<ChunkScanResult> results = new ArrayList<>();
         for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
               LevelChunk chunk = this.mc.level.getChunkSource().getChunk(currentPos.x + dx, currentPos.z + dz, false);
               if (chunk != null) {
                  ChunkScanResult r = ChunkScannerEngine.scanChunk(chunk, playerPos, this.scanMode.get(), this.customBlocks.get());
                  if (r != null) {
                     results.add(r);
                  }
               }
            }
         }
         this.lastResult = ChunkScannerEngine.mergeResults(currentPos, results, playerPos);
      }
   }

   public ChunkScanResult getLastResult() {
      if ((this.lastResult == null || this.lastResult.entries == null) && this.mc.player != null) {
         this.forceScan();
      }
      return this.lastResult;
   }

   public boolean isTargeted(Block block) {
      if (block == null) return false;
      if (this.customBlocks.get().contains(block)) return true;
      net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
      if (id == null) return false;
      for (Block b : this.customBlocks.get()) {
         if (id.equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b))) return true;
      }
      return false;
   }

   public void toggleTarget(Block block) {
      if (block == null) return;
      net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
      boolean removed = this.customBlocks.get().removeIf(b -> b == block || (id != null && id.equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b))));
      if (!removed) {
         this.customBlocks.get().add(block);
         this.info("Added (highlight)%s(default) to targets.", meteordevelopment.meteorclient.utils.misc.Names.get(block));
      } else {
         this.info("Removed (highlight)%s(default) from targets.", meteordevelopment.meteorclient.utils.misc.Names.get(block));
      }
      this.customBlocks.onChanged();
      this.forceScan();
   }

   public void setHighlighted(Block block) {
      this.highlightedBlock = (this.highlightedBlock == block) ? null : block;
   }

   @Override
   public WWidget getWidget(GuiTheme theme) {
      WVerticalList list = theme.verticalList();
      WButton openScreenBtn = list.add(theme.button("Open Chunk Inspector")).expandX().widget();
      openScreenBtn.action = () -> {
         this.mc.setScreen(new ChunkScannerScreen(theme, this));
      };

      WButton selectTargetsBtn = list.add(theme.button("Configure Target Blocks (" + this.customBlocks.get().size() + ")")).expandX().widget();
      selectTargetsBtn.action = () -> {
         this.mc.setScreen(new BlockListSettingScreen(theme, this.customBlocks));
      };

      if (this.isMiningChunk) {
         WButton stopBtn = list.add(theme.button("Stop Mining")).expandX().widget();
         stopBtn.action = this::stopMining;
      }

      return list;
   }
}
