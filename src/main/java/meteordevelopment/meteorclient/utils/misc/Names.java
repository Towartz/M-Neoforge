package meteordevelopment.meteorclient.utils.misc;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.ResourcePacksReloadedEvent;
import meteordevelopment.meteorclient.utils.PreInit;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess.Frozen;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringUtil;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import org.apache.commons.lang3.StringUtils;

public class Names {
   private static final Map<MobEffect, String> statusEffectNames = new Reference2ObjectOpenHashMap(16);
   private static final Map<Item, String> itemNames = new Reference2ObjectOpenHashMap(128);
   private static final Map<Block, String> blockNames = new Reference2ObjectOpenHashMap(128);
   private static final Map<ResourceKey<Enchantment>, String> enchantmentKeyNames = new WeakHashMap<>(16);
   private static final Map<Holder<Enchantment>, String> enchantmentEntryNames = new Reference2ObjectOpenHashMap(16);
   private static final Map<EntityType<?>, String> entityTypeNames = new Reference2ObjectOpenHashMap(64);
   private static final Map<ParticleType<?>, String> particleTypesNames = new Reference2ObjectOpenHashMap(64);
   private static final Map<ResourceLocation, String> soundNames = new HashMap<>(64);

   private Names() {
   }

   @PreInit
   public static void init() {
      MeteorClient.EVENT_BUS.subscribe(Names.class);
   }

   @EventHandler
   private static void onResourcePacksReloaded(ResourcePacksReloadedEvent event) {
      statusEffectNames.clear();
      itemNames.clear();
      blockNames.clear();
      enchantmentEntryNames.clear();
      entityTypeNames.clear();
      particleTypesNames.clear();
      soundNames.clear();
   }

   public static String get(MobEffect effect) {
      return statusEffectNames.computeIfAbsent(effect, effect1 -> StringUtil.stripColor(I18n.get(effect1.getDescriptionId(), new Object[0])));
   }

   public static String get(Item item) {
      return itemNames.computeIfAbsent(item, item1 -> StringUtil.stripColor(I18n.get(item1.getDescriptionId(), new Object[0])));
   }

   public static String get(Block block) {
      return blockNames.computeIfAbsent(block, block1 -> StringUtil.stripColor(I18n.get(block1.getDescriptionId(), new Object[0])));
   }

   public static String get(ResourceKey<Enchantment> enchantment) {
      return enchantmentKeyNames.computeIfAbsent(
         enchantment,
         enchantment1 -> Optional.ofNullable(Minecraft.getInstance().getConnection())
               .<Frozen>map(ClientPacketListener::registryAccess)
               .flatMap(registryManager -> registryManager.registry(Registries.ENCHANTMENT))
               .flatMap(registry -> registry.getHolder(enchantment))
               .map(Names::get)
               .orElseGet(() -> {
                  String key = "enchantment." + enchantment1.location().toLanguageKey();
                  String translated = I18n.get(key, new Object[0]);
                  return translated == key ? enchantment1.location().toString() : translated;
               })
      );
   }

   public static String get(Holder<Enchantment> enchantment) {
      return enchantmentEntryNames.computeIfAbsent(
         enchantment, enchantment1 -> StringUtil.stripColor(((Enchantment)enchantment.value()).description().getString())
      );
   }

   public static String get(EntityType<?> entityType) {
      return entityTypeNames.computeIfAbsent(entityType, entityType1 -> StringUtil.stripColor(I18n.get(entityType1.getDescriptionId(), new Object[0])));
   }

   public static String get(ParticleType<?> type) {
      return !(type instanceof ParticleOptions)
         ? ""
         : particleTypesNames.computeIfAbsent(type, effect1 -> StringUtils.capitalize(BuiltInRegistries.PARTICLE_TYPE.getKey(type).getPath().replace("_", " ")));
   }

   public static String getSoundName(ResourceLocation id) {
      return soundNames.computeIfAbsent(id, identifier -> {
         WeighedSoundEvents soundSet = MeteorClient.mc.getSoundManager().getSoundEvent(identifier);
         if (soundSet == null) {
            return identifier.getPath();
         } else {
            Component text = soundSet.getSubtitle();
            return text == null ? identifier.getPath() : StringUtil.stripColor(text.getString());
         }
      });
   }

   public static String get(ItemStack stack) {
      return stack.getHoverName().getString();
   }
}
