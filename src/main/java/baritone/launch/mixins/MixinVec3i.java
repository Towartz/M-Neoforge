package baritone.launch.mixins;

import baritone.api.utils.BetterBlockPos;
import net.minecraft.core.Vec3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin({Vec3i.class})
public class MixinVec3i {
   @Overwrite
   @Override
   public int hashCode() {
      Vec3i vec = (Vec3i)(Object)this;
      return (int)BetterBlockPos.longHash(vec.getX(), vec.getY(), vec.getZ());
   }
}
