package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.meteorclient.utils.network.Capes;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({ElytraLayer.class})
public abstract class ElytraFeatureRendererMixin<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
   public ElytraFeatureRendererMixin(RenderLayerParent<T, M> context) {
      super(context);
   }

   @ModifyExpressionValue(
      method = {"render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V"},
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
      T livingEntity,
      float f,
      float g,
      float h,
      float j,
      float k,
      float l
   ) {
      if (livingEntity instanceof AbstractClientPlayer playerEntity) {
         ResourceLocation id = Capes.get(playerEntity);
         return id == null ? original : id;
      } else {
         return original;
      }
   }
}
