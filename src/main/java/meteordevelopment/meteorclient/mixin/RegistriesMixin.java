package meteordevelopment.meteorclient.mixin;

import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({BuiltInRegistries.class})
public abstract class RegistriesMixin {
   @Redirect(
      method = {"create(Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/registry/MutableRegistry;Lnet/minecraft/registry/Registries$Initializer;)Lnet/minecraft/registry/MutableRegistry;"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/Bootstrap;ensureBootstrapped(Ljava/util/function/Supplier;)V"
      )
   )
   private static void ignoreBootstrap(Supplier<String> callerGetter) {
   }
}
