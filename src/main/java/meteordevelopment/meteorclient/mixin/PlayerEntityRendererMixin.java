package meteordevelopment.meteorclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Chams;
import meteordevelopment.meteorclient.systems.modules.render.HandView;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin({PlayerRenderer.class})
public abstract class PlayerEntityRendererMixin {
   @ModifyArgs(
      method = {"renderHand"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V",
         ordinal = 0
      )
   )
   private void modifyRenderLayer(
      Args args, PoseStack matrices, MultiBufferSource vertexConsumers, int light, AbstractClientPlayer player, ModelPart arm, ModelPart sleeve
   ) {
      Chams chams = Modules.get().get(Chams.class);
      if (chams.isActive() && chams.hand.get()) {
         ResourceLocation texture = chams.handTexture.get() ? player.getSkin().texture() : Chams.BLANK;
         args.set(1, vertexConsumers.getBuffer(RenderType.entityTranslucent(texture)));
      }
   }

   @Redirect(
      method = {"renderHand"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V",
         ordinal = 0
      )
   )
   private void redirectRenderMain(ModelPart modelPart, PoseStack matrices, VertexConsumer vertices, int light, int overlay) {
      Chams chams = Modules.get().get(Chams.class);
      if (chams.isActive() && chams.hand.get()) {
         Color color = chams.handColor.get();
         modelPart.render(matrices, vertices, light, overlay, color.getPacked());
      } else {
         modelPart.render(matrices, vertices, light, overlay);
      }
   }

   @Redirect(
      method = {"renderHand"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V",
         ordinal = 1
      )
   )
   private void redirectRenderSleeve(ModelPart modelPart, PoseStack matrices, VertexConsumer vertices, int light, int overlay) {
      Chams chams = Modules.get().get(Chams.class);
      if (!Modules.get().isActive(HandView.class)) {
         if (chams.isActive() && chams.hand.get()) {
            Color color = chams.handColor.get();
            modelPart.render(matrices, vertices, light, overlay, color.getPacked());
         } else {
            modelPart.render(matrices, vertices, light, overlay);
         }
      }
   }
}
