package meteordevelopment.meteorclient.mixin;

import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({AbstractArrow.class})
public interface ProjectileInGroundAccessor {
   @Accessor("inGround")
   boolean getInGround();
}
