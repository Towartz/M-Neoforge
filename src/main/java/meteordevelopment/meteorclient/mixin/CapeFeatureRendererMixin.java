package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.meteorclient.utils.network.Capes;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({CapeLayer.class})
public abstract class CapeFeatureRendererMixin {
   @ModifyExpressionValue(
      method = {"render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/resources/PlayerSkin;capeTexture()Lnet/minecraft/resources/ResourceLocation;"
      )}
   )
   private ResourceLocation modifyCapeTexture(
      ResourceLocation original,
      PoseStack matrixStack,
      MultiBufferSource vertexConsumerProvider,
      int i,
      AbstractClientPlayer abstractClientPlayerEntity,
      float f,
      float g,
      float h,
      float j,
      float k,
      float l
   ) {
      ResourceLocation id = Capes.get(abstractClientPlayerEntity);
      return id == null ? original : id;
   }
}
