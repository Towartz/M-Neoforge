package meteordevelopment.meteorclient.mixin;

import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffect.AttributeTemplate;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({MobEffect.class})
public interface StatusEffectAccessor {
   @Accessor
   Map<Holder<Attribute>, AttributeTemplate> getAttributeModifiers();
}
