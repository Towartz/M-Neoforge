package meteordevelopment.meteorclient.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({MobEffectInstance.class})
public interface StatusEffectInstanceAccessor {
   @Accessor("duration")
   void setDuration(int var1);

   @Accessor("amplifier")
   void setAmplifier(int var1);
}
