package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.PotionSaver;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({MobEffectInstance.class})
public abstract class StatusEffectInstanceMixin {
   @Shadow
   private int duration;

   @Inject(
      method = {"updateDuration"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void tick(CallbackInfoReturnable<Integer> info) {
      if (Utils.canUpdate()) {
         if (Modules.get().get(PotionSaver.class).shouldFreeze((MobEffect)((MobEffectInstance)(Object)this).getEffect().value())) {
            info.setReturnValue(this.duration);
         }
      }
   }
}
