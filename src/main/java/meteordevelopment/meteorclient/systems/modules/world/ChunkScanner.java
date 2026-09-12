package meteordevelopment.meteorclient.systems.modules.world;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.process.IMineProcess;
import baritone.api.utils.BlockOptionalMeta;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
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
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.ChunkScannerEngine;
import meteordevelopment.meteorclient.utils.world.ChunkScannerEngine.ChunkScanResult;
import meteordevelopment.meteorclient.utils.world.ChunkScannerEngine.DiscoveredBlockEntry;
import meteordevelopment.meteorclient.utils.world.OreDiscovery;
import meteordevelopment.meteorclient.utils.world.OreDropHelper;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

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
   private final SettingGroup sgSafety = this.settings.createGroup("Safety & Warden Guard");

   // Safety & Warden Guard Settings
   public final Setting<Boolean> avoidSculkThreats = this.sgSafety
      .add(new BoolSetting.Builder()
         .name("avoid-sculk-threats")
         .description("Avoids mining ores or pathing near Sculk Sensors, Sculk Shriekers, and Warden spawn zones.")
         .defaultValue(true)
         .onChanged(v -> this.forceScan())
         .build());

   public final Setting<Integer> sculkAvoidRadius = this.sgSafety
      .add(new IntSetting.Builder()
         .name("sculk-avoid-radius")
         .description("Safety buffer distance in blocks around Sculk Sensors and Shriekers to avoid.")
         .defaultValue(10)
         .min(4)
         .sliderRange(4, 24)
         .onChanged(v -> this.forceScan())
         .build());

   public final Setting<Boolean> autoSneakNearSculk = this.sgSafety
      .add(new BoolSetting.Builder()
         .name("auto-sneak-near-sculk")
         .description("Silently crouch/sneak when moving near Sculk Sensors to suppress footstep vibrations.")
         .defaultValue(true)
         .build());

   public final Setting<Boolean> wardenEmergencyStop = this.sgSafety
      .add(new BoolSetting.Builder()
         .name("warden-emergency-stop")
         .description("Instantly stops mining and pathing if a Warden is nearby or Darkness effect triggers.")
         .defaultValue(true)
         .build());

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

   private final Setting<Boolean> renderSculkDanger = this.sgRender
      .add(new BoolSetting.Builder().name("render-sculk-danger").description("Renders 3D danger warning zones around detected Sculk Sensors and Shriekers.").defaultValue(true).build());

   private final Setting<SettingColor> sculkDangerColor = this.sgRender
      .add(new ColorSetting.Builder().name("sculk-danger-color").description("Color of Sculk danger warning zones.").defaultValue(new SettingColor(255, 30, 30, 70)).build());

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

   private final Setting<Integer> dropCollectRadius = this.sgMining
      .add(new IntSetting.Builder()
         .name("drop-collect-radius")
         .description("Radius in blocks to scan and collect nearby dropped ore items.")
         .defaultValue(16)
         .min(4)
         .sliderRange(4, 32)
         .build());

   private final Setting<Integer> dropCollectTimeout = this.sgMining
      .add(new IntSetting.Builder()
         .name("drop-collect-timeout")
         .description("Maximum seconds to attempt reaching an unreachable drop before skipping it.")
         .defaultValue(3)
         .min(1)
         .sliderRange(1, 10)
         .build());

   private ChunkPos lastChunkPos = null;
   private ChunkScanResult lastResult = null;
   private Block highlightedBlock = null;

   // Active Mining Supervisor State (Sequential Queue & Drop Collection)
   private boolean isMiningChunk = false;
   private boolean isCollectingDrops = false;
   private final Set<Integer> blacklistedDropEntityIds = new HashSet<>();
   private int currentDropTicks = 0;
   private Integer currentTargetDropId = null;
   private final Map<BlockPos, Long> recentBrokenOrePositions = new HashMap<>();
   private final List<Block> activeTargets = new ArrayList<>();
   private final List<List<Block>> sequentialTargetQueue = new ArrayList<>();
   private ChunkPos targetChunkPos = null;
   private int miningStuckTicks = 0;
   private int miningRetryCount = 0;

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
      this.lastResult = null;
      this.lastChunkPos = null;
      this.recentBrokenOrePositions.clear();
      this.blacklistedDropEntityIds.clear();
   }

   @EventHandler
   private void onGameLeft(meteordevelopment.meteorclient.events.game.GameLeftEvent event) {
      this.onDeactivate();
   }

   public boolean isMining() {
      return this.isMiningChunk;
   }

   public boolean isCollectingDrops() {
      return this.isCollectingDrops;
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
      this.miningRetryCount = 0;

      this.applyBaritoneSettings();

      IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
      baritone.getPathingBehavior().cancelEverything();

      // Repack Baritone world scanner cache so all loaded blocks are indexed
      BaritoneAPI.getProvider().getWorldScanner().repack(baritone.getPlayerContext());

      List<BlockOptionalMeta> metas = new ArrayList<>(this.activeTargets.size());
      for (Block b : this.activeTargets) {
         metas.add(new BlockOptionalMeta(b));
      }

      baritone.getMineProcess().mine(0, metas.toArray(new BlockOptionalMeta[0]));
      this.info("Started chunk mining on (highlight)%d(default) target block type(s).", this.activeTargets.size());
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
      this.miningRetryCount = 0;
      this.isCollectingDrops = false;
      this.blacklistedDropEntityIds.clear();
      this.currentTargetDropId = null;
      this.currentDropTicks = 0;
      this.recentBrokenOrePositions.clear();

      this.applyBaritoneSettings();

      IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
      baritone.getPathingBehavior().cancelEverything();

      // Repack Baritone world scanner cache
      BaritoneAPI.getProvider().getWorldScanner().repack(baritone.getPlayerContext());

      List<BlockOptionalMeta> metas = new ArrayList<>(this.activeTargets.size());
      for (Block b : this.activeTargets) {
         metas.add(new BlockOptionalMeta(b));
      }

      List<BlockPos> initialPositions = new ArrayList<>();
      if (this.lastResult != null) {
         for (DiscoveredBlockEntry entry : this.lastResult.entries) {
            if (this.activeTargets.contains(entry.block)) {
               initialPositions.addAll(entry.positions);
            }
         }
      }

      baritone.getMineProcess().mine(0, metas.toArray(new BlockOptionalMeta[0]), initialPositions);
      this.info("Mining next vein: (highlight)%s(default) (%d remaining in queue).",
         nextTargets.get(0).getName().getString(), this.sequentialTargetQueue.size());
   }

   private void applyBaritoneSettings() {
      Settings s = BaritoneAPI.getSettings();
      s.allowBreak.value = true;
      s.allowPlace.value = true;
      s.allowSprint.value = true;
      s.blacklistClosestOnFailure.value = this.blacklistClosestOnFailure.get();
      s.allowOnlyExposedOres.value = this.allowOnlyExposedOres.get();
      s.legitMine.value = this.legitMine.get();
      s.mineMaxOreLocationsCount.value = this.maxOreLocations.get();
      s.minYLevelWhileMining.value = this.minY.get();
      s.maxYLevelWhileMining.value = this.maxY.get();
      s.mineScanDroppedItems.value = this.collectDrops.get();
      s.mineDropLoiterDurationMSThanksLouca.value = 1000L;
      s.exploreForBlocks.value = false;
      s.mineOnlyLoadedChunks.value = true;
      s.mineMaxChunkRadius.value = 8;
      s.extendCacheOnThreshold.value = true;

      if (this.avoidSculkThreats.get()) {
         s.avoidance.value = true;
         addIfMissing(s.blocksToAvoid.value, net.minecraft.world.level.block.Blocks.SCULK_SENSOR);
         addIfMissing(s.blocksToAvoid.value, net.minecraft.world.level.block.Blocks.CALIBRATED_SCULK_SENSOR);
         addIfMissing(s.blocksToAvoid.value, net.minecraft.world.level.block.Blocks.SCULK_SHRIEKER);

         addIfMissing(s.blocksToDisallowBreaking.value, net.minecraft.world.level.block.Blocks.SCULK_SENSOR);
         addIfMissing(s.blocksToDisallowBreaking.value, net.minecraft.world.level.block.Blocks.CALIBRATED_SCULK_SENSOR);
         addIfMissing(s.blocksToDisallowBreaking.value, net.minecraft.world.level.block.Blocks.SCULK_SHRIEKER);
      }
   }

   private static <T> void addIfMissing(List<T> list, T item) {
      if (list != null && !list.contains(item)) {
         list.add(item);
      }
   }

   public void stopMining() {
      if (this.isMiningChunk) {
         this.isMiningChunk = false;
         this.isCollectingDrops = false;
         this.blacklistedDropEntityIds.clear();
         this.currentTargetDropId = null;
         this.currentDropTicks = 0;
         this.recentBrokenOrePositions.clear();
         this.activeTargets.clear();
         this.sequentialTargetQueue.clear();
         this.targetChunkPos = null;
         this.miningStuckTicks = 0;
         this.miningRetryCount = 0;
         if (BaritoneAPI.getProvider() != null) {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone != null) {
               baritone.getMineProcess().cancel();
               baritone.getPathingBehavior().cancelEverything();
            }
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

      // Emergency stop if Warden entity is detected nearby or Darkness effect triggers
      if (this.wardenEmergencyStop.get() && this.isMiningChunk) {
         boolean wardenNearby = !this.mc.level.getEntitiesOfClass(Warden.class, this.mc.player.getBoundingBox().inflate(36.0)).isEmpty();
         boolean hasDarkness = this.mc.player.hasEffect(MobEffects.DARKNESS);
         if (wardenNearby || hasDarkness) {
            this.warning("Warden threat detected nearby! Aborting mining immediately for safety!");
            this.stopMining();
            return;
         }
      }

      // Auto-sneak when near sculk threat positions to suppress footstep vibrations
      if (this.avoidSculkThreats.get() && this.autoSneakNearSculk.get() && this.lastResult != null && !this.lastResult.sculkThreatPositions.isEmpty()) {
         BlockPos playerPos = this.mc.player.blockPosition();
         double radius = (double)this.sculkAvoidRadius.get();
         double rSq = radius * radius;
         boolean inThreatRange = false;
         for (BlockPos threat : this.lastResult.sculkThreatPositions) {
            if (playerPos.distSqr(threat) <= rSq) {
               inThreatRange = true;
               break;
            }
         }
         if (inThreatRange) {
            this.mc.options.keyShift.setDown(true);
         }
      }

      long now = System.currentTimeMillis();
      this.recentBrokenOrePositions.entrySet().removeIf(e -> now - e.getValue() > 10000L);

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
         IMineProcess mineProcess = baritone.getMineProcess();

         // 1. Check remaining targets in the targeted chunk
         int remainingInChunk = 0;
         if (this.lastResult != null) {
            for (DiscoveredBlockEntry entry : this.lastResult.entries) {
               if (this.activeTargets.contains(entry.block)) {
                  remainingInChunk += entry.count;
               }
            }
         }

         if (remainingInChunk == 0) {
            // Check if there are nearby target ore drops to collect before advancing or finishing
            if (this.collectDrops.get()) {
               Vec3 playerPos = this.mc.player.position();
               double radius = (double)this.dropCollectRadius.get();
               List<ItemEntity> drops = new ArrayList<>(OreDropHelper.findNearbyTargetDrops(this.mc.level, playerPos, radius, this.activeTargets));

               // Also check recent broken ore positions
               for (BlockPos bPos : this.recentBrokenOrePositions.keySet()) {
                  Vec3 breakPos = Vec3.atCenterOf(bPos);
                  for (ItemEntity e : OreDropHelper.findNearbyTargetDrops(this.mc.level, breakPos, 6.0, this.activeTargets)) {
                     if (!drops.contains(e)) {
                        drops.add(e);
                     }
                  }
               }

               // Filter out blacklisted (unreachable) drops and dead entities
               drops.removeIf(e -> !e.isAlive() || this.blacklistedDropEntityIds.contains(e.getId()));

               if (!drops.isEmpty()) {
                  if (!this.isCollectingDrops) {
                     this.isCollectingDrops = true;
                     this.info("Collecting %d nearby dropped ore item(s)...", drops.size());
                     mineProcess.cancel();
                     baritone.getPathingBehavior().cancelEverything();
                  }

                  // Pick nearest drop to player
                  drops.sort(Comparator.comparingDouble(e -> e.distanceToSqr(playerPos)));
                  ItemEntity targetDrop = drops.get(0);

                  if (this.currentTargetDropId == null || this.currentTargetDropId != targetDrop.getId()) {
                     this.currentTargetDropId = targetDrop.getId();
                     this.currentDropTicks = 0;
                     baritone.getPathingBehavior().cancelEverything();
                     baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(targetDrop.blockPosition()));
                  } else {
                     this.currentDropTicks++;
                     int maxTicks = this.dropCollectTimeout.get() * 20;
                     if (this.currentDropTicks >= maxTicks) {
                        this.blacklistedDropEntityIds.add(targetDrop.getId());
                        this.currentTargetDropId = null;
                        this.currentDropTicks = 0;
                        baritone.getPathingBehavior().cancelEverything();
                     } else if (!baritone.getPathingBehavior().isPathing()) {
                        baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(targetDrop.blockPosition()));
                     }
                  }
                  return;
               }
            }

            // All target blocks and all nearby target drops have been collected!
            this.isCollectingDrops = false;
            this.currentTargetDropId = null;
            this.currentDropTicks = 0;
            this.blacklistedDropEntityIds.clear();
            this.recentBrokenOrePositions.clear();

            mineProcess.cancel();
            baritone.getPathingBehavior().cancelEverything();
            if (!this.sequentialTargetQueue.isEmpty()) {
               this.advanceSequentialMining();
            } else {
               this.info("Finished mining all target blocks in chunk [%d, %d]!",
                  this.targetChunkPos != null ? this.targetChunkPos.x : currentPos.x,
                  this.targetChunkPos != null ? this.targetChunkPos.z : currentPos.z);
               this.stopMining();
            }
         } else {
            this.isCollectingDrops = false;
            this.currentTargetDropId = null;
            this.currentDropTicks = 0;

            // Targets still exist in chunk: ensure mine process is active
            if (!mineProcess.isActive()) {
               this.miningStuckTicks++;
               if (this.miningStuckTicks >= 25) { // 1.25s grace period before re-triggering
                  this.miningStuckTicks = 0;
                  this.miningRetryCount++;
                  if (this.miningRetryCount <= 3) {
                     // Refresh world cache and re-trigger mine
                     BaritoneAPI.getProvider().getWorldScanner().repack(baritone.getPlayerContext());
                     List<BlockOptionalMeta> metas = new ArrayList<>(this.activeTargets.size());
                     for (Block b : this.activeTargets) {
                        metas.add(new BlockOptionalMeta(b));
                     }
                     List<BlockPos> initialPositions = new ArrayList<>();
                     if (this.lastResult != null) {
                        for (DiscoveredBlockEntry entry : this.lastResult.entries) {
                           if (this.activeTargets.contains(entry.block)) {
                              initialPositions.addAll(entry.positions);
                           }
                        }
                     }
                     mineProcess.mine(0, metas.toArray(new BlockOptionalMeta[0]), initialPositions);
                  } else {
                     // Skip to next if unreachable after 3 attempts
                     this.warning("Could not reach remaining target blocks in chunk. Skipping...");
                     if (!this.sequentialTargetQueue.isEmpty()) {
                        this.advanceSequentialMining();
                     } else {
                        this.stopMining();
                     }
                  }
               }
            } else {
               this.miningStuckTicks = 0;
            }
         }
      }
   }

   @EventHandler
   private void onBlockUpdate(BlockUpdateEvent event) {
      if (this.mc.player == null || this.mc.level == null || this.lastResult == null) return;
      int chunkX = event.pos.getX() >> 4;
      int chunkZ = event.pos.getZ() >> 4;
      ChunkPos playerChunk = this.mc.player.chunkPosition();
      int radius = this.scanRadius.get();

      if (Math.abs(chunkX - playerChunk.x) <= radius && Math.abs(chunkZ - playerChunk.z) <= radius) {
         Block oldBlock = event.oldState.getBlock();
         Block newBlock = event.newState.getBlock();
         boolean relevant = this.isTargeted(oldBlock) || this.isTargeted(newBlock)
            || OreDiscovery.isOre(oldBlock) || OreDiscovery.isOre(newBlock);

         if (relevant) {
            this.forceScan();

            if (this.supervisor.get() && this.isMiningChunk && !this.activeTargets.isEmpty()) {
               if (this.activeTargets.contains(oldBlock) && event.newState.isAir()) {
                  this.miningStuckTicks = 0;
                  this.recentBrokenOrePositions.put(event.pos.immutable(), System.currentTimeMillis());

                  if (this.mc.level != null) {
                     net.minecraft.world.phys.AABB searchBox = new net.minecraft.world.phys.AABB(event.pos).inflate(2.0);
                     for (ItemEntity ie : this.mc.level.getEntitiesOfClass(ItemEntity.class, searchBox)) {
                        if (ie != null && ie.isAlive() && !ie.getItem().isEmpty()) {
                           OreDropHelper.registerLearnedDrop(oldBlock, ie.getItem().getItem());
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (this.mc.player == null || this.mc.level == null) return;
      ChunkPos pos = this.mc.player.chunkPosition();

      // Render chunk boundaries
      if (this.renderChunk.get()) {
         int radius = this.scanRadius.get();
         double minY = this.mc.level.getMinBuildHeight();
         double maxY = this.mc.level.getMaxBuildHeight();

         if (radius <= 0) {
            double minX = pos.getMinBlockX();
            double minZ = pos.getMinBlockZ();
            double maxX = pos.getMaxBlockX() + 1;
            double maxZ = pos.getMaxBlockZ() + 1;

            event.renderer.box(minX, minY, minZ, maxX, maxY, maxZ,
               this.chunkSideColor.get(), this.chunkLineColor.get(), this.chunkShape.get(), 0);
         } else {
            // Render full scanned radius outer bounds
            double areaMinX = (pos.x - radius) << 4;
            double areaMinZ = (pos.z - radius) << 4;
            double areaMaxX = ((pos.x + radius) << 4) + 16;
            double areaMaxZ = ((pos.z + radius) << 4) + 16;

            event.renderer.box(areaMinX, minY, areaMinZ, areaMaxX, maxY, areaMaxZ,
               this.chunkSideColor.get(), this.chunkLineColor.get(), this.chunkShape.get(), 0);

            // Render subtle inner box for player's current chunk
            double curMinX = pos.getMinBlockX();
            double curMinZ = pos.getMinBlockZ();
            double curMaxX = pos.getMaxBlockX() + 1;
            double curMaxZ = pos.getMaxBlockZ() + 1;
            Color innerLine = new Color(this.chunkLineColor.get().r, this.chunkLineColor.get().g, this.chunkLineColor.get().b, 90);
            event.renderer.box(curMinX, minY, curMinZ, curMaxX, maxY, curMaxZ,
               null, innerLine, ShapeMode.Lines, 0);
         }
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
                  if (this.mc.level.getBlockState(bPos).isAir()) continue;
                  event.renderer.box(bPos, boxColor, boxColor, ShapeMode.Lines, 0);
               }
            }

            if (this.tracers.get() && entry.nearestPos != null) {
               if (!this.mc.level.getBlockState(entry.nearestPos).isAir()) {
                  event.renderer.line(
                     RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                     entry.nearestPos.getX() + 0.5, entry.nearestPos.getY() + 0.5, entry.nearestPos.getZ() + 0.5,
                     tColor
                  );
               }
            }
         }
      }

      // Render Sculk Danger warning zones
      if (this.renderSculkDanger.get() && this.lastResult != null && !this.lastResult.sculkThreatPositions.isEmpty()) {
         double r = (double)this.sculkAvoidRadius.get();
         SettingColor dColor = this.sculkDangerColor.get();
         Color dSide = new Color(dColor.r, dColor.g, dColor.b, 20);
         for (BlockPos threatPos : this.lastResult.sculkThreatPositions) {
            event.renderer.box(
               threatPos.getX() - r, threatPos.getY() - r, threatPos.getZ() - r,
               threatPos.getX() + r + 1, threatPos.getY() + r + 1, threatPos.getZ() + r + 1,
               dSide, dColor, ShapeMode.Both, 0
            );
         }
      }
   }

   public void forceScan() {
      if (this.mc.player == null || this.mc.level == null) return;
      ChunkPos currentPos = this.mc.player.chunkPosition();
      int radius = this.scanRadius.get();
      BlockPos playerPos = this.mc.player.blockPosition();
      boolean avoidSculk = this.avoidSculkThreats.get();
      int sculkRadius = this.sculkAvoidRadius.get();

      if (radius <= 0) {
         LevelChunk chunk = this.mc.level.getChunkSource().getChunk(currentPos.x, currentPos.z, false);
         if (chunk != null) {
            this.lastResult = ChunkScannerEngine.scanChunk(chunk, playerPos, this.scanMode.get(), this.customBlocks.get(), avoidSculk, sculkRadius);
         }
      } else {
         List<ChunkScanResult> results = new ArrayList<>();
         for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
               LevelChunk chunk = this.mc.level.getChunkSource().getChunk(currentPos.x + dx, currentPos.z + dz, false);
               if (chunk != null) {
                  ChunkScanResult r = ChunkScannerEngine.scanChunk(chunk, playerPos, this.scanMode.get(), this.customBlocks.get(), avoidSculk, sculkRadius);
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
   public WWidget getTopWidget(GuiTheme theme) {
      WVerticalList list = theme.verticalList();
      WButton openScreenBtn = list.add(theme.button("Open Chunk Scanner")).expandX().widget();
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

   @Override
   public WWidget getWidget(GuiTheme theme) {
      return null;
   }
}
