package meteordevelopment.meteorclient.systems.hud.elements;

import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.mixin.WorldRendererAccessor;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class HoleHud extends HudElement {
   public static final HudElementInfo<HoleHud> INFO = new HudElementInfo<>(
      Hud.GROUP, "hole", "Displays information about the hole you are standing in.", HoleHud::new
   );
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgBackground = this.settings.createGroup("Background");
   public final Setting<List<Block>> safe = this.sgGeneral
      .add(
         new BlockListSetting.Builder()
            .name("safe-blocks")
            .description("Which blocks to consider safe.")
            .defaultValue(Blocks.OBSIDIAN, Blocks.BEDROCK, Blocks.CRYING_OBSIDIAN, Blocks.NETHERITE_BLOCK)
            .build()
      );
   private final Setting<Double> scale = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("scale")
            .description("The scale.")
            .defaultValue(2.0)
            .onChanged(aDouble -> this.calculateSize())
            .min(1.0)
            .sliderRange(1.0, 5.0)
            .build()
      );
   private final Setting<Integer> border = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("border")
            .description("How much space to add around the element.")
            .defaultValue(Integer.valueOf(0))
            .onChanged(integer -> this.calculateSize())
            .build()
      );
   private final Setting<Boolean> background = this.sgBackground
      .add(new BoolSetting.Builder().name("background").description("Displays background.").defaultValue(Boolean.valueOf(false)).build());
   private final Setting<SettingColor> backgroundColor = this.sgBackground
      .add(
         new ColorSetting.Builder()
            .name("background-color")
            .description("Color used for the background.")
            .visible(this.background::get)
            .defaultValue(new SettingColor(25, 25, 25, 50))
            .build()
      );
   private final Color BG_COLOR = new Color(255, 25, 25, 100);
   private final Color OL_COLOR = new Color(255, 25, 25, 255);

   public HoleHud() {
      super(INFO);
      this.calculateSize();
   }

   @Override
   public void setSize(double width, double height) {
      super.setSize(width + (double)(this.border.get() * 2), height + (double)(this.border.get() * 2));
   }

   private void calculateSize() {
      this.setSize(48.0 * this.scale.get(), 48.0 * this.scale.get());
   }

   @Override
   public void render(HudRenderer renderer) {
      renderer.post(() -> {
         double x = (double)(this.x + this.border.get());
         double y = (double)(this.y + this.border.get());
         this.drawBlock(renderer, this.get(HoleHud.Facing.Left), x, y + 16.0 * this.scale.get());
         this.drawBlock(renderer, this.get(HoleHud.Facing.Front), x + 16.0 * this.scale.get(), y);
         this.drawBlock(renderer, this.get(HoleHud.Facing.Right), x + 32.0 * this.scale.get(), y + 16.0 * this.scale.get());
         this.drawBlock(renderer, this.get(HoleHud.Facing.Back), x + 16.0 * this.scale.get(), y + 32.0 * this.scale.get());
      });
      if (this.background.get()) {
         renderer.quad((double)this.x, (double)this.y, (double)this.getWidth(), (double)this.getHeight(), this.backgroundColor.get());
      }
   }

   private Direction get(HoleHud.Facing dir) {
      return this.isInEditor() ? Direction.DOWN : Direction.fromYRot((double)Mth.wrapDegrees(MeteorClient.mc.player.getYRot() + (float)dir.offset));
   }

   private void drawBlock(HudRenderer renderer, Direction dir, double x, double y) {
      Block block = dir == Direction.DOWN
         ? Blocks.OBSIDIAN
         : MeteorClient.mc.level.getBlockState(MeteorClient.mc.player.blockPosition().relative(dir)).getBlock();
      if (this.safe.get().contains(block)) {
         renderer.item(block.asItem().getDefaultInstance(), (int)x, (int)y, this.scale.get().floatValue(), false);
         if (dir != Direction.DOWN) {
            ((WorldRendererAccessor)MeteorClient.mc.levelRenderer).getBlockBreakingInfos().values().forEach(info -> {
               if (info.getPos().equals(MeteorClient.mc.player.blockPosition().relative(dir))) {
                  this.renderBreaking(renderer, x, y, (double)((float)info.getProgress() / 9.0F));
               }
            });
         }
      }
   }

   private void renderBreaking(HudRenderer renderer, double x, double y, double percent) {
      renderer.quad(x, y, 16.0 * percent * this.scale.get(), 16.0 * this.scale.get(), this.BG_COLOR);
      renderer.quad(x, y, 16.0 * this.scale.get(), 1.0 * this.scale.get(), this.OL_COLOR);
      renderer.quad(x, y + 15.0 * this.scale.get(), 16.0 * this.scale.get(), 1.0 * this.scale.get(), this.OL_COLOR);
      renderer.quad(x, y, 1.0 * this.scale.get(), 16.0 * this.scale.get(), this.OL_COLOR);
      renderer.quad(x + 15.0 * this.scale.get(), y, 1.0 * this.scale.get(), 16.0 * this.scale.get(), this.OL_COLOR);
   }

   private static enum Facing {
      Left(-90),
      Right(90),
      Front(0),
      Back(180);

      public final int offset;

      private Facing(int offset) {
         this.offset = offset;
      }
   }
}
