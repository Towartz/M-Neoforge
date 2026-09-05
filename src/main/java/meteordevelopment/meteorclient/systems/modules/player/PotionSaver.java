package meteordevelopment.meteorclient.systems.modules.player;

import java.util.List;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StatusEffectListSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

public class PotionSaver extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<List<MobEffect>> effects = this.sgGeneral
      .add(
         new StatusEffectListSetting.Builder()
            .name("effects")
            .description("The effects to preserve.")
            .defaultValue(
               (MobEffect)MobEffects.DAMAGE_BOOST.value(),
               (MobEffect)MobEffects.ABSORPTION.value(),
               (MobEffect)MobEffects.DAMAGE_RESISTANCE.value(),
               (MobEffect)MobEffects.FIRE_RESISTANCE.value(),
               (MobEffect)MobEffects.MOVEMENT_SPEED.value(),
               (MobEffect)MobEffects.DIG_SPEED.value(),
               (MobEffect)MobEffects.REGENERATION.value(),
               (MobEffect)MobEffects.WATER_BREATHING.value(),
               (MobEffect)MobEffects.SATURATION.value(),
               (MobEffect)MobEffects.LUCK.value(),
               (MobEffect)MobEffects.SLOW_FALLING.value(),
               (MobEffect)MobEffects.DOLPHINS_GRACE.value(),
               (MobEffect)MobEffects.CONDUIT_POWER.value(),
               (MobEffect)MobEffects.HERO_OF_THE_VILLAGE.value()
            )
            .build()
      );
   public final Setting<Boolean> onlyWhenStationary = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("only-when-stationary")
            .description("Only freezes effects when you aren't moving.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );

   public PotionSaver() {
      super(Categories.Player, "potion-saver", "Stops potion effects ticking when you stand still.");
   }

   public boolean shouldFreeze(MobEffect effect) {
      return this.isActive()
         && (!this.onlyWhenStationary.get() || !PlayerUtils.isMoving())
         && !this.mc.player.getActiveEffects().isEmpty()
         && this.effects.get().contains(effect);
   }
}
