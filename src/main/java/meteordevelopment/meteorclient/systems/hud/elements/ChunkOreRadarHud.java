package meteordevelopment.meteorclient.systems.hud.elements;

import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.Alignment;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.ChunkScanner;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.ChunkScannerEngine.ChunkScanResult;
import meteordevelopment.meteorclient.utils.world.ChunkScannerEngine.DiscoveredBlockEntry;

public class ChunkOreRadarHud extends HudElement {
   public static final HudElementInfo<ChunkOreRadarHud> INFO = new HudElementInfo<>(
      Hud.GROUP, "chunk-ore-radar", "Displays all discovered ores in your current chunk.", ChunkOreRadarHud::new
   );

   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgScale = this.settings.createGroup("Scale");
   private final SettingGroup sgBackground = this.settings.createGroup("Background");

   private final Setting<Integer> limit = this.sgGeneral
      .add(new IntSetting.Builder().name("limit").description("Max ores to show.").defaultValue(8).min(1).sliderRange(1, 20).build());

   private final Setting<Boolean> showIcons = this.sgGeneral
      .add(new BoolSetting.Builder().name("show-icons").description("Renders 3D item sprite next to each ore.").defaultValue(true).build());

   private final Setting<Boolean> showMod = this.sgGeneral
      .add(new BoolSetting.Builder().name("show-mod-name").description("Shows the mod badge [Create, TFMG, etc.].").defaultValue(true).build());

   private final Setting<Boolean> showDepth = this.sgGeneral
      .add(new BoolSetting.Builder().name("show-depth").description("Shows Y-level depth range.").defaultValue(true).build());

   private final Setting<Boolean> shadow = this.sgGeneral
      .add(new BoolSetting.Builder().name("shadow").description("Renders shadow behind text.").defaultValue(true).build());

   private final Setting<SettingColor> primaryColor = this.sgGeneral
      .add(new ColorSetting.Builder().name("primary-color").description("Primary text color.").defaultValue(new SettingColor(255, 255, 255)).build());

   private final Setting<SettingColor> secondaryColor = this.sgGeneral
      .add(new ColorSetting.Builder().name("secondary-color").description("Secondary text color.").defaultValue(new SettingColor(180, 180, 180)).build());

   private final Setting<SettingColor> accentColor = this.sgGeneral
      .add(new ColorSetting.Builder().name("accent-color").description("Count and badge color.").defaultValue(new SettingColor(0, 220, 255)).build());

   private final Setting<Integer> border = this.sgGeneral
      .add(new IntSetting.Builder().name("border").description("Space around the element.").defaultValue(4).build());

   private final Setting<Boolean> customScale = this.sgScale
      .add(new BoolSetting.Builder().name("custom-scale").description("Custom text scale.").defaultValue(false).build());

   private final Setting<Double> scale = this.sgScale
      .add(new DoubleSetting.Builder().name("scale").description("Scale factor.").visible(this.customScale::get).defaultValue(1.0).min(0.5).sliderRange(0.5, 3.0).build());

   private final Setting<Boolean> background = this.sgBackground
      .add(new BoolSetting.Builder().name("background").description("Renders background.").defaultValue(true).build());

   private final Setting<SettingColor> backgroundColor = this.sgBackground
      .add(new ColorSetting.Builder().name("background-color").description("Background color.").visible(this.background::get).defaultValue(new SettingColor(20, 20, 20, 120)).build());

   public ChunkOreRadarHud() {
      super(INFO);
   }

   @Override
   public void setSize(double width, double height) {
      super.setSize(width + (double)(this.border.get() * 2), height + (double)(this.border.get() * 2));
   }

   private ChunkScanResult getResult() {
      ChunkScanner scanner = Modules.get().get(ChunkScanner.class);
      return scanner != null ? scanner.getLastResult() : null;
   }

   private double getScale() {
      return this.customScale.get() ? this.scale.get() : -1.0;
   }

   @Override
   public void tick(HudRenderer renderer) {
      double scale = this.getScale();
      boolean shadow = this.shadow.get();
      ChunkScanResult result = this.getResult();

      String title;
      if (result != null) {
         if (result.totalCustom > 0 && result.totalOres > 0) {
            title = String.format("Chunk [%d, %d] (%d targets)", result.chunkPos.x, result.chunkPos.z, result.totalBlocks);
         } else if (result.totalCustom > 0) {
            title = String.format("Chunk [%d, %d] (%d custom)", result.chunkPos.x, result.chunkPos.z, result.totalBlocks);
         } else {
            title = String.format("Chunk [%d, %d] (%d ores)", result.chunkPos.x, result.chunkPos.z, result.totalOres);
         }
      } else {
         title = "Chunk Radar (No Data)";
      }

      double width = renderer.textWidth(title, shadow, scale);
      double lineHeight = Math.max(16.0, renderer.textHeight(shadow, scale) + 2.0);
      double height = renderer.textHeight(shadow, scale) + 4.0;

      if (result != null && !result.entries.isEmpty()) {
         int count = 0;
         for (DiscoveredBlockEntry entry : result.entries) {
            if (count >= this.limit.get()) break;

            StringBuilder sb = new StringBuilder();
            if (this.showIcons.get()) sb.append("   "); // icon spacing
            if (entry.isCustomTarget) sb.append("* ");
            sb.append(entry.displayName);
            if (this.showMod.get()) sb.append(" [").append(entry.modName).append("]");
            sb.append(" x").append(entry.count);
            if (this.showDepth.get()) sb.append(String.format(" (Y:%d..%d)", entry.minY, entry.maxY));

            width = Math.max(width, renderer.textWidth(sb.toString(), shadow, scale) + (this.showIcons.get() ? 18.0 : 0.0));
            height += lineHeight;
            count++;
         }
      }

      this.setSize(width, height);
   }

   @Override
   public void render(HudRenderer renderer) {
      double scale = this.getScale();
      boolean shadow = this.shadow.get();
      double x = (double)(this.x + this.border.get());
      double y = (double)(this.y + this.border.get());

      if (this.background.get()) {
         renderer.quad((double)this.x, (double)this.y, (double)this.getWidth(), (double)this.getHeight(), this.backgroundColor.get());
      }

      ChunkScanResult result = this.getResult();
      String title;
      if (result != null) {
         if (result.totalCustom > 0 && result.totalOres > 0) {
            title = String.format("Chunk [%d, %d] (%d targets)", result.chunkPos.x, result.chunkPos.z, result.totalBlocks);
         } else if (result.totalCustom > 0) {
            title = String.format("Chunk [%d, %d] (%d custom)", result.chunkPos.x, result.chunkPos.z, result.totalBlocks);
         } else {
            title = String.format("Chunk [%d, %d] (%d ores)", result.chunkPos.x, result.chunkPos.z, result.totalOres);
         }
      } else {
         title = "Chunk Radar (No Data)";
      }

      renderer.text(title, x, y, this.accentColor.get(), shadow, scale);
      y += renderer.textHeight(shadow, scale) + 4.0;

      if (result != null && !result.entries.isEmpty()) {
         int count = 0;
         double lineHeight = Math.max(16.0, renderer.textHeight(shadow, scale) + 2.0);

         for (DiscoveredBlockEntry entry : result.entries) {
            if (count >= this.limit.get()) break;

            double rowX = x;
            if (this.showIcons.get() && entry.icon != null) {
               renderer.item(entry.icon, (int)rowX, (int)y, 1.0f, false);
               rowX += 18.0;
            }

            String displayName = (entry.isCustomTarget ? "* " : "") + entry.displayName;
            Color nameColor = entry.isCustomTarget ? this.accentColor.get() : this.primaryColor.get();
            rowX = renderer.text(displayName, rowX, y + 2.0, nameColor, shadow, scale);

            if (this.showMod.get()) {
               rowX = renderer.text(" [" + entry.modName + "]", rowX, y + 2.0, this.secondaryColor.get(), shadow, scale);
            }

            rowX = renderer.text(" x" + entry.count, rowX, y + 2.0, this.accentColor.get(), shadow, scale);

            if (this.showDepth.get()) {
               String depthStr = String.format(" (Y:%d..%d)", entry.minY, entry.maxY);
               renderer.text(depthStr, rowX, y + 2.0, this.secondaryColor.get(), shadow, scale);
            }

            y += lineHeight;
            count++;
         }
      }
   }
}
