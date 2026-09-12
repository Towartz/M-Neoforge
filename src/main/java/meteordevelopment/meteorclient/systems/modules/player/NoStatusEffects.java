package meteordevelopment.meteorclient.systems.modules.player;

import java.util.List;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StatusEffectListSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;

public class NoStatusEffects extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

   private final Setting<Boolean> badEffects = this.sgGeneral.add(
      new BoolSetting.Builder()
         .name("bad-effects")
         .description("Automatically blocks all harmful/negative potion and status effects.")
         .defaultValue(true)
         .build()
   );

   private final Setting<List<MobEffect>> blockedEffects = this.sgGeneral.add(
      new StatusEffectListSetting.Builder()
         .name("blocked-effects")
         .description("Specific status effects to block.")
         .defaultValue(
            MobEffects.LEVITATION.value(),
            MobEffects.JUMP.value(),
            MobEffects.SLOW_FALLING.value(),
            MobEffects.DOLPHINS_GRACE.value()
         )
         .onChanged(v -> this.updateBlockedSet())
         .build()
   );

   private volatile java.util.Set<MobEffect> blockedSet = java.util.Collections.emptySet();

   public NoStatusEffects() {
      super(Categories.Player, "no-status-effects", "Blocks specified status effects and negative potions.");
   }

   public void updateBlockedSet() {
      List<MobEffect> list = this.blockedEffects.get();
      if (list != null && !list.isEmpty()) {
         this.blockedSet = new it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet<>(list);
      } else {
         this.blockedSet = java.util.Collections.emptySet();
      }
   }

   @Override
   public void onActivate() {
      this.updateBlockedSet();
   }

   public boolean shouldBlock(MobEffect effect) {
      if (!this.isActive() || effect == null) {
         return false;
      }
      if (this.badEffects.get() && effect.getCategory() == MobEffectCategory.HARMFUL) {
         return true;
      }
      return this.blockedSet.contains(effect);
   }
}
