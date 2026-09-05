package meteordevelopment.meteorclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.NoRender;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.TransparentBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin({ItemRenderer.class})
public abstract class ItemRendererMixin {
   @ModifyArgs(
      method = {"renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/item/ItemRenderer;renderBakedItemModel(Lnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/item/ItemStack;IILnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;)V"
      )
   )
   private void modifyEnchant(
      Args args,
      ItemStack stack,
      ItemDisplayContext renderMode,
      boolean leftHanded,
      PoseStack matrices,
      MultiBufferSource vertexConsumers,
      int light,
      int overlay,
      BakedModel model
   ) {
      if (Modules.get().get(NoRender.class).noEnchantGlint()) {
         boolean bl = renderMode == ItemDisplayContext.GUI
            || renderMode.firstPerson()
            || !(stack.getItem() instanceof BlockItem blockItem)
            || !(blockItem.getBlock() instanceof TransparentBlock) && !(blockItem.getBlock() instanceof StainedGlassPaneBlock);
         args.set(5, vertexConsumers.getBuffer(ItemBlockRenderTypes.getRenderType(stack, bl)));
      }
   }
}
