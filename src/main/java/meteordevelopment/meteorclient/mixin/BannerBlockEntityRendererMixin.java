package meteordevelopment.meteorclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.NoRender;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({BannerRenderer.class})
public abstract class BannerBlockEntityRendererMixin {
   @Final
   @Shadow
   private ModelPart pole;
   @Final
   @Shadow
   private ModelPart bar;

   @Inject(
      method = {"render(Lnet/minecraft/block/entity/BannerBlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void render(
      BannerBlockEntity bannerBlockEntity, float f, PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, int i, int j, CallbackInfo ci
   ) {
      if (bannerBlockEntity.getLevel() != null) {
         NoRender.BannerRenderMode renderMode = Modules.get().get(NoRender.class).getBannerRenderMode();
         if (renderMode == NoRender.BannerRenderMode.None) {
            ci.cancel();
         } else if (renderMode == NoRender.BannerRenderMode.Pillar) {
            BlockState blockState = bannerBlockEntity.getBlockState();
            if (blockState.getBlock() instanceof BannerBlock) {
               this.pole.visible = true;
               this.bar.visible = false;
               this.renderPillar(bannerBlockEntity, matrixStack, vertexConsumerProvider, i, j);
            } else {
               this.pole.visible = false;
               this.bar.visible = true;
               this.renderCrossbar(bannerBlockEntity, matrixStack, vertexConsumerProvider, i, j);
            }

            ci.cancel();
         }
      }
   }

   @Unique
   private void renderPillar(BannerBlockEntity bannerBlockEntity, PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, int i, int j) {
      matrixStack.pushPose();
      BlockState blockState = bannerBlockEntity.getBlockState();
      matrixStack.translate(0.5, 0.5, 0.5);
      float h = (float)(-(Integer)blockState.getValue(BannerBlock.ROTATION) * 360) / 16.0F;
      matrixStack.mulPose(Axis.YP.rotationDegrees(h));
      matrixStack.pushPose();
      matrixStack.scale(0.6666667F, -0.6666667F, -0.6666667F);
      VertexConsumer vertexConsumer = ModelBakery.BANNER_BASE.buffer(vertexConsumerProvider, RenderType::entitySolid);
      this.pole.render(matrixStack, vertexConsumer, i, j);
      matrixStack.popPose();
      matrixStack.popPose();
   }

   @Unique
   private void renderCrossbar(BannerBlockEntity bannerBlockEntity, PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, int i, int j) {
      matrixStack.pushPose();
      BlockState blockState = bannerBlockEntity.getBlockState();
      matrixStack.translate(0.5, -0.16666667F, 0.5);
      float h = -((Direction)blockState.getValue(WallBannerBlock.FACING)).toYRot();
      matrixStack.mulPose(Axis.YP.rotationDegrees(h));
      matrixStack.translate(0.0, -0.3125, -0.4375);
      matrixStack.pushPose();
      matrixStack.scale(0.6666667F, -0.6666667F, -0.6666667F);
      VertexConsumer vertexConsumer = ModelBakery.BANNER_BASE.buffer(vertexConsumerProvider, RenderType::entitySolid);
      this.bar.render(matrixStack, vertexConsumer, i, j);
      matrixStack.popPose();
      matrixStack.popPose();
   }
}
