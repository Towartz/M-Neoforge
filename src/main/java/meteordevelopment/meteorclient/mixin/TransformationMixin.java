package meteordevelopment.meteorclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.ApplyTransformationEvent;
import net.minecraft.client.renderer.block.model.ItemTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ItemTransform.class})
public abstract class TransformationMixin {
   @Inject(
      method = {"apply"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onApply(boolean leftHanded, PoseStack matrices, CallbackInfo info) {
      ApplyTransformationEvent event = MeteorClient.EVENT_BUS.post(ApplyTransformationEvent.get((ItemTransform)(Object)this, leftHanded, matrices));
      if (event.isCancelled()) {
         info.cancel();
      }
   }
}
