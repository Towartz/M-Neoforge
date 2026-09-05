package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.DeltaTracker.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Timer.class})
public abstract class RenderTickCounterDynamicMixin {
   @Shadow
   private float deltaTicks;

   @Inject(
      method = {"beginRenderTick(J)I"},
      at = {@At(
         value = "FIELD",
         target = "Lnet/minecraft/client/render/RenderTickCounter$Dynamic;prevTimeMillis:J",
         opcode = 181
      )}
   )
   private void onBeingRenderTick(long a, CallbackInfoReturnable<Integer> info) {
      this.deltaTicks = this.deltaTicks * (float)Modules.get().get(meteordevelopment.meteorclient.systems.modules.world.Timer.class).getMultiplier();
   }
}
