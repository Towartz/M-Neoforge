package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.BlockSelection;
import meteordevelopment.meteorclient.systems.modules.render.ESP;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.meteorclient.systems.modules.render.Fullbright;
import meteordevelopment.meteorclient.systems.modules.render.NoRender;
import meteordevelopment.meteorclient.systems.modules.world.Ambience;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.postprocess.EntityShader;
import meteordevelopment.meteorclient.utils.render.postprocess.PostProcessShaders;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LevelRenderer.class})
public abstract class WorldRendererMixin {
   @Shadow
   private RenderTarget entityTarget;
   @Unique
   private ESP esp;

   @Shadow
   protected abstract void renderEntity(Entity var1, double var2, double var4, double var6, float var8, PoseStack var9, MultiBufferSource var10);

   @Inject(
      method = {"checkPoseStack"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onCheckEmpty(PoseStack matrixStack, CallbackInfo info) {
      info.cancel();
   }

   @Inject(
      method = {"renderHitOutline"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onDrawHighlightedBlockOutline(
      PoseStack matrixStack,
      VertexConsumer vertexConsumer,
      Entity entity,
      double d,
      double e,
      double f,
      BlockPos blockPos,
      BlockState blockState,
      CallbackInfo info
   ) {
      if (Modules.get().isActive(BlockSelection.class)) {
         info.cancel();
      }
   }

   @ModifyArg(
      method = {"renderLevel"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/LevelRenderer;setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V"
      ),
      index = 3
   )
   private boolean renderSetupTerrainModifyArg(boolean spectator) {
      return Modules.get().isActive(Freecam.class) || spectator;
   }

   @Inject(
      method = {"renderSnowAndRain"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onRenderWeather(LightTexture manager, float f, double d, double e, double g, CallbackInfo info) {
      if (Modules.get().get(NoRender.class).noWeather()) {
         info.cancel();
      }
   }

   @Inject(
      method = {"doesMobEffectBlockSky(Lnet/minecraft/client/Camera;)Z"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void hasBlindnessOrDarkness(Camera camera, CallbackInfoReturnable<Boolean> info) {
      if (Modules.get().get(NoRender.class).noBlindness() || Modules.get().get(NoRender.class).noDarkness()) {
         info.setReturnValue(Boolean.valueOf(false));
      }
   }

   @Inject(
      method = {"renderLevel"},
      at = {@At("HEAD")}
   )
   private void onRenderHead(
      DeltaTracker tickCounter,
      boolean renderBlockOutline,
      Camera camera,
      GameRenderer gameRenderer,
      LightTexture lightmapTextureManager,
      Matrix4f matrix4f,
      Matrix4f matrix4f2,
      CallbackInfo ci
   ) {
      PostProcessShaders.beginRender();
   }

   @Inject(
      method = {"renderEntity"},
      at = {@At("HEAD")}
   )
   private void renderEntity(
      Entity entity, double cameraX, double cameraY, double cameraZ, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, CallbackInfo info
   ) {
      this.draw(entity, cameraX, cameraY, cameraZ, tickDelta, vertexConsumers, matrices, PostProcessShaders.CHAMS, Color.WHITE);
      this.draw(
         entity,
         cameraX,
         cameraY,
         cameraZ,
         tickDelta,
         vertexConsumers,
         matrices,
         PostProcessShaders.ENTITY_OUTLINE,
         Modules.get().get(ESP.class).getColor(entity)
      );
   }

   @Unique
   private void draw(
      Entity entity,
      double cameraX,
      double cameraY,
      double cameraZ,
      float tickDelta,
      MultiBufferSource vertexConsumers,
      PoseStack matrices,
      EntityShader shader,
      Color color
   ) {
      if (shader.shouldDraw(entity) && !PostProcessShaders.isCustom(vertexConsumers) && color != null) {
         RenderTarget prevBuffer = this.entityTarget;
         this.entityTarget = shader.framebuffer;
         PostProcessShaders.rendering = true;
         shader.vertexConsumerProvider.setColor(color.r, color.g, color.b, color.a);
         this.renderEntity(entity, cameraX, cameraY, cameraZ, tickDelta, matrices, shader.vertexConsumerProvider);
         PostProcessShaders.rendering = false;
         this.entityTarget = prevBuffer;
      }
   }

   @Inject(
      method = {"renderLevel"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/OutlineBufferSource;endOutlineBatch()V"
      )}
   )
   private void onRender(
      DeltaTracker tickCounter,
      boolean renderBlockOutline,
      Camera camera,
      GameRenderer gameRenderer,
      LightTexture lightmapTextureManager,
      Matrix4f matrix4f,
      Matrix4f matrix4f2,
      CallbackInfo ci
   ) {
      PostProcessShaders.endRender();
   }

   @ModifyExpressionValue(
      method = {"renderLevel"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/Minecraft;shouldEntityAppearGlowing(Lnet/minecraft/world/entity/Entity;)Z"
      )}
   )
   private boolean shouldMobGlow(boolean original, @Local Entity entity) {
      return this.getESP().isGlow() && !this.getESP().shouldSkip(entity) ? this.getESP().getColor(entity) != null || original : original;
   }

   @WrapOperation(
      method = {"renderLevel"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/OutlineBufferSource;setColor(IIII)V"
      )}
   )
   private void setGlowColor(OutlineBufferSource instance, int red, int green, int blue, int alpha, Operation<Void> original, @Local LocalRef<Entity> entity) {
      if (this.getESP().isGlow() && !this.getESP().shouldSkip((Entity)entity.get())) {
         Color color = this.getESP().getColor((Entity)entity.get());
         if (color == null) {
            original.call(new Object[]{instance, red, green, blue, alpha});
         } else {
            instance.setColor(color.r, color.g, color.b, color.a);
         }
      } else {
         original.call(new Object[]{instance, red, green, blue, alpha});
      }
   }

   @Inject(
      method = {"resize"},
      at = {@At("HEAD")}
   )
   private void onResized(int width, int height, CallbackInfo info) {
      PostProcessShaders.onResized(width, height);
   }

   @ModifyVariable(
      method = {"getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I"},
      at = @At("STORE"),
      ordinal = 0
   )
   private static int getLightmapCoordinatesModifySkyLight(int sky) {
      return Math.max(Modules.get().get(Fullbright.class).getLuminance(LightLayer.SKY), sky);
   }

   @ModifyVariable(
      method = {"getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I"},
      at = @At("STORE"),
      ordinal = 1
   )
   private static int getLightmapCoordinatesModifyBlockLight(int sky) {
      return Math.max(Modules.get().get(Fullbright.class).getLuminance(LightLayer.BLOCK), sky);
   }

   @Unique
   private ESP getESP() {
      if (this.esp == null) {
         this.esp = Modules.get().get(ESP.class);
      }

      return this.esp;
   }
}
