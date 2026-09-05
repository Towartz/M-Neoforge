package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Xray;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ItemBlockRenderTypes.class})
public abstract class RenderLayersMixin {
   @Inject(
      method = {"getBlockLayer"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void onGetBlockLayer(BlockState state, CallbackInfoReturnable<RenderType> info) {
      if (Modules.get() != null) {
         int alpha = Xray.getAlpha(state, null);
         if (alpha > 0 && alpha < 255) {
            info.setReturnValue(RenderType.translucent());
         }
      }
   }
}
