package meteordevelopment.meteorclient.mixin;

import java.util.UUID;
import net.minecraft.client.resources.DefaultPlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({DefaultPlayerSkin.class})
public abstract class DefaultSkinHelperMixin {
   @Inject(
      method = {"getSkinTextures(Ljava/util/UUID;)Lnet/minecraft/client/util/SkinTextures;"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void onShouldUseSlimModel(UUID uuid, CallbackInfoReturnable<Boolean> info) {
      if (uuid == null) {
         info.setReturnValue(false);
      }
   }
}
