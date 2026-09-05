package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.render.RenderAfterWorldEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.LiquidInteract;
import meteordevelopment.meteorclient.systems.modules.player.NoMiningTrace;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.meteorclient.systems.modules.render.NoRender;
import meteordevelopment.meteorclient.systems.modules.render.Zoom;
import meteordevelopment.meteorclient.systems.modules.world.HighwayBuilder;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin({GameRenderer.class})
public abstract class GameRendererMixin {
   @Shadow
   @Final
   Minecraft minecraft;
   @Shadow
   @Final
   private Camera mainCamera;
   @Unique
   private Renderer3D renderer;
   @Unique
   private final PoseStack matrices = new PoseStack();
   @Unique
   private boolean freecamSet = false;

   @Shadow
   public abstract void pick(float var1);

   @Shadow
   public abstract void resetData();

   @Shadow
   protected abstract void bobView(PoseStack var1, float var2);

   @Shadow
   protected abstract void bobHurt(PoseStack var1, float var2);

   @Inject(
      method = {"renderWorld"},
      at = {@At(
         value = "INVOKE_STRING",
         target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V",
         args = {"ldc=hand"}
      )},
      locals = LocalCapture.CAPTURE_FAILSOFT,
      require = 0
   )
   private void onRenderWorld(
      DeltaTracker tickCounter, CallbackInfo ci, @Local(ordinal = 1) Matrix4f matrix4f2, @Local(ordinal = 1) float tickDelta, @Local PoseStack matrixStack
   ) {
      if (Utils.canUpdate()) {
         this.minecraft.getProfiler().push("meteor-client_render");
         if (this.renderer == null) {
            this.renderer = new Renderer3D();
         }

         Render3DEvent event = Render3DEvent.get(
            matrixStack, this.renderer, tickDelta, this.mainCamera.getPosition().x, this.mainCamera.getPosition().y, this.mainCamera.getPosition().z
         );
         RenderUtils.updateScreenCenter(matrix4f2);
         NametagUtils.onRender(matrix4f2);
         RenderSystem.getModelViewStack().pushMatrix().mul(matrix4f2);
         this.matrices.pushPose();
         this.bobHurt(this.matrices, this.mainCamera.getPartialTickTime());
         if ((Boolean)this.minecraft.options.bobView().get()) {
            this.bobView(this.matrices, this.mainCamera.getPartialTickTime());
         }

         RenderSystem.getModelViewStack().mul(this.matrices.last().pose().invert());
         this.matrices.popPose();
         RenderSystem.applyModelViewMatrix();
         this.renderer.begin();
         MeteorClient.EVENT_BUS.post(event);
         this.renderer.render(matrixStack);
         RenderSystem.getModelViewStack().popMatrix();
         RenderSystem.applyModelViewMatrix();
         this.minecraft.getProfiler().pop();
      }
   }

   @Inject(
      method = {"renderWorld"},
      at = {@At("TAIL")}
   )
   private void onRenderWorldTail(CallbackInfo info) {
      MeteorClient.EVENT_BUS.post(RenderAfterWorldEvent.get());
   }

   @ModifyReturnValue(
      method = {"findCrosshairTarget"},
      at = {@At("RETURN")}
   )
   private HitResult onUpdateTargetedEntity(HitResult original, @Local HitResult hitResult) {
      return Modules.get().get(NoMiningTrace.class).canWork(original instanceof EntityHitResult ehr ? ehr.getEntity() : null)
            && hitResult.getType() == Type.BLOCK
         ? hitResult
         : original;
   }

   @Redirect(
      method = {"findCrosshairTarget"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/entity/Entity;raycast(DFZ)Lnet/minecraft/util/hit/HitResult;"
      )
   )
   private HitResult updateTargetedEntityEntityRayTraceProxy(Entity entity, double maxDistance, float tickDelta, boolean includeFluids) {
      if (Modules.get().isActive(LiquidInteract.class)) {
         HitResult result = entity.pick(maxDistance, tickDelta, includeFluids);
         return result.getType() != Type.MISS ? result : entity.pick(maxDistance, tickDelta, true);
      } else {
         return entity.pick(maxDistance, tickDelta, includeFluids);
      }
   }

   @Inject(
      method = {"showFloatingItem"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onShowFloatingItem(ItemStack floatingItem, CallbackInfo info) {
      if (floatingItem.getItem() == Items.TOTEM_OF_UNDYING && Modules.get().get(NoRender.class).noTotemAnimation()) {
         info.cancel();
      }
   }

   @ModifyExpressionValue(
      method = {"renderWorld"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/util/math/MathHelper;lerp(FFF)F"
      )}
   )
   private float applyCameraTransformationsMathHelperLerpProxy(float original) {
      return Modules.get().get(NoRender.class).noNausea() ? 0.0F : original;
   }

   @Inject(
      method = {"renderNausea"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onRenderNausea(GuiGraphics context, float distortionStrength, CallbackInfo ci) {
      if (Modules.get().get(NoRender.class).noNausea()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"pick", "updateCrosshairTarget"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void updateTargetedEntityInvoke(float tickDelta, CallbackInfo info) {
      Freecam freecam = Modules.get().get(Freecam.class);
      boolean highwayBuilder = Modules.get().isActive(HighwayBuilder.class);
      if ((freecam.isActive() || highwayBuilder) && this.minecraft.getCameraEntity() != null && !this.freecamSet) {
         info.cancel();
         Entity cameraE = this.minecraft.getCameraEntity();
         double x = cameraE.getX();
         double y = cameraE.getY();
         double z = cameraE.getZ();
         double prevX = cameraE.xo;
         double prevY = cameraE.yo;
         double prevZ = cameraE.zo;
         float yaw = cameraE.getYRot();
         float pitch = cameraE.getXRot();
         float prevYaw = cameraE.yRotO;
         float prevPitch = cameraE.xRotO;
         if (highwayBuilder) {
            cameraE.setYRot(this.mainCamera.getYRot());
            cameraE.setXRot(this.mainCamera.getXRot());
         } else {
            ((IVec3d)cameraE.position()).set(freecam.pos.x, freecam.pos.y - (double)cameraE.getEyeHeight(cameraE.getPose()), freecam.pos.z);
            cameraE.xo = freecam.prevPos.x;
            cameraE.yo = freecam.prevPos.y - (double)cameraE.getEyeHeight(cameraE.getPose());
            cameraE.zo = freecam.prevPos.z;
            cameraE.setYRot(freecam.yaw);
            cameraE.setXRot(freecam.pitch);
            cameraE.yRotO = freecam.prevYaw;
            cameraE.xRotO = freecam.prevPitch;
         }

         this.freecamSet = true;
         this.pick(tickDelta);
         this.freecamSet = false;
         ((IVec3d)cameraE.position()).set(x, y, z);
         cameraE.xo = prevX;
         cameraE.yo = prevY;
         cameraE.zo = prevZ;
         cameraE.setYRot(yaw);
         cameraE.setXRot(pitch);
         cameraE.yRotO = prevYaw;
         cameraE.xRotO = prevPitch;
      }
   }

   @Inject(
      method = {"renderHand"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void renderHand(Camera camera, float tickDelta, Matrix4f matrix4f, CallbackInfo ci) {
      if (!Modules.get().get(Freecam.class).renderHands() || !Modules.get().get(Zoom.class).renderHands()) {
         ci.cancel();
      }
   }
}
