package meteordevelopment.meteorclient.systems.hud.elements;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ModuleListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.Alignment;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public class ActiveModulesHud extends HudElement {
   public static final HudElementInfo<ActiveModulesHud> INFO = new HudElementInfo<>(
      Hud.GROUP, "active-modules", "Displays your active modules.", ActiveModulesHud::new
   );
   private static final Color WHITE = new Color();
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<List<Module>> hiddenModules = this.sgGeneral
      .add(new ModuleListSetting.Builder().name("hidden-modules").description("Which modules not to show in the list.").build());
   private final Setting<ActiveModulesHud.Sort> sort = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("sort")).description("How to sort active modules."))
               .defaultValue(ActiveModulesHud.Sort.Biggest))
            .build()
      );
   private final Setting<Boolean> activeInfo = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("additional-info")
            .description("Shows additional info from the module next to the name in the active modules list.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   private final Setting<SettingColor> moduleInfoColor = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("module-info-color")
            .description("Color of module info text.")
            .defaultValue(new SettingColor(175, 175, 175))
            .visible(this.activeInfo::get)
            .build()
      );
   private final Setting<ActiveModulesHud.ColorMode> colorMode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("color-mode"))
                  .description("What color to use for active modules."))
               .defaultValue(ActiveModulesHud.ColorMode.Rainbow))
            .build()
      );
   private final Setting<SettingColor> flatColor = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("flat-color")
            .description("Color for flat color mode.")
            .defaultValue(new SettingColor(225, 25, 25))
            .visible(() -> this.colorMode.get() == ActiveModulesHud.ColorMode.Flat)
            .build()
      );
   private final Setting<Boolean> shadow = this.sgGeneral
      .add(new BoolSetting.Builder().name("shadow").description("Renders shadow behind text.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<Alignment> alignment = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("alignment")).description("Horizontal alignment."))
               .defaultValue(Alignment.Auto))
            .build()
      );
   private final Setting<Boolean> outlines = this.sgGeneral
      .add(new BoolSetting.Builder().name("outlines").description("Whether or not to render outlines").defaultValue(Boolean.valueOf(false)).build());
   private final Setting<Integer> outlineWidth = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("outline-width")
            .description("Outline width")
            .defaultValue(Integer.valueOf(2))
            .min(1)
            .sliderMin(1)
            .visible(this.outlines::get)
            .build()
      );
   private final Setting<Boolean> customScale = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("custom-scale")
            .description("Applies custom text scale rather than the global one.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   private final Setting<Double> scale = this.sgGeneral
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
   private final Setting<Double> rainbowSpeed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("rainbow-speed")
            .description("Rainbow speed of rainbow color mode.")
            .defaultValue(0.05)
            .sliderMin(0.01)
            .sliderMax(0.2)
            .decimalPlaces(4)
            .visible(() -> this.colorMode.get() == ActiveModulesHud.ColorMode.Rainbow)
            .build()
      );
   private final Setting<Double> rainbowSpread = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("rainbow-spread")
            .description("Rainbow spread of rainbow color mode.")
            .defaultValue(0.01)
            .sliderMin(0.001)
            .sliderMax(0.05)
            .decimalPlaces(4)
            .visible(() -> this.colorMode.get() == ActiveModulesHud.ColorMode.Rainbow)
            .build()
      );
   private final Setting<Double> rainbowSaturation = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("rainbow-saturation")
            .defaultValue(1.0)
            .sliderRange(0.0, 1.0)
            .visible(() -> this.colorMode.get() == ActiveModulesHud.ColorMode.Rainbow)
            .build()
      );
   private final Setting<Double> rainbowBrightness = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("rainbow-brightness")
            .defaultValue(1.0)
            .sliderRange(0.0, 1.0)
            .visible(() -> this.colorMode.get() == ActiveModulesHud.ColorMode.Rainbow)
            .build()
      );
   private final List<Module> modules = new ArrayList<>();
   private final Color rainbow = new Color(255, 255, 255);
   private double rainbowHue1;
   private double rainbowHue2;
   private double prevX;
   private double prevTextLength;
   private Color prevColor = new Color();

   public ActiveModulesHud() {
      super(INFO);
   }

   @Override
   public void tick(HudRenderer renderer) {
      this.modules.clear();

      for (Module module : Modules.get().getActive()) {
         if (!this.hiddenModules.get().contains(module)) {
            this.modules.add(module);
         }
      }

      if (this.modules.isEmpty()) {
         if (this.isInEditor()) {
            this.setSize(renderer.textWidth("Active Modules", this.shadow.get(), this.getScale()), renderer.textHeight(this.shadow.get(), this.getScale()));
         }
      } else {
         this.modules.sort((e1, e2) -> {
            return switch ((ActiveModulesHud.Sort)this.sort.get()) {
               case Alphabetical -> e1.title.compareTo(e2.title);
               case Biggest -> Double.compare(this.getModuleWidth(renderer, e2), this.getModuleWidth(renderer, e1));
               case Smallest -> Double.compare(this.getModuleWidth(renderer, e1), this.getModuleWidth(renderer, e2));
            };
         });
         double width = 0.0;
         double height = 0.0;

         for (int i = 0; i < this.modules.size(); i++) {
            Module modulex = this.modules.get(i);
            width = Math.max(width, this.getModuleWidth(renderer, modulex));
            height += renderer.textHeight(this.shadow.get(), this.getScale());
            if (i > 0) {
               height += 2.0;
            }
         }

         this.setSize(width, height);
      }
   }

   @Override
   public void render(HudRenderer renderer) {
      double x = (double)this.x;
      double y = (double)this.y;
      if (this.modules.isEmpty()) {
         if (this.isInEditor()) {
            renderer.text("Active Modules", x, y, WHITE, this.shadow.get(), this.getScale());
         }
      } else {
         this.rainbowHue1 = this.rainbowHue1 + this.rainbowSpeed.get() * renderer.delta;
         if (this.rainbowHue1 > 1.0) {
            this.rainbowHue1--;
         } else if (this.rainbowHue1 < -1.0) {
            this.rainbowHue1++;
         }

         this.rainbowHue2 = this.rainbowHue1;
         this.prevX = x;

         for (int i = 0; i < this.modules.size(); i++) {
            double offset = this.alignX(this.getModuleWidth(renderer, this.modules.get(i)), this.alignment.get());
            this.renderModule(renderer, this.modules, i, x + offset, y);
            this.prevX = x + offset;
            y += 2.0 + renderer.textHeight(this.shadow.get(), this.getScale());
         }
      }
   }

   private void renderModule(HudRenderer renderer, List<Module> modules, int index, double x, double y) {
      Module module = modules.get(index);
      Color color = this.flatColor.get();
      switch ((ActiveModulesHud.ColorMode)this.colorMode.get()) {
         case Random:
            color = module.color;
            break;
         case Rainbow:
            this.rainbowHue2 = this.rainbowHue2 + this.rainbowSpread.get();
            int c = java.awt.Color.HSBtoRGB((float)this.rainbowHue2, this.rainbowSaturation.get().floatValue(), this.rainbowBrightness.get().floatValue());
            this.rainbow.r = Color.toRGBAR(c);
            this.rainbow.g = Color.toRGBAG(c);
            this.rainbow.b = Color.toRGBAB(c);
            color = this.rainbow;
      }

      renderer.text(module.title, x, y, color, this.shadow.get(), this.getScale());
      double emptySpace = renderer.textWidth(" ", this.shadow.get(), this.getScale());
      double textHeight = renderer.textHeight(this.shadow.get(), this.getScale());
      double textLength = renderer.textWidth(module.title, this.shadow.get(), this.getScale());
      if (this.activeInfo.get()) {
         String info = module.getInfoString();
         if (info != null) {
            renderer.text(info, x + emptySpace + textLength, y, this.moduleInfoColor.get(), this.shadow.get(), this.getScale());
            textLength += emptySpace + renderer.textWidth(info, this.shadow.get(), this.getScale());
         }
      }

      if (this.outlines.get()) {
         if (index == 0) {
            renderer.quad(
               x - 2.0 - (double)this.outlineWidth.get().intValue(),
               y - 2.0,
               (double)this.outlineWidth.get().intValue(),
               textHeight + 4.0,
               this.prevColor,
               this.prevColor,
               color,
               color
            );
            renderer.quad(
               x + textLength + 2.0, y - 2.0, (double)this.outlineWidth.get().intValue(), textHeight + 4.0, this.prevColor, this.prevColor, color, color
            );
            renderer.quad(
               x - 2.0 - (double)this.outlineWidth.get().intValue(),
               y - 2.0 - (double)this.outlineWidth.get().intValue(),
               textLength + 4.0 + (double)(this.outlineWidth.get() * 2),
               (double)this.outlineWidth.get().intValue(),
               this.prevColor,
               this.prevColor,
               color,
               color
            );
            if (index == modules.size() - 1) {
               renderer.quad(
                  x - 2.0 - (double)this.outlineWidth.get().intValue(),
                  y + textHeight + 2.0,
                  textLength + 4.0 + (double)(this.outlineWidth.get() * 2),
                  (double)this.outlineWidth.get().intValue(),
                  this.prevColor,
                  this.prevColor,
                  color,
                  color
               );
            }
         } else if (index == modules.size() - 1) {
            renderer.quad(
               x - 2.0 - (double)this.outlineWidth.get().intValue(),
               y,
               (double)this.outlineWidth.get().intValue(),
               textHeight + 2.0 + (double)this.outlineWidth.get().intValue(),
               this.prevColor,
               this.prevColor,
               color,
               color
            );
            renderer.quad(
               x + textLength + 2.0,
               y,
               (double)this.outlineWidth.get().intValue(),
               textHeight + 2.0 + (double)this.outlineWidth.get().intValue(),
               this.prevColor,
               this.prevColor,
               color,
               color
            );
            renderer.quad(
               x - 2.0 - (double)this.outlineWidth.get().intValue(),
               y + textHeight + 2.0,
               textLength + 4.0 + (double)(this.outlineWidth.get() * 2),
               (double)this.outlineWidth.get().intValue(),
               this.prevColor,
               this.prevColor,
               color,
               color
            );
         }

         if (index > 0) {
            if (index < modules.size() - 1) {
               renderer.quad(
                  x - 2.0 - (double)this.outlineWidth.get().intValue(),
                  y,
                  (double)this.outlineWidth.get().intValue(),
                  textHeight + 2.0,
                  this.prevColor,
                  this.prevColor,
                  color,
                  color
               );
               renderer.quad(
                  x + textLength + 2.0, y, (double)this.outlineWidth.get().intValue(), textHeight + 2.0, this.prevColor, this.prevColor, color, color
               );
            }

            renderer.quad(
               Math.min(this.prevX, x) - 2.0 - (double)this.outlineWidth.get().intValue(),
               Math.max(this.prevX, x) == x ? y : y - (double)this.outlineWidth.get().intValue(),
               Math.max(this.prevX, x) - 2.0 - (Math.min(this.prevX, x) - 2.0 - (double)this.outlineWidth.get().intValue()),
               (double)this.outlineWidth.get().intValue(),
               this.prevColor,
               this.prevColor,
               color,
               color
            );
            renderer.quad(
               Math.min(this.prevX + this.prevTextLength, x + textLength) + 2.0,
               Math.min(this.prevX + this.prevTextLength, x + textLength) == x + textLength ? y : y - (double)this.outlineWidth.get().intValue(),
               Math.max(this.prevX + this.prevTextLength, x + textLength)
                  + 2.0
                  + (double)this.outlineWidth.get().intValue()
                  - (Math.min(this.prevX + this.prevTextLength, x + textLength) + 2.0),
               (double)this.outlineWidth.get().intValue(),
               this.prevColor,
               this.prevColor,
               color,
               color
            );
         }
      }

      this.prevTextLength = textLength;
      this.prevColor = color;
   }

   private double getModuleWidth(HudRenderer renderer, Module module) {
      double width = renderer.textWidth(module.title, this.shadow.get(), this.getScale());
      if (this.activeInfo.get()) {
         String info = module.getInfoString();
         if (info != null) {
            width += renderer.textWidth(" ", this.shadow.get(), this.getScale()) + renderer.textWidth(info, this.shadow.get(), this.getScale());
         }
      }

      return width;
   }

   private double getScale() {
      return this.customScale.get() ? this.scale.get() : -1.0;
   }

   public static enum ColorMode {
      Flat,
      Random,
      Rainbow;
   }

   public static enum Sort {
      Alphabetical,
      Biggest,
      Smallest;
   }
}
