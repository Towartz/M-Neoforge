package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.mixininterface.IText;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;

@Mixin({Component.class})
public interface TextMixin extends IText {
   @Override
   default void meteor$invalidateCache() {
   }
}
