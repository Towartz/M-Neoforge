package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.NoRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(
   targets = {"net/minecraft/client/gui/Font$StringRenderOutput"}
)
public abstract class TextRendererMixin {
   @ModifyExpressionValue(
      method = {"accept(ILnet/minecraft/network/chat/Style;I)Z", "accept"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/chat/Style;isObfuscated()Z"
      )}
   )
   private boolean onRenderObfuscatedStyle(boolean original) {
      return Modules.get() != null && Modules.get().get(NoRender.class) != null ? !Modules.get().get(NoRender.class).noObfuscation() && original : original;
   }
}
