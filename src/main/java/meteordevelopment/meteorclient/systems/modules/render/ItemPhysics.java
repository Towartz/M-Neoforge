package meteordevelopment.meteorclient.systems.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import meteordevelopment.meteorclient.events.render.ApplyTransformationEvent;
import meteordevelopment.meteorclient.events.render.RenderItemEntityEvent;
import meteordevelopment.meteorclient.mixininterface.IBakedQuad;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ItemPhysics extends Module {
   private static final Direction[] FACES = new Direction[]{
      null, Direction.UP, Direction.DOWN, Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.WEST
   };
   private static final float PIXEL_SIZE = 0.0625F;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> randomRotation = this.sgGeneral
      .add(new BoolSetting.Builder().name("random-rotation").description("Adds a random rotation to every item.").defaultValue(Boolean.valueOf(true)).build());
   private final RandomSource random = RandomSource.createNewThreadLocalInstance();
   private boolean renderingItem;

   public ItemPhysics() {
      super(Categories.Render, "item-physics", "Applies physics to items on the ground.");
   }

   @EventHandler
   private void onRenderItemEntity(RenderItemEntityEvent event) {
      PoseStack matrices = event.matrixStack;
      matrices.pushPose();
      ItemStack itemStack = event.itemEntity.getItem();
      BakedModel model = this.getModel(event.itemEntity);
      ItemPhysics.ModelInfo info = this.getInfo(model);
      this.random.setSeed((long)event.itemEntity.getId() * 2365798L);
      this.applyTransformation(matrices, model);
      matrices.translate(0.0F, info.offsetY, 0.0F);
      this.offsetInWater(matrices, event.itemEntity);
      this.preventZFighting(matrices, event.itemEntity);
      if (info.flat) {
         matrices.mulPose(Axis.XP.rotationDegrees(90.0F));
         matrices.translate(0.0F, 0.0F, info.offsetZ);
      }

      if (this.randomRotation.get()) {
         Axis axis = Axis.YP;
         if (info.flat) {
            axis = Axis.ZP;
         }

         float degrees = (this.random.nextFloat() * 2.0F - 1.0F) * 90.0F;
         matrices.mulPose(axis.rotationDegrees(degrees));
      }

      this.renderItem(event, matrices, itemStack, model, info);
      matrices.popPose();
      event.cancel();
   }

   @EventHandler
   private void onApplyTransformation(ApplyTransformationEvent event) {
      if (this.renderingItem) {
         event.cancel();
      }
   }

   private void renderItem(RenderItemEntityEvent event, PoseStack matrices, ItemStack itemStack, BakedModel model, ItemPhysics.ModelInfo info) {
      this.renderingItem = true;
      int count = this.getRenderedCount(itemStack);

      for (int i = 0; i < count; i++) {
         matrices.pushPose();
         if (i > 0) {
            float x = (this.random.nextFloat() * 2.0F - 1.0F) * 0.25F;
            float z = (this.random.nextFloat() * 2.0F - 1.0F) * 0.25F;
            this.translate(matrices, info, x, 0.0F, z);
         }

         event.itemRenderer
            .render(itemStack, ItemDisplayContext.GROUND, false, matrices, event.vertexConsumerProvider, event.light, OverlayTexture.NO_OVERLAY, model);
         matrices.popPose();
         float y = Math.max(this.random.nextFloat() * 0.0625F, 0.03125F);
         this.translate(matrices, info, 0.0F, y, 0.0F);
      }

      this.renderingItem = false;
   }

   private void translate(PoseStack matrices, ItemPhysics.ModelInfo info, float x, float y, float z) {
      if (info.flat) {
         float temp = y;
         y = z;
         z = -temp;
      }

      matrices.translate(x, y, z);
   }

   private int getRenderedCount(ItemStack stack) {
      int i = 1;
      if (stack.getCount() > 48) {
         i = 5;
      } else if (stack.getCount() > 32) {
         i = 4;
      } else if (stack.getCount() > 16) {
         i = 3;
      } else if (stack.getCount() > 1) {
         i = 2;
      }

      return i;
   }

   private void applyTransformation(PoseStack matrices, BakedModel model) {
      ItemTransform transformation = model.getTransforms().ground;
      float prevY = transformation.translation.y;
      transformation.translation.y = 0.0F;
      transformation.apply(false, matrices);
      transformation.translation.y = prevY;
   }

   private void offsetInWater(PoseStack matrices, ItemEntity entity) {
      if (entity.isInWater()) {
         matrices.translate(0.0F, 0.333F, 0.0F);
      }
   }

   private void preventZFighting(PoseStack matrices, ItemEntity entity) {
      float offset = 1.0E-4F;
      float distance = (float)this.mc.gameRenderer.getMainCamera().getPosition().distanceTo(entity.position());
      offset = Math.min(offset * Math.max(1.0F, distance), 0.01F);
      matrices.translate(0.0F, offset, 0.0F);
   }

   private BakedModel getModel(ItemEntity entity) {
      ItemStack itemStack = entity.getItem();
      if (itemStack.is(Items.TRIDENT)) {
         return this.mc.getItemRenderer().getItemModelShaper().getModelManager().getModel(ItemRenderer.TRIDENT_MODEL);
      } else {
         return itemStack.is(Items.SPYGLASS)
            ? this.mc.getItemRenderer().getItemModelShaper().getModelManager().getModel(ItemRenderer.SPYGLASS_MODEL)
            : this.mc.getItemRenderer().getModel(itemStack, entity.level(), null, entity.getId());
      }
   }

   private ItemPhysics.ModelInfo getInfo(BakedModel model) {
      RandomSource random = RandomSource.createNewThreadLocalInstance();
      float minX = Float.MAX_VALUE;
      float maxX = Float.MIN_VALUE;
      float minY = Float.MAX_VALUE;
      float maxY = Float.MIN_VALUE;
      float minZ = Float.MAX_VALUE;
      float maxZ = Float.MIN_VALUE;

      for (Direction face : FACES) {
         for (BakedQuad _quad : model.getQuads(null, face, random)) {
            IBakedQuad quad = (IBakedQuad)_quad;

            for (int i = 0; i < 4; i++) {
               switch (_quad.getDirection()) {
                  case DOWN:
                     minY = Math.min(minY, quad.meteor$getY(i));
                     break;
                  case UP:
                     maxY = Math.max(maxY, quad.meteor$getY(i));
                     break;
                  case NORTH:
                     minZ = Math.min(minZ, quad.meteor$getZ(i));
                     break;
                  case SOUTH:
                     maxZ = Math.max(maxZ, quad.meteor$getZ(i));
                     break;
                  case WEST:
                     minX = Math.min(minX, quad.meteor$getX(i));
                     break;
                  case EAST:
                     maxX = Math.max(maxX, quad.meteor$getX(i));
               }
            }
         }
      }

      if (minX == Float.MAX_VALUE) {
         minX = 0.0F;
      }

      if (minY == Float.MAX_VALUE) {
         minY = 0.0F;
      }

      if (minZ == Float.MAX_VALUE) {
         minZ = 0.0F;
      }

      if (maxX == Float.MIN_VALUE) {
         maxX = 1.0F;
      }

      if (maxY == Float.MIN_VALUE) {
         maxY = 1.0F;
      }

      if (maxZ == Float.MIN_VALUE) {
         maxZ = 1.0F;
      }

      float x = maxX - minX;
      float y = maxY - minY;
      float z = maxZ - minZ;
      boolean flat = x > 0.0625F && y > 0.0625F && z <= 0.0625F;
      return new ItemPhysics.ModelInfo(flat, 0.5F - minY, minZ - minY);
   }

   static record ModelInfo(boolean flat, float offsetY, float offsetZ) {
   }
}
