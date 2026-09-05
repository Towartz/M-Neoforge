package meteordevelopment.meteorclient.gui.screens;

import baritone.api.BaritoneAPI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.screens.settings.BlockListSettingScreen;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.systems.modules.world.ChunkScanner;
import meteordevelopment.meteorclient.systems.modules.world.ChunkScanner.ScanMode;
import meteordevelopment.meteorclient.utils.world.ChunkScannerEngine;
import meteordevelopment.meteorclient.utils.world.ChunkScannerEngine.ChunkScanResult;
import meteordevelopment.meteorclient.utils.world.ChunkScannerEngine.DiscoveredBlockEntry;
import meteordevelopment.meteorclient.utils.world.SurfaceEscapeEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;

public class ChunkScannerScreen extends WindowScreen {
   private final ChunkScanner module;
   private WTable table;
   private WTextBox searchBox;
   private String filterText = "";
   private SortMode sortMode = SortMode.Distance;

   private enum SortMode {
      Distance("Distance (Nearest)"),
      Count("Count (Highest)"),
      Depth("Depth (Lowest Y)"),
      Name("Name (A-Z)");

      public final String title;
      SortMode(String title) { this.title = title; }
      public SortMode next() {
         SortMode[] vals = values();
         return vals[(this.ordinal() + 1) % vals.length];
      }
   }

   public ChunkScannerScreen(GuiTheme theme, ChunkScanner module) {
      super(theme, "Chunk Inspector");
      this.module = module;
   }

   @Override
   public void initWidgets() {
      this.module.forceScan();
      ChunkScanResult result = this.module.getLastResult();
      if (result == null) {
         this.add(this.theme.label("No chunk scanned yet. Walk into a chunk or enable the module.")).expandX();
         return;
      }

      if (this.module.scanMode.get() == ScanMode.Custom && this.module.customBlocks.get().isEmpty()) {
         WSection notice = this.add(this.theme.section("No Custom Target Blocks")).expandX().widget();
         WTable noticeTable = notice.add(this.theme.table()).expandX().widget();
         noticeTable.add(this.theme.label("Custom Only mode is enabled, but no blocks have been selected.")).expandCellX();
         WButton configBtn = noticeTable.add(this.theme.button("Configure Targets...")).widget();
         configBtn.action = () -> Minecraft.getInstance().setScreen(new BlockListSettingScreen(this.theme, this.module.customBlocks));
         this.add(this.theme.horizontalSeparator()).expandX();
      }

      BlockPos pPos = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.blockPosition() : null;
      ChunkPos cPos = result.chunkPos;
      int minX = cPos.getMinBlockX();
      int maxX = cPos.getMaxBlockX();
      int minZ = cPos.getMinBlockZ();
      int maxZ = cPos.getMaxBlockZ();

      // 1. Overview Dashboard Header
      WTable dashboard = this.add(this.theme.table()).expandX().widget();
      dashboard.add(this.theme.label(String.format("Chunk [%d, %d] (X: %d..%d | Z: %d..%d)", cPos.x, cPos.z, minX, maxX, minZ, maxZ))).expandCellX();
      dashboard.add(this.theme.label("Biome: " + result.biomeName)).right();
      dashboard.row();

      int uniqueCount = result.entries.size();
      int minChunkY = Integer.MAX_VALUE;
      int maxChunkY = Integer.MIN_VALUE;
      for (DiscoveredBlockEntry e : result.entries) {
         if (e.minY < minChunkY) minChunkY = e.minY;
         if (e.maxY > maxChunkY) maxChunkY = e.maxY;
      }
      String spanText = (minChunkY != Integer.MAX_VALUE) ? String.format("Depth: Y: %d .. %d", minChunkY, maxChunkY) : "Depth: N/A";
      int distToSurf = SurfaceEscapeEngine.getDistanceToSurface(62);
      String surfInfo = distToSurf > 0 ? String.format(" | Surface: %dm above", distToSurf) : " | Surface: Reached";

      String statsText;
      if (result.totalCustom > 0 && result.totalOres > 0) {
         statsText = String.format("Total: %d blocks (%d ores, %d custom | %d types) | %s | Player Y: %d%s",
            result.totalBlocks, result.totalOres, result.totalCustom, uniqueCount, spanText, pPos != null ? pPos.getY() : 0, surfInfo);
      } else if (result.totalCustom > 0) {
         statsText = String.format("Total: %d custom blocks (%d types) | %s | Player Y: %d%s",
            result.totalBlocks, uniqueCount, spanText, pPos != null ? pPos.getY() : 0, surfInfo);
      } else {
         statsText = String.format("Total Ores: %d (%d types) | %s | Player Y: %d%s",
            result.totalOres, uniqueCount, spanText, pPos != null ? pPos.getY() : 0, surfInfo);
      }
      dashboard.add(this.theme.label(statsText)).expandCellX();

      if (this.module.isMining()) {
         dashboard.add(this.theme.label("(highlight)Mining in progress...(default)")).right();
      }
      dashboard.row();

      this.add(this.theme.horizontalSeparator()).expandX();

      // 2. Nearest Radar Spotlight Card
      DiscoveredBlockEntry closest = result.getClosestEntry(pPos);
      if (closest != null) {
         String spotlightTitle = closest.isCustomTarget ? "Nearest Target Spotlight" : "Nearest Ore Radar Spotlight";
         WSection radarSection = this.add(this.theme.section(spotlightTitle)).expandX().widget();
         WTable radarTable = radarSection.add(this.theme.table()).expandX().widget();

         // Row 1: Item icon + Name + Mod tag
         String badge = closest.isCustomTarget ? "(highlight)[Target](default) " : "";
         String closestName = String.format("%s%s [%s]", badge, closest.displayName, closest.modName);
         radarTable.add(this.theme.itemWithLabel(closest.icon, closestName)).expandCellX();

         // Distance Badge
         radarTable.add(this.theme.label(String.format("(highlight)%s away(default)", closest.getDistanceString(pPos)))).right();
         radarTable.row();

         // Row 2: Relative bearing + 3D delta offsets + exact position
         String bearing = closest.getBearingString(pPos);
         String delta = closest.getDeltaString(pPos);
         BlockPos bPos = closest.nearestPos != null ? closest.nearestPos : pPos;
         String coords = String.format("Coords: [%d, %d, %d] | Bearing: %s | %s",
            bPos.getX(), bPos.getY(), bPos.getZ(), bearing, delta);
         radarTable.add(this.theme.label(coords)).expandCellX();

         // Radar Actions
         WTable radarActions = radarTable.add(this.theme.table()).right().widget();
         if (BaritoneAPI.getProvider() != null) {
            List<Block> targets = this.module.getAutoBundleVariants()
               ? ChunkScannerEngine.getFamilyBlocks(closest, result.entries)
               : List.of(closest.block);
            String btnText = targets.size() > 1 ? "Mine (All)" : "Mine";
            WButton mineClosestBtn = radarActions.add(this.theme.button(btnText)).widget();
            mineClosestBtn.action = () -> {
               this.module.startMining(targets);
               this.onClose();
            };

            WButton gotoClosestBtn = radarActions.add(this.theme.button("Goto")).widget();
            gotoClosestBtn.action = () -> {
               this.module.gotoBlock(closest.nearestPos);
               this.onClose();
            };
         }

         WButton highlightClosestBtn = radarActions.add(this.theme.button("Highlight")).widget();
         highlightClosestBtn.action = () -> {
            this.module.setHighlighted(closest.block);
            this.module.info("Highlighting (highlight)%s(default) in chunk.", closest.displayName);
         };

         radarTable.row();
         this.add(this.theme.horizontalSeparator()).expandX();
      }

      // 3. Search & Sort Toolbar + Actions
      WTable toolbar = this.add(this.theme.table()).expandX().widget();

      this.searchBox = toolbar.add(this.theme.textBox(this.filterText, "Search block or @mod...")).minWidth(180.0).expandX().widget();
      this.searchBox.action = () -> {
         this.filterText = this.searchBox.get().trim();
         this.table.clear();
         this.fillTable(result);
      };

      WButton modeToggleBtn = toolbar.add(this.theme.button("Mode: " + this.module.scanMode.get().title)).widget();
      modeToggleBtn.action = () -> {
         this.module.scanMode.set(this.module.scanMode.get().next());
         modeToggleBtn.set("Mode: " + this.module.scanMode.get().title);
         this.module.forceScan();
         this.clear();
         this.initWidgets();
      };

      String radTitle = switch (this.module.scanRadius.get()) {
         case 1 -> "Radius: 3x3";
         case 2 -> "Radius: 5x5";
         case 3 -> "Radius: 7x7";
         default -> "Radius: 1x1";
      };
      WButton radiusToggleBtn = toolbar.add(this.theme.button(radTitle)).widget();
      radiusToggleBtn.action = () -> {
         int next = (this.module.scanRadius.get() + 1) % 4;
         this.module.scanRadius.set(next);
         this.module.forceScan();
         this.clear();
         this.initWidgets();
      };

      WButton targetsBtn = toolbar.add(this.theme.button("Targets (" + this.module.customBlocks.get().size() + ")")).widget();
      targetsBtn.action = () -> {
         Minecraft.getInstance().setScreen(new BlockListSettingScreen(this.theme, this.module.customBlocks));
      };

      WButton sortToggleBtn = toolbar.add(this.theme.button("Sort: " + this.sortMode.title)).widget();
      sortToggleBtn.action = () -> {
         this.sortMode = this.sortMode.next();
         sortToggleBtn.set("Sort: " + this.sortMode.title);
         this.table.clear();
         this.fillTable(result);
      };

      if (this.module.isMining()) {
         WButton stopBtn = toolbar.add(this.theme.button("Stop Mining")).widget();
         stopBtn.action = () -> {
            this.module.stopMining();
            this.clear();
            this.initWidgets();
         };
      } else if (BaritoneAPI.getProvider() != null) {
         WButton mineAllBtn = toolbar.add(this.theme.button("Mine All")).widget();
         mineAllBtn.action = () -> {
            this.module.startSequentialMining(result.entries);
            this.onClose();
         };

         String surfBtnText = distToSurf > 0 ? String.format("Goto Surface (%dm)", distToSurf) : "Goto Surface";
         WButton surfBtn = toolbar.add(this.theme.button(surfBtnText)).widget();
         surfBtn.action = () -> {
            this.module.gotoSurface();
            this.onClose();
         };
      }

      WButton rescanBtn = toolbar.add(this.theme.button("Rescan")).widget();
      rescanBtn.action = () -> {
         this.module.forceScan();
         this.clear();
         this.initWidgets();
      };

      toolbar.row();
      this.add(this.theme.horizontalSeparator()).expandX();

      // 4. Detailed Data Table
      this.table = this.add(this.theme.table()).expandX().widget();
      this.fillTable(result);
   }

   private void fillTable(ChunkScanResult result) {
      this.table.add(this.theme.label("Target Block / Mod")).expandCellX();
      this.table.add(this.theme.label("Count")).right();
      this.table.add(this.theme.label("Nearest (Distance & Bearing)")).right();
      this.table.add(this.theme.label("Depth")).right();
      this.table.add(this.theme.label("Actions")).right();
      this.table.row();

      BlockPos pPos = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.blockPosition() : null;

      // Filter & Sort entries
      List<DiscoveredBlockEntry> list = new ArrayList<>(result.entries);
      String query = this.filterText.toLowerCase(Locale.ROOT);

      if (!query.isEmpty()) {
         list.removeIf(entry -> {
            boolean matchName = entry.displayName.toLowerCase(Locale.ROOT).contains(query);
            boolean matchMod = entry.modName.toLowerCase(Locale.ROOT).contains(query);
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(entry.block);
            boolean matchId = id != null && (id.getPath().toLowerCase(Locale.ROOT).contains(query) || id.getNamespace().toLowerCase(Locale.ROOT).contains(query));
            return !matchName && !matchMod && !matchId;
         });
      }

      switch (this.sortMode) {
         case Distance -> list.sort(Comparator.comparingDouble(e -> e.getDistanceSq(pPos)));
         case Count -> list.sort(Comparator.comparingInt((DiscoveredBlockEntry e) -> e.count).reversed());
         case Depth -> list.sort(Comparator.comparingInt((DiscoveredBlockEntry e) -> e.minY));
         case Name -> list.sort(Comparator.comparing(e -> e.displayName.toLowerCase(Locale.ROOT)));
      }

      if (list.isEmpty()) {
         String msg = this.filterText.isEmpty()
            ? "No targeted blocks found in scanned chunk(s). Click '+Radius' or configure 'Targets'."
            : "No blocks matching search filter.";
         this.table.add(this.theme.label(msg)).expandCellX();
         return;
      }

      for (DiscoveredBlockEntry entry : list) {
         String typeBadge = entry.isCustomTarget ? "(highlight)[Target](default) " : "";
         String nameWithMod = String.format("%s%s [%s]", typeBadge, entry.displayName, entry.modName);
         WWidget itemLabel = this.theme.itemWithLabel(entry.icon, nameWithMod);
         this.table.add(itemLabel).expandCellX();

         // Count + Percentage of chunk targets
         int totalCount = result.totalBlocks > 0 ? result.totalBlocks : result.totalOres;
         double pct = totalCount > 0 ? (entry.count * 100.0 / totalCount) : 0.0;
         String countStr = String.format("x%d (%.0f%%)", entry.count, pct);
         this.table.add(this.theme.label(countStr)).right();

         // Distance & Bearing info
         String distStr = entry.getDistanceString(pPos);
         String bearingStr = entry.getBearingString(pPos);
         String nearInfo = String.format("%s (%s)", distStr, bearingStr);
         this.table.add(this.theme.label(nearInfo)).right();

         // Depth
         String yRange = String.format("Y: %d..%d", entry.minY, entry.maxY);
         this.table.add(this.theme.label(yRange)).right();

         // Action buttons
         WTable rowActions = this.theme.table();
         if (BaritoneAPI.getProvider() != null) {
            List<Block> targets = this.module.getAutoBundleVariants()
               ? ChunkScannerEngine.getFamilyBlocks(entry, result.entries)
               : List.of(entry.block);
            String btnText = targets.size() > 1 ? "Mine (All)" : "Mine";
            WButton mineBtn = rowActions.add(this.theme.button(btnText)).widget();
            mineBtn.action = () -> {
               this.module.startMining(targets);
               this.onClose();
            };

            if (entry.nearestPos != null) {
               WButton gotoBtn = rowActions.add(this.theme.button("Goto")).widget();
               gotoBtn.action = () -> {
                  this.module.gotoBlock(entry.nearestPos);
                  this.onClose();
               };
            }
         }

         WButton highlightBtn = rowActions.add(this.theme.button("Highlight")).widget();
         highlightBtn.action = () -> {
            this.module.setHighlighted(entry.block);
            this.module.info("Highlighting (highlight)%s(default) in chunk.", entry.displayName);
         };

         boolean isTargeted = this.module.isTargeted(entry.block);
         WButton targetToggleBtn = rowActions.add(this.theme.button(isTargeted ? "-Target" : "+Target")).widget();
         targetToggleBtn.action = () -> {
            this.module.toggleTarget(entry.block);
            this.clear();
            this.initWidgets();
         };

         this.table.add(rowActions).right();
         this.table.row();
      }
   }
}
