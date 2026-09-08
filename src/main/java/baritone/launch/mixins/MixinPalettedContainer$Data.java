package baritone.launch.mixins;

import baritone.utils.accessor.IPalettedContainer;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.chunk.Palette;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(
   targets = {"net/minecraft/world/level/chunk/PalettedContainer$Data"}
)
public abstract class MixinPalettedContainer$Data<T> implements IPalettedContainer.IData<T> {
   @Accessor
   @Override
   public abstract Palette<T> getPalette();

   @Accessor
   @Override
   public abstract BitStorage getStorage();
}
