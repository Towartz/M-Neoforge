package meteordevelopment.meteorclient.mixin;

import java.util.Map;
import java.util.Objects;
import meteordevelopment.meteorclient.MeteorClient;
import net.neoforged.neoforge.client.extensions.common.ClientExtensionsManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientExtensionsManager.class, remap = false)
public class ClientExtensionsManagerMixin {
    @Inject(method = "register", at = @At("HEAD"), cancellable = true)
    private static <T, E> void onRegister(E extensions, Map<T, E> target, T[] objects, CallbackInfo ci) {
        if (objects.length == 0) {
            throw new IllegalArgumentException("At least one target must be provided");
        }
        Objects.requireNonNull(extensions, "Extensions must not be null");

        for (T object : objects) {
            Objects.requireNonNull(object, "Target must not be null");
            E oldExtensions = target.put(object, extensions);
            if (oldExtensions != null) {
                MeteorClient.LOG.warn("Duplicate client extensions registration for {} (old: {}, new: {}) - safely ignoring to prevent crash",
                    object, oldExtensions, extensions);
            }
        }
        ci.cancel();
    }
}
