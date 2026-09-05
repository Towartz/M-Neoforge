package meteordevelopment.meteorclient.gui.utils;

import meteordevelopment.meteorclient.utils.misc.ISerializable;
import net.minecraft.nbt.CompoundTag;

public class WindowConfig implements ISerializable<WindowConfig> {
   public boolean expanded = true;
   public double x = -1.0;
   public double y = -1.0;

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putBoolean("expanded", this.expanded);
      tag.putDouble("x", this.x);
      tag.putDouble("y", this.y);
      return tag;
   }

   public WindowConfig fromTag(CompoundTag tag) {
      this.expanded = tag.getBoolean("expanded");
      this.x = tag.getDouble("x");
      this.y = tag.getDouble("y");
      return this;
   }
}
