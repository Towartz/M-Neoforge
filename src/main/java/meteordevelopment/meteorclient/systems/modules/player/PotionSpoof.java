package meteordevelopment.meteorclient.systems.modules.player;

import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap.Entry;
import java.util.List;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.StatusEffectInstanceAccessor;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StatusEffectAmplifierMapSetting;
import meteordevelopment.meteorclient.settings.StatusEffectListSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class PotionSpoof extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Reference2IntMap<MobEffect>> spoofPotions = this.sgGeneral
      .add(
         new StatusEffectAmplifierMapSetting.Builder()
            .name("spoofed-potions")
            .description("Potions to add.")
            .defaultValue(Utils.createStatusEffectMap())
            .build()
      );
   private final Setting<Boolean> clearEffects = this.sgGeneral
      .add(new BoolSetting.Builder().name("clear-effects").description("Clears effects on module disable.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<List<MobEffect>> antiPotion = this.sgGeneral
      .add(
         new StatusEffectListSetting.Builder()
            .name("blocked-potions")
            .description("Potions to block.")
            .defaultValue(
               (MobEffect)MobEffects.LEVITATION.value(),
               (MobEffect)MobEffects.JUMP.value(),
               (MobEffect)MobEffects.SLOW_FALLING.value(),
               (MobEffect)MobEffects.DOLPHINS_GRACE.value()
            )
            .build()
      );
   private final Setting<Integer> effectDuration = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("effect-duration")
            .description("How many ticks to spoof the effect for.")
            .range(1, 32767)
            .sliderRange(20, 500)
            .defaultValue(Integer.valueOf(420))
            .build()
      );

   public PotionSpoof() {
      super(Categories.Player, "potion-spoof", "Spoofs potion statuses for you. SOME effects DO NOT work.");
   }

   @Override
   public void onDeactivate() {
      if (this.clearEffects.get() && Utils.canUpdate()) {
         ObjectIterator var1 = this.spoofPotions.get().reference2IntEntrySet().iterator();

         while (var1.hasNext()) {
            Entry<MobEffect> entry = (Entry<MobEffect>)var1.next();
            if (entry.getIntValue() > 0 && this.mc.player.hasEffect(BuiltInRegistries.MOB_EFFECT.wrapAsHolder((MobEffect)entry.getKey()))) {
               this.mc.player.removeEffect(BuiltInRegistries.MOB_EFFECT.wrapAsHolder((MobEffect)entry.getKey()));
            }
         }
      }
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      ObjectIterator var2 = this.spoofPotions.get().reference2IntEntrySet().iterator();

      while (var2.hasNext()) {
         Entry<MobEffect> entry = (Entry<MobEffect>)var2.next();
         int level = entry.getIntValue();
         if (level > 0) {
            if (this.mc.player.hasEffect(BuiltInRegistries.MOB_EFFECT.wrapAsHolder((MobEffect)entry.getKey()))) {
               MobEffectInstance instance = this.mc.player.getEffect(BuiltInRegistries.MOB_EFFECT.wrapAsHolder((MobEffect)entry.getKey()));
               ((StatusEffectInstanceAccessor)instance).setAmplifier(level - 1);
               if (instance.getDuration() < this.effectDuration.get()) {
                  ((StatusEffectInstanceAccessor)instance).setDuration(this.effectDuration.get());
               }
            } else {
               this.mc
                  .player
                  .addEffect(new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder((MobEffect)entry.getKey()), this.effectDuration.get(), level - 1));
            }
         }
      }
   }

   public boolean shouldBlock(MobEffect effect) {
      return this.isActive() && this.antiPotion.get().contains(effect);
   }
}
