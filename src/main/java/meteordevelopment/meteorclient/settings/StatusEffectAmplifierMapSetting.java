package meteordevelopment.meteorclient.settings;

import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;

public class StatusEffectAmplifierMapSetting extends Setting<Reference2IntMap<MobEffect>> {
   public static final Reference2IntMap<MobEffect> EMPTY_STATUS_EFFECT_MAP = createStatusEffectMap();

   public StatusEffectAmplifierMapSetting(
      String name,
      String description,
      Reference2IntMap<MobEffect> defaultValue,
      Consumer<Reference2IntMap<MobEffect>> onChanged,
      Consumer<Setting<Reference2IntMap<MobEffect>>> onModuleActivated,
      IVisible visible
   ) {
      super(name, description, defaultValue, onChanged, onModuleActivated, visible);
   }

   @Override
   public void resetImpl() {
      this.value = new Reference2IntOpenHashMap(this.defaultValue);
   }

   protected Reference2IntMap<MobEffect> parseImpl(String str) {
      String[] values = str.split(",");
      Reference2IntMap<MobEffect> effects = new Reference2IntOpenHashMap(EMPTY_STATUS_EFFECT_MAP);

      try {
         for (String value : values) {
            String[] split = value.split(" ");
            MobEffect effect = (MobEffect)parseId(BuiltInRegistries.MOB_EFFECT, split[0]);
            int level = Integer.parseInt(split[1]);
            effects.put(effect, level);
         }
      } catch (Exception var11) {
      }

      return effects;
   }

   protected boolean isValueValid(Reference2IntMap<MobEffect> value) {
      return true;
   }

   @Override
   public CompoundTag save(CompoundTag tag) {
      CompoundTag valueTag = new CompoundTag();
      ObjectIterator var3 = this.get().keySet().iterator();

      while (var3.hasNext()) {
         MobEffect statusEffect = (MobEffect)var3.next();
         ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(statusEffect);
         if (id != null) {
            valueTag.putInt(id.toString(), this.get().getInt(statusEffect));
         }
      }

      tag.put("value", valueTag);
      return tag;
   }

   private static Reference2IntMap<MobEffect> createStatusEffectMap() {
      Reference2IntMap<MobEffect> map = new Reference2IntArrayMap(BuiltInRegistries.MOB_EFFECT.keySet().size());
      BuiltInRegistries.MOB_EFFECT.forEach(potion -> map.put(potion, 0));
      return map;
   }

   public Reference2IntMap<MobEffect> load(CompoundTag tag) {
      this.get().clear();
      CompoundTag valueTag = tag.getCompound("value");

      for (String key : valueTag.getAllKeys()) {
         MobEffect statusEffect = (MobEffect)BuiltInRegistries.MOB_EFFECT.get(ResourceLocation.parse(key));
         if (statusEffect != null) {
            this.get().put(statusEffect, valueTag.getInt(key));
         }
      }

      return this.get();
   }

   public static class Builder
      extends Setting.SettingBuilder<StatusEffectAmplifierMapSetting.Builder, Reference2IntMap<MobEffect>, StatusEffectAmplifierMapSetting> {
      public Builder() {
         super(new Reference2IntOpenHashMap(0));
      }

      public StatusEffectAmplifierMapSetting build() {
         return new StatusEffectAmplifierMapSetting(this.name, this.description, this.defaultValue, this.onChanged, this.onModuleActivated, this.visible);
      }
   }
}
