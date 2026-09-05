package meteordevelopment.meteorclient.systems.modules.render.blockesp;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockDataSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.world.level.block.Block;

public class ESPBlockDataScreen extends WindowScreen {
   private final ESPBlockData blockData;
   private final Block block;
   private final BlockDataSetting<ESPBlockData> setting;

   public ESPBlockDataScreen(GuiTheme theme, ESPBlockData blockData, Block block, BlockDataSetting<ESPBlockData> setting) {
      super(theme, "Configure Block");
      this.blockData = blockData;
      this.block = block;
      this.setting = setting;
   }

   @Override
   public void initWidgets() {
      Settings settings = new Settings();
      SettingGroup sgGeneral = settings.getDefaultGroup();
      SettingGroup sgTracer = settings.createGroup("Tracer");
      sgGeneral.add(
         new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the shape is rendered.")
            .defaultValue(ShapeMode.Lines)
            .onModuleActivated(shapeModeSetting -> shapeModeSetting.set(this.blockData.shapeMode))
            .onChanged(shapeMode -> {
               this.blockData.shapeMode = shapeMode;
               this.changed(this.blockData, this.block, this.setting);
            })
            .build()
      );
      sgGeneral.add(
         new ColorSetting.Builder()
            .name("line-color")
            .description("Color of lines.")
            .defaultValue(new SettingColor(0, 255, 200))
            .onModuleActivated(settingColorSetting -> settingColorSetting.set(this.blockData.lineColor))
            .onChanged(settingColor -> {
               this.blockData.lineColor.set((Color)settingColor);
               this.changed(this.blockData, this.block, this.setting);
            })
            .build()
      );
      sgGeneral.add(
         new ColorSetting.Builder()
            .name("side-color")
            .description("Color of sides.")
            .defaultValue(new SettingColor(0, 255, 200, 45))
            .onModuleActivated(settingColorSetting -> settingColorSetting.set(this.blockData.sideColor))
            .onChanged(settingColor -> {
               this.blockData.sideColor.set((Color)settingColor);
               this.changed(this.blockData, this.block, this.setting);
            })
            .build()
      );
      sgTracer.add(
         new BoolSetting.Builder()
            .name("tracer")
            .description("If tracer line is allowed to this block.")
            .defaultValue(Boolean.valueOf(true))
            .onModuleActivated(booleanSetting -> booleanSetting.set(this.blockData.tracer))
            .onChanged(aBoolean -> {
               this.blockData.tracer = aBoolean;
               this.changed(this.blockData, this.block, this.setting);
            })
            .build()
      );
      sgTracer.add(
         new ColorSetting.Builder()
            .name("tracer-color")
            .description("Color of tracer line.")
            .defaultValue(new SettingColor(0, 255, 200, 125))
            .onModuleActivated(settingColorSetting -> settingColorSetting.set(this.blockData.tracerColor))
            .onChanged(settingColor -> {
               this.blockData.tracerColor = settingColor;
               this.changed(this.blockData, this.block, this.setting);
            })
            .build()
      );
      settings.onActivated();
      this.add(this.theme.settings(settings)).expandX();
   }

   private void changed(ESPBlockData blockData, Block block, BlockDataSetting<ESPBlockData> setting) {
      if (!blockData.isChanged() && block != null && setting != null) {
         setting.get().put(block, blockData);
         setting.onChanged();
      }

      blockData.changed();
   }
}
