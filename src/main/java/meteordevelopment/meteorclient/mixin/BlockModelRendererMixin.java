package meteordevelopment.meteorclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import meteordevelopment.meteorclient.systems.modules.render.Xray;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
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
      method = "tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V",
      at = @At("HEAD"),
      cancellable = true,
      require = 0
   )
   private void onTesselateBlock(
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
      ModelData modelData,
      RenderType renderType,
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
      method = "putQuadData",
      constant = @Constant(
         floatValue = 1.0F,
         ordinal = 3
      ),
      require = 0
   )
   private float putQuadData_modifyAlpha(float original) {
      Integer alpha = this.alphas.get();
      return (alpha == null || alpha == -1) ? original : (float)alpha / 255.0F;
   }
}
