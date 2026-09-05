package meteordevelopment.meteorclient.events.render;

import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.meteorclient.events.Cancellable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;

public class RenderItemEntityEvent extends Cancellable {
   private static final RenderItemEntityEvent INSTANCE = new RenderItemEntityEvent();
   public ItemEntity itemEntity;
   public float f;
   public float tickDelta;
   public PoseStack matrixStack;
   public MultiBufferSource vertexConsumerProvider;
   public int light;
   public RandomSource random;
   public ItemRenderer itemRenderer;

   public static RenderItemEntityEvent get(
      ItemEntity itemEntity,
      float f,
      float tickDelta,
      PoseStack matrixStack,
      MultiBufferSource vertexConsumerProvider,
      int light,
      RandomSource random,
      ItemRenderer itemRenderer
   ) {
      INSTANCE.setCancelled(false);
      INSTANCE.itemEntity = itemEntity;
      INSTANCE.f = f;
      INSTANCE.tickDelta = tickDelta;
      INSTANCE.matrixStack = matrixStack;
      INSTANCE.vertexConsumerProvider = vertexConsumerProvider;
      INSTANCE.light = light;
      INSTANCE.random = random;
      INSTANCE.itemRenderer = itemRenderer;
      return INSTANCE;
   }
}
