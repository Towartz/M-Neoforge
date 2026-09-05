package meteordevelopment.meteorclient.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.nukateam.ntgl.client.handlers.ClientPlayHandler", remap = false)
public class NtglClientPlayHandlerMixin {
    @Inject(method = "handleUpdateProjectile", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onHandleUpdateProjectile(@Coerce Object message, CallbackInfo ci) {
        try {
            Class<?> managerClass = Class.forName("com.nukateam.ntgl.modules.datapack.managers.NetworkProjectileManager");
            Object manager = managerClass.getMethod("get").invoke(null);
            if (manager == null) {
                ci.cancel();
            }
        } catch (Throwable t) {
            ci.cancel();
        }
    }
}
