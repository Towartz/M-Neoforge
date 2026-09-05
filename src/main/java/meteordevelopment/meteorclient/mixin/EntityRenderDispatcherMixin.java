package meteordevelopment.meteorclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import meteordevelopment.meteorclient.mixininterface.IBox;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.Hitboxes;
import meteordevelopment.meteorclient.systems.modules.render.NoRender;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import meteordevelopment.meteorclient.utils.render.postprocess.PostProcessShaders;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin({EntityRenderDispatcher.class})
public abstract class EntityRenderDispatcherMixin {
   @Shadow
   public Camera camera;

   @Inject(
      method = {"render"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private <E extends Entity> void render(
      E entity, double x, double y, double z, float yaw, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, CallbackInfo info
   ) {
      if (entity instanceof FakePlayerEntity player && player.hideWhenInsideCamera) {
         int cX = Mth.floor(this.camera.getPosition().x);
         int cY = Mth.floor(this.camera.getPosition().y);
         int cZ = Mth.floor(this.camera.getPosition().z);
         if (cX == entity.getBlockX() && cZ == entity.getBlockZ() && (cY == entity.getBlockY() || cY == entity.getBlockY() + 1)) {
            info.cancel();
         }
      }
   }

   @Inject(
      method = {"renderHitbox"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/WorldRenderer;drawBox(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/util/math/Box;FFFF)V",
         ordinal = 0
      )},
      locals = LocalCapture.CAPTURE_FAILSOFT
   )
   private static void onRenderHitbox(
      PoseStack matrices, VertexConsumer vertices, Entity entity, float tickDelta, float red, float green, float blue, CallbackInfo ci, AABB box
   ) {
      double v = Modules.get().get(Hitboxes.class).getEntityValue(entity);
      if (v != 0.0) {
         ((IBox)box).expand(v);
      }
   }

   @Inject(
      method = {"renderShadow"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void onRenderShadow(
      PoseStack matrices, MultiBufferSource vertexConsumers, Entity entity, float opacity, float tickDelta, LevelReader world, float radius, CallbackInfo info
   ) {
      if (PostProcessShaders.rendering) {
         info.cancel();
      }

      if (Modules.get().get(NoRender.class).noDeadEntities() && entity instanceof LivingEntity && ((LivingEntity)entity).isDeadOrDying()) {
         info.cancel();
      }
   }

   @Inject(
      method = {"getSquaredDistanceToCamera(Lnet/minecraft/entity/Entity;)D"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onGetSquaredDistanceToCameraEntity(Entity entity, CallbackInfoReturnable<Double> info) {
      if (this.camera == null) {
         info.setReturnValue(0.0);
      }
   }

   @Inject(
      method = {"getSquaredDistanceToCamera(DDD)D"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onGetSquaredDistanceToCameraXYZ(double x, double y, double z, CallbackInfoReturnable<Double> info) {
      if (this.camera == null) {
         info.setReturnValue(0.0);
      }
   }
}
