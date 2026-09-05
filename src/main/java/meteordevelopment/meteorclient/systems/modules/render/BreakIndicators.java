package meteordevelopment.meteorclient.systems.modules.render;

import java.util.List;
import java.util.Map;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.mixin.ClientPlayerInteractionManagerAccessor;
import meteordevelopment.meteorclient.mixin.WorldRendererAccessor;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.PacketMine;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BreakIndicators extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<ShapeMode> shapeMode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode"))
                  .description("How the shapes are rendered."))
               .defaultValue(ShapeMode.Both))
            .build()
      );
   public final Setting<Boolean> packetMine = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("packet-mine")
            .description("Whether or not to render blocks being packet mined.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   private final Setting<SettingColor> startColor = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("start-color")
            .description("The color for the non-broken block.")
            .defaultValue(new SettingColor(25, 252, 25, 150))
            .build()
      );
   private final Setting<SettingColor> endColor = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("end-color")
            .description("The color for the fully-broken block.")
            .defaultValue(new SettingColor(255, 25, 25, 150))
            .build()
      );
   private final Color cSides = new Color();
   private final Color cLines = new Color();

   public BreakIndicators() {
      super(Categories.Render, "break-indicators", "Renders the progress of a block being broken.");
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      this.renderNormal(event);
      if (this.packetMine.get() && !Modules.get().get(PacketMine.class).blocks.isEmpty()) {
         this.renderPacket(event, Modules.get().get(PacketMine.class).blocks);
      }
   }

   private void renderNormal(Render3DEvent event) {
      Map<Integer, BlockDestructionProgress> blocks = ((WorldRendererAccessor)this.mc.levelRenderer).getBlockBreakingInfos();
      float ownBreakingStage = ((ClientPlayerInteractionManagerAccessor)this.mc.gameMode).getBreakingProgress();
      BlockPos ownBreakingPos = ((ClientPlayerInteractionManagerAccessor)this.mc.gameMode).getCurrentBreakingBlockPos();
      if (ownBreakingPos != null && ownBreakingStage > 0.0F) {
         BlockState state = this.mc.level.getBlockState(ownBreakingPos);
         VoxelShape shape = state.getShape(this.mc.level, ownBreakingPos);
         if (shape == null || shape.isEmpty()) {
            return;
         }

         AABB orig = shape.bounds();
         double shrinkFactor = 1.0 - (double)ownBreakingStage;
         this.renderBlock(event, orig, ownBreakingPos, shrinkFactor, (double)ownBreakingStage);
      }

      blocks.values().forEach(info -> {
         BlockPos pos = info.getPos();
         int stage = info.getProgress();
         if (!pos.equals(ownBreakingPos)) {
            BlockState statex = this.mc.level.getBlockState(pos);
            VoxelShape shapex = statex.getShape(this.mc.level, pos);
            if (shapex != null && !shapex.isEmpty()) {
               AABB origx = shapex.bounds();
               double shrinkFactorx = (double)(9 - (stage + 1)) / 9.0;
               double progress = 1.0 - shrinkFactorx;
               this.renderBlock(event, origx, pos, shrinkFactorx, progress);
            }
         }
      });
   }

   private void renderPacket(Render3DEvent event, List<PacketMine.MyBlock> blocks) {
      for (PacketMine.MyBlock block : blocks) {
         if (block.mining && block.progress != Double.POSITIVE_INFINITY) {
            VoxelShape shape = block.blockState.getShape(this.mc.level, block.blockPos);
            if (shape == null || shape.isEmpty()) {
               return;
            }

            AABB orig = shape.bounds();
            double progressNormalised = block.progress > 1.0 ? 1.0 : block.progress;
            double shrinkFactor = 1.0 - progressNormalised;
            BlockPos pos = block.blockPos;
            this.renderBlock(event, orig, pos, shrinkFactor, progressNormalised);
         }
      }
   }

   private void renderBlock(Render3DEvent event, AABB orig, BlockPos pos, double shrinkFactor, double progress) {
      AABB box = orig.contract(orig.getXsize() * shrinkFactor, orig.getYsize() * shrinkFactor, orig.getZsize() * shrinkFactor);
      double xShrink = orig.getXsize() * shrinkFactor / 2.0;
      double yShrink = orig.getYsize() * shrinkFactor / 2.0;
      double zShrink = orig.getZsize() * shrinkFactor / 2.0;
      double x1 = (double)pos.getX() + box.minX + xShrink;
      double y1 = (double)pos.getY() + box.minY + yShrink;
      double z1 = (double)pos.getZ() + box.minZ + zShrink;
      double x2 = (double)pos.getX() + box.maxX + xShrink;
      double y2 = (double)pos.getY() + box.maxY + yShrink;
      double z2 = (double)pos.getZ() + box.maxZ + zShrink;
      Color c1Sides = this.startColor.get().copy().a(this.startColor.get().a / 2);
      Color c2Sides = this.endColor.get().copy().a(this.endColor.get().a / 2);
      this.cSides
         .set(
            (int)Math.round((double)c1Sides.r + (double)(c2Sides.r - c1Sides.r) * progress),
            (int)Math.round((double)c1Sides.g + (double)(c2Sides.g - c1Sides.g) * progress),
            (int)Math.round((double)c1Sides.b + (double)(c2Sides.b - c1Sides.b) * progress),
            (int)Math.round((double)c1Sides.a + (double)(c2Sides.a - c1Sides.a) * progress)
         );
      Color c1Lines = this.startColor.get();
      Color c2Lines = this.endColor.get();
      this.cLines
         .set(
            (int)Math.round((double)c1Lines.r + (double)(c2Lines.r - c1Lines.r) * progress),
            (int)Math.round((double)c1Lines.g + (double)(c2Lines.g - c1Lines.g) * progress),
            (int)Math.round((double)c1Lines.b + (double)(c2Lines.b - c1Lines.b) * progress),
            (int)Math.round((double)c1Lines.a + (double)(c2Lines.a - c1Lines.a) * progress)
         );
      event.renderer.box(x1, y1, z1, x2, y2, z2, this.cSides, this.cLines, this.shapeMode.get(), 0);
   }
}
