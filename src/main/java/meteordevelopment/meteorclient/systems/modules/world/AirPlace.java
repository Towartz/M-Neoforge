package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class AirPlace extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRange = this.settings.createGroup("Range");
   private final Setting<Boolean> render = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("render")
            .description("Renders a block overlay where the obsidian will be placed.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   private final Setting<ShapeMode> shapeMode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode"))
                  .description("How the shapes are rendered."))
               .defaultValue(ShapeMode.Both))
            .build()
      );
   private final Setting<SettingColor> sideColor = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("side-color")
            .description("The color of the sides of the blocks being rendered.")
            .defaultValue(new SettingColor(204, 0, 0, 10))
            .build()
      );
   private final Setting<SettingColor> lineColor = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("line-color")
            .description("The color of the lines of the blocks being rendered.")
            .defaultValue(new SettingColor(204, 0, 0, 255))
            .build()
      );
   private final Setting<Boolean> customRange = this.sgRange
      .add(new BoolSetting.Builder().name("custom-range").description("Use custom range for air place.").defaultValue(Boolean.valueOf(false)).build());
   private final Setting<Double> range = this.sgRange
      .add(
         new DoubleSetting.Builder()
            .name("range")
            .description("Custom range to place at.")
            .visible(this.customRange::get)
            .defaultValue(5.0)
            .min(0.0)
            .sliderMax(6.0)
            .build()
      );
   private HitResult hitResult;

   public AirPlace() {
      super(Categories.Player, "air-place", "Places a block where your crosshair is pointing at.");
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      double r = this.customRange.get() ? this.range.get() : this.mc.player.blockInteractionRange();
      this.hitResult = this.mc.getCameraEntity().pick(r, 0.0F, false);
      if (this.hitResult instanceof BlockHitResult blockHitResult
         && (this.mc.player.getMainHandItem().getItem() instanceof BlockItem || this.mc.player.getMainHandItem().getItem() instanceof SpawnEggItem)) {
         if (this.mc.options.keyUse.isDown()) {
            BlockUtils.place(blockHitResult.getBlockPos(), InteractionHand.MAIN_HAND, this.mc.player.getInventory().selected, false, 0, true, true, false);
         }

         return;
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (this.hitResult instanceof BlockHitResult blockHitResult
         && this.mc.level.getBlockState(blockHitResult.getBlockPos()).canBeReplaced()
         && (this.mc.player.getMainHandItem().getItem() instanceof BlockItem || this.mc.player.getMainHandItem().getItem() instanceof SpawnEggItem)
         && this.render.get()) {
         event.renderer.box(blockHitResult.getBlockPos(), this.sideColor.get(), this.lineColor.get(), this.shapeMode.get(), 0);
         return;
      }
   }
}
