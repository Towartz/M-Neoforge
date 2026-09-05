package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.NoRender;
import net.minecraft.world.level.lighting.SkyLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({SkyLightEngine.class})
public abstract class ChunkSkyLightProviderMixin {
   @Inject(
      at = {@At("HEAD")},
      method = {"propagateIncrease(JJI)V"},
      cancellable = true
   )
   private void recalculateLevel(long blockPos, long l, int lightLevel, CallbackInfo ci) {
      if (Modules.get().get(NoRender.class).noSkylightUpdates()) {
         ci.cancel();
      }
   }
}
