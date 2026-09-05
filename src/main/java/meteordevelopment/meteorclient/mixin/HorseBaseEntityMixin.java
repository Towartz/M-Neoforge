package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.mixininterface.IHorseBaseEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin({AbstractHorse.class})
public abstract class HorseBaseEntityMixin implements IHorseBaseEntity {
   @Shadow
   protected abstract void setFlag(int var1, boolean var2);

   @Override
   public void setSaddled(boolean saddled) {
      this.setFlag(4, saddled);
   }
}
