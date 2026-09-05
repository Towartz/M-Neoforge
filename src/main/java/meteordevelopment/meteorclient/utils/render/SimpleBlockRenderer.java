package meteordevelopment.meteorclient.utils.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.mixininterface.IBakedQuad;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class SimpleBlockRenderer {
   private static final PoseStack MATRICES = new PoseStack();
   private static final Direction[] DIRECTIONS = Direction.values();
   private static final RandomSource RANDOM = RandomSource.create();

   private SimpleBlockRenderer() {
   }

   public static void renderWithBlockEntity(BlockEntity blockEntity, float tickDelta, IVertexConsumerProvider vertexConsumerProvider) {
      vertexConsumerProvider.setOffset(blockEntity.getBlockPos().getX(), blockEntity.getBlockPos().getY(), blockEntity.getBlockPos().getZ());
      render(blockEntity.getBlockPos(), blockEntity.getBlockState(), vertexConsumerProvider);
      BlockEntityRenderer<BlockEntity> renderer = MeteorClient.mc.getBlockEntityRenderDispatcher().getRenderer(blockEntity);
      if (renderer != null && blockEntity.hasLevel() && blockEntity.getType().isValid(blockEntity.getBlockState())) {
         renderer.render(blockEntity, tickDelta, MATRICES, vertexConsumerProvider, 15728880, OverlayTexture.NO_OVERLAY);
      }

      vertexConsumerProvider.setOffset(0, 0, 0);
   }

   public static void render(BlockPos pos, BlockState state, MultiBufferSource consumerProvider) {
      if (state.getRenderShape() == RenderShape.MODEL) {
         VertexConsumer consumer = consumerProvider.getBuffer(RenderType.solid());
         BakedModel model = MeteorClient.mc.getBlockRenderer().getBlockModel(state);
         Vec3 offset = state.getOffset(MeteorClient.mc.level, pos);
         float offsetX = (float)offset.x;
         float offsetY = (float)offset.y;
         float offsetZ = (float)offset.z;

         for (Direction direction : DIRECTIONS) {
            List<BakedQuad> list = model.getQuads(state, direction, RANDOM);
            if (!list.isEmpty()) {
               renderQuads(list, offsetX, offsetY, offsetZ, consumer);
            }
         }

         List<BakedQuad> list = model.getQuads(state, null, RANDOM);
         if (!list.isEmpty()) {
            renderQuads(list, offsetX, offsetY, offsetZ, consumer);
         }
      }
   }

   private static void renderQuads(List<BakedQuad> quads, float offsetX, float offsetY, float offsetZ, VertexConsumer consumer) {
      for (BakedQuad bakedQuad : quads) {
         IBakedQuad quad = (IBakedQuad)bakedQuad;

         for (int j = 0; j < 4; j++) {
            float x = quad.meteor$getX(j);
            float y = quad.meteor$getY(j);
            float z = quad.meteor$getZ(j);
            consumer.addVertex(offsetX + x, offsetY + y, offsetZ + z);
         }
      }
   }
}
