package meteordevelopment.meteorclient.mixin;

import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ResourceLocation.class})
public interface IdentifierAccessor {
   @Mutable
   @Accessor
   void setPath(String var1);
}
