package meteordevelopment.meteorclient.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public class SoundEventListSetting extends Setting<List<SoundEvent>> {
   public SoundEventListSetting(
      String name,
      String description,
      List<SoundEvent> defaultValue,
      Consumer<List<SoundEvent>> onChanged,
      Consumer<Setting<List<SoundEvent>>> onModuleActivated,
      IVisible visible
   ) {
      super(name, description, defaultValue, onChanged, onModuleActivated, visible);
   }

   @Override
   public void resetImpl() {
      this.value = new ArrayList<>(this.defaultValue);
   }

   protected List<SoundEvent> parseImpl(String str) {
      String[] values = str.split(",");
      List<SoundEvent> sounds = new ArrayList<>(values.length);

      try {
         for (String value : values) {
            SoundEvent sound = (SoundEvent)parseId(BuiltInRegistries.SOUND_EVENT, value);
            if (sound != null) {
               sounds.add(sound);
            }
         }
      } catch (Exception var9) {
      }

      return sounds;
   }

   protected boolean isValueValid(List<SoundEvent> value) {
      return true;
   }

   @Override
   public Iterable<ResourceLocation> getIdentifierSuggestions() {
      return BuiltInRegistries.SOUND_EVENT.keySet();
   }

   @Override
   public CompoundTag save(CompoundTag tag) {
      ListTag valueTag = new ListTag();

      for (SoundEvent sound : this.get()) {
         ResourceLocation id = BuiltInRegistries.SOUND_EVENT.getKey(sound);
         if (id != null) {
            valueTag.add(StringTag.valueOf(id.toString()));
         }
      }

      tag.put("value", valueTag);
      return tag;
   }

   public List<SoundEvent> load(CompoundTag tag) {
      this.get().clear();

      for (Tag tagI : tag.getList("value", 8)) {
         SoundEvent soundEvent = (SoundEvent)BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(tagI.getAsString()));
         if (soundEvent != null) {
            this.get().add(soundEvent);
         }
      }

      return this.get();
   }

   public static class Builder extends Setting.SettingBuilder<SoundEventListSetting.Builder, List<SoundEvent>, SoundEventListSetting> {
      public Builder() {
         super(new ArrayList<>(0));
      }

      public SoundEventListSetting.Builder defaultValue(SoundEvent... defaults) {
         return this.defaultValue((List<SoundEvent>)(defaults != null ? Arrays.asList(defaults) : new ArrayList<>()));
      }

      public SoundEventListSetting build() {
         return new SoundEventListSetting(this.name, this.description, this.defaultValue, this.onChanged, this.onModuleActivated, this.visible);
      }
   }
}
