package meteordevelopment.meteorclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.RenderBlockEntityEvent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({BlockEntityRenderDispatcher.class})
public abstract class BlockEntityRenderDispatcherMixin {
   @Inject(
      method = {"render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private <E extends BlockEntity> void onRenderEntity(
      E blockEntity, float tickDelta, PoseStack matrix, MultiBufferSource vertexConsumerProvider, CallbackInfo info
   ) {
      RenderBlockEntityEvent event = MeteorClient.EVENT_BUS.post(RenderBlockEntityEvent.get(blockEntity));
      if (event.isCancelled()) {
         info.cancel();
      }
   }
}
