package meteordevelopment.meteorclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import meteordevelopment.meteorclient.systems.modules.render.Xray;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ModelBlockRenderer.class})
public abstract class BlockModelRendererMixin {
   @Unique
   private final ThreadLocal<Integer> alphas = new ThreadLocal<>();

   @Inject(
      method = {"renderSmooth", "renderFlat"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onRenderSmooth(
      BlockAndTintGetter world,
      BakedModel model,
      BlockState state,
      BlockPos pos,
      PoseStack matrices,
      VertexConsumer vertexConsumer,
      boolean cull,
      RandomSource random,
      long seed,
      int overlay,
      CallbackInfo info
   ) {
      int alpha = Xray.getAlpha(state, pos);
      if (alpha == 0) {
         info.cancel();
      } else {
         this.alphas.set(alpha);
      }
   }

   @ModifyConstant(
      method = {"renderQuad"},
      constant = {@Constant(
         floatValue = 1.0F,
         ordinal = 3
      )}
   )
   private float renderQuad_modifyAlpha(float original) {
      int alpha = this.alphas.get();
      return alpha == -1 ? original : (float)alpha / 255.0F;
   }
}
