package meteordevelopment.meteorclient.settings;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.lang.reflect.AccessFlag;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

public class EnchantmentListSetting extends Setting<Set<ResourceKey<Enchantment>>> {
   public EnchantmentListSetting(
      String name,
      String description,
      Set<ResourceKey<Enchantment>> defaultValue,
      Consumer<Set<ResourceKey<Enchantment>>> onChanged,
      Consumer<Setting<Set<ResourceKey<Enchantment>>>> onModuleActivated,
      IVisible visible
   ) {
      super(name, description, defaultValue, onChanged, onModuleActivated, visible);
   }

   @Override
   public void resetImpl() {
      this.value = new ObjectOpenHashSet(this.defaultValue);
   }

   protected Set<ResourceKey<Enchantment>> parseImpl(String str) {
      String[] values = str.split(",");
      Set<ResourceKey<Enchantment>> enchs = new ObjectOpenHashSet(values.length);

      for (String value : values) {
         String name = value.trim();
         ResourceLocation id;
         if (name.contains(":")) {
            id = ResourceLocation.parse(name);
         } else {
            id = ResourceLocation.withDefaultNamespace(name);
         }

         enchs.add(ResourceKey.create(Registries.ENCHANTMENT, id));
      }

      return enchs;
   }

   protected boolean isValueValid(Set<ResourceKey<Enchantment>> value) {
      return true;
   }

   @Override
   public Iterable<ResourceLocation> getIdentifierSuggestions() {
      return Optional.ofNullable(Minecraft.getInstance().getConnection())
         .flatMap(networkHandler -> networkHandler.registryAccess().registry(Registries.ENCHANTMENT))
         .<Set<ResourceLocation>>map(Registry::keySet)
         .orElse(Set.of());
   }

   @Override
   public CompoundTag save(CompoundTag tag) {
      ListTag valueTag = new ListTag();

      for (ResourceKey<Enchantment> ench : this.get()) {
         valueTag.add(StringTag.valueOf(ench.location().toString()));
      }

      tag.put("value", valueTag);
      return tag;
   }

   public Set<ResourceKey<Enchantment>> load(CompoundTag tag) {
      this.get().clear();

      for (Tag tagI : tag.getList("value", 8)) {
         this.get().add(ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse(tagI.getAsString())));
      }

      return this.get();
   }

   public static class Builder extends Setting.SettingBuilder<EnchantmentListSetting.Builder, Set<ResourceKey<Enchantment>>, EnchantmentListSetting> {
      private static final Set<ResourceKey<Enchantment>> VANILLA_DEFAULTS = Arrays.stream(Enchantments.class.getDeclaredFields())
         .filter(field -> field.accessFlags().containsAll(List.of(AccessFlag.PUBLIC, AccessFlag.STATIC, AccessFlag.FINAL)))
         .filter(field -> field.getType() == ResourceKey.class)
         .map(field -> {
            try {
               return field.get(null);
            } catch (IllegalAccessException var2) {
               return null;
            }
         })
         .filter(Objects::nonNull)
         .<ResourceKey<Enchantment>>map(f -> (ResourceKey<Enchantment>) f)
         .filter(registryKey -> registryKey.isFor(Registries.ENCHANTMENT))
         .collect(Collectors.toSet());

      public Builder() {
         super(new ObjectOpenHashSet());
      }

      public EnchantmentListSetting.Builder vanillaDefaults() {
         return this.defaultValue(VANILLA_DEFAULTS);
      }

      @SafeVarargs
      public final EnchantmentListSetting.Builder defaultValue(ResourceKey<Enchantment>... defaults) {
         return this.defaultValue(defaults != null ? new ObjectOpenHashSet(defaults) : new ObjectOpenHashSet());
      }

      public EnchantmentListSetting build() {
         return new EnchantmentListSetting(this.name, this.description, this.defaultValue, this.onChanged, this.onModuleActivated, this.visible);
      }
   }
}
