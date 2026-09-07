package meteordevelopment.meteorclient.mixin.baritone;

import baritone.api.command.argument.IArgConsumer;
import baritone.command.defaults.SurfaceCommand;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.GotoSurface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SurfaceCommand.class, remap = false)
public abstract class SurfaceCommandMixin {
   @Inject(method = "execute", at = @At("HEAD"), cancellable = true)
   private void onExecute(String label, IArgConsumer args, CallbackInfo ci) {
      GotoSurface module = Modules.get().get(GotoSurface.class);
      if (module != null) {
         module.clearTarget();
         if (!module.isActive()) {
            module.toggle();
         }
         ci.cancel();
      }
   }
}
