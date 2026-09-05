package meteordevelopment.meteorclient.systems.modules.render.blockesp;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.utils.IScreenFactory;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockDataSetting;
import meteordevelopment.meteorclient.settings.IBlockData;
import meteordevelopment.meteorclient.utils.misc.IChangeable;
import meteordevelopment.meteorclient.utils.misc.ICopyable;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;

public class ESPBlockData implements ICopyable<ESPBlockData>, ISerializable<ESPBlockData>, IChangeable, IBlockData<ESPBlockData>, IScreenFactory {
   public ShapeMode shapeMode;
   public SettingColor lineColor;
   public SettingColor sideColor;
   public boolean tracer;
   public SettingColor tracerColor;
   private boolean changed;

   public ESPBlockData(ShapeMode shapeMode, SettingColor lineColor, SettingColor sideColor, boolean tracer, SettingColor tracerColor) {
      this.shapeMode = shapeMode;
      this.lineColor = lineColor;
      this.sideColor = sideColor;
      this.tracer = tracer;
      this.tracerColor = tracerColor;
   }

   @Override
   public WidgetScreen createScreen(GuiTheme theme, Block block, BlockDataSetting<ESPBlockData> setting) {
      return new ESPBlockDataScreen(theme, this, block, setting);
   }

   @Override
   public WidgetScreen createScreen(GuiTheme theme) {
      return new ESPBlockDataScreen(theme, this, null, null);
   }

   @Override
   public boolean isChanged() {
      return this.changed;
   }

   public void changed() {
      this.changed = true;
   }

   public void tickRainbow() {
      this.lineColor.update();
      this.sideColor.update();
      this.tracerColor.update();
   }

   public ESPBlockData set(ESPBlockData value) {
      this.shapeMode = value.shapeMode;
      this.lineColor.set((Color)value.lineColor);
      this.sideColor.set((Color)value.sideColor);
      this.tracer = value.tracer;
      this.tracerColor.set((Color)value.tracerColor);
      this.changed = value.changed;
      return this;
   }

   public ESPBlockData copy() {
      return new ESPBlockData(
         this.shapeMode, new SettingColor(this.lineColor), new SettingColor(this.sideColor), this.tracer, new SettingColor(this.tracerColor)
      );
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("shapeMode", this.shapeMode.name());
      tag.put("lineColor", this.lineColor.toTag());
      tag.put("sideColor", this.sideColor.toTag());
      tag.putBoolean("tracer", this.tracer);
      tag.put("tracerColor", this.tracerColor.toTag());
      tag.putBoolean("changed", this.changed);
      return tag;
   }

   public ESPBlockData fromTag(CompoundTag tag) {
      this.shapeMode = ShapeMode.valueOf(tag.getString("shapeMode"));
      this.lineColor.fromTag(tag.getCompound("lineColor"));
      this.sideColor.fromTag(tag.getCompound("sideColor"));
      this.tracer = tag.getBoolean("tracer");
      this.tracerColor.fromTag(tag.getCompound("tracerColor"));
      this.changed = tag.getBoolean("changed");
      return this;
   }
}
