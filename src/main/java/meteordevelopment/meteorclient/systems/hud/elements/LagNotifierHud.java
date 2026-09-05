package meteordevelopment.meteorclient.systems.hud.elements;

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
import meteordevelopment.meteorclient.utils.world.TickRate;

public class LagNotifierHud extends HudElement {
   public static final HudElementInfo<LagNotifierHud> INFO = new HudElementInfo<>(
      Hud.GROUP, "lag-notifier", "Displays if the server is lagging in ticks.", LagNotifierHud::new
   );
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgScale = this.settings.createGroup("Scale");
   private final SettingGroup sgBackground = this.settings.createGroup("Background");
   private final Setting<Boolean> shadow = this.sgGeneral
      .add(new BoolSetting.Builder().name("shadow").description("Text shadow.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<SettingColor> textColor = this.sgGeneral
      .add(new ColorSetting.Builder().name("text-color").description("A.").defaultValue(new SettingColor()).build());
   private final Setting<SettingColor> color1 = this.sgGeneral
      .add(new ColorSetting.Builder().name("color-1").description("First color.").defaultValue(new SettingColor(255, 255, 5)).build());
   private final Setting<SettingColor> color2 = this.sgGeneral
      .add(new ColorSetting.Builder().name("color-2").description("Second color.").defaultValue(new SettingColor(235, 158, 52)).build());
   private final Setting<SettingColor> color3 = this.sgGeneral
      .add(new ColorSetting.Builder().name("color-3").description("Third color.").defaultValue(new SettingColor(225, 45, 45)).build());
   private final Setting<Integer> border = this.sgGeneral
      .add(new IntSetting.Builder().name("border").description("How much space to add around the element.").defaultValue(Integer.valueOf(0)).build());
   private final Setting<Boolean> customScale = this.sgScale
      .add(
         new BoolSetting.Builder()
            .name("custom-scale")
            .description("Applies custom text scale rather than the global one.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   private final Setting<Double> scale = this.sgScale
      .add(
         new DoubleSetting.Builder()
            .name("scale")
            .description("Custom scale.")
            .visible(this.customScale::get)
            .defaultValue(1.0)
            .min(0.5)
            .sliderRange(0.5, 3.0)
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

   public LagNotifierHud() {
      super(INFO);
   }

   @Override
   public void setSize(double width, double height) {
      super.setSize(width + (double)(this.border.get() * 2), height + (double)(this.border.get() * 2));
   }

   @Override
   public void render(HudRenderer renderer) {
      if (this.background.get()) {
         renderer.quad((double)this.x, (double)this.y, (double)this.getWidth(), (double)this.getHeight(), this.backgroundColor.get());
      }

      if (this.isInEditor()) {
         this.render(renderer, "4.3", this.color3.get());
      } else {
         float timeSinceLastTick = TickRate.INSTANCE.getTimeSinceLastTick();
         if (timeSinceLastTick >= 1.0F) {
            Color color;
            if (timeSinceLastTick > 10.0F) {
               color = this.color3.get();
            } else if (timeSinceLastTick > 3.0F) {
               color = this.color2.get();
            } else {
               color = this.color1.get();
            }

            this.render(renderer, String.format("%.1f", timeSinceLastTick), color);
         }
      }
   }

   private void render(HudRenderer renderer, String right, Color rightColor) {
      double x = (double)(this.x + this.border.get());
      double y = (double)(this.y + this.border.get());
      double x2 = renderer.text("Time since last tick ", x, y, this.textColor.get(), this.shadow.get(), this.getScale());
      x2 = renderer.text(right, x2, y, rightColor, this.shadow.get(), this.getScale());
      this.setSize(x2 - x, renderer.textHeight(this.shadow.get(), this.getScale()));
   }

   private double getScale() {
      return this.customScale.get() ? this.scale.get() : -1.0;
   }
}
