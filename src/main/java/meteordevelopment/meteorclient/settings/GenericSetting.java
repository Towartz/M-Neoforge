package meteordevelopment.meteorclient.settings;

import java.util.function.Consumer;
import meteordevelopment.meteorclient.gui.utils.IScreenFactory;
import meteordevelopment.meteorclient.utils.misc.ICopyable;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import net.minecraft.nbt.CompoundTag;

public class GenericSetting<T extends ICopyable<T> & ISerializable<T> & IScreenFactory> extends Setting<T> {
   public GenericSetting(String name, String description, T defaultValue, Consumer<T> onChanged, Consumer<Setting<T>> onModuleActivated, IVisible visible) {
      super(name, description, defaultValue, onChanged, onModuleActivated, visible);
   }

   @Override
   public void resetImpl() {
      if (this.value == null) {
         this.value = this.defaultValue.copy();
      }

      this.value.set(this.defaultValue);
   }

   protected T parseImpl(String str) {
      return this.defaultValue.copy();
   }

   protected boolean isValueValid(T value) {
      return true;
   }

   @Override
   public CompoundTag save(CompoundTag tag) {
      tag.put("value", this.get().toTag());
      return tag;
   }

   public T load(CompoundTag tag) {
      this.get().fromTag(tag.getCompound("value"));
      return this.get();
   }

   public static class Builder<T extends ICopyable<T> & ISerializable<T> & IScreenFactory>
      extends Setting.SettingBuilder<GenericSetting.Builder<T>, T, GenericSetting<T>> {
      public Builder() {
         super(null);
      }

      public GenericSetting<T> build() {
         return new GenericSetting<>(this.name, this.description, this.defaultValue, this.onChanged, this.onModuleActivated, this.visible);
      }
   }
}
