package meteordevelopment.meteorclient.settings;

import java.util.function.Consumer;
import meteordevelopment.meteorclient.gui.utils.CharFilter;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import net.minecraft.nbt.CompoundTag;

public class StringSetting extends Setting<String> {
   public final Class<? extends WTextBox.Renderer> renderer;
   public final CharFilter filter;
   public final boolean wide;

   public StringSetting(
      String name,
      String description,
      String defaultValue,
      Consumer<String> onChanged,
      Consumer<Setting<String>> onModuleActivated,
      IVisible visible,
      Class<? extends WTextBox.Renderer> renderer,
      CharFilter filter,
      boolean wide
   ) {
      super(name, description, defaultValue, onChanged, onModuleActivated, visible);
      this.renderer = renderer;
      this.filter = filter;
      this.wide = wide;
   }

   protected String parseImpl(String str) {
      return str;
   }

   protected boolean isValueValid(String value) {
      return true;
   }

   @Override
   public CompoundTag save(CompoundTag tag) {
      tag.putString("value", this.get());
      return tag;
   }

   public String load(CompoundTag tag) {
      this.set(tag.getString("value"));
      return this.get();
   }

   public static class Builder extends Setting.SettingBuilder<StringSetting.Builder, String, StringSetting> {
      private Class<? extends WTextBox.Renderer> renderer;
      private CharFilter filter;
      private boolean wide;

      public Builder() {
         super("");
      }

      public StringSetting.Builder renderer(Class<? extends WTextBox.Renderer> renderer) {
         this.renderer = renderer;
         return this;
      }

      public StringSetting.Builder filter(CharFilter filter) {
         this.filter = filter;
         return this;
      }

      public StringSetting.Builder wide() {
         this.wide = true;
         return this;
      }

      public StringSetting build() {
         return new StringSetting(
            this.name, this.description, this.defaultValue, this.onChanged, this.onModuleActivated, this.visible, this.renderer, this.filter, this.wide
         );
      }
   }
}
