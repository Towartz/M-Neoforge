package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Chams;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.meteorclient.systems.modules.render.NoRender;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin({LivingEntityRenderer.class})
public abstract class LivingEntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>> {
   @Shadow
   @Nullable
   protected abstract RenderType getRenderType(T var1, boolean var2, boolean var3, boolean var4);

   @ModifyExpressionValue(
      method = {"shouldShowName"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/Minecraft;getCameraEntity()Lnet/minecraft/world/entity/Entity;"
      )}
   )
   private Entity hasLabelGetCameraEntityProxy(Entity cameraEntity) {
      return Modules.get().isActive(Freecam.class) ? null : cameraEntity;
   }

   @ModifyVariable(
      method = {"render"},
      ordinal = 2,
      at = @At(
         value = "STORE",
         ordinal = 0
      )
   )
   public float changeYaw(float oldValue, LivingEntity entity) {
      return entity.equals(MeteorClient.mc.player) && Rotations.rotationTimer < 10 ? Rotations.serverYaw : oldValue;
   }

   @ModifyVariable(
      method = {"render"},
      ordinal = 3,
      at = @At(
         value = "STORE",
         ordinal = 0
      )
   )
   public float changeHeadYaw(float oldValue, LivingEntity entity) {
      return entity.equals(MeteorClient.mc.player) && Rotations.rotationTimer < 10 ? Rotations.serverYaw : oldValue;
   }

   @ModifyVariable(
      method = {"render"},
      ordinal = 5,
      at = @At(
         value = "STORE",
         ordinal = 3
      )
   )
   public float changePitch(float oldValue, LivingEntity entity) {
      return entity.equals(MeteorClient.mc.player) && Rotations.rotationTimer < 10 ? Rotations.serverPitch : oldValue;
   }

   @ModifyExpressionValue(
      method = {"shouldShowName"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;getTeam()Lnet/minecraft/world/scores/Team;"
      )}
   )
   private PlayerTeam hasLabelClientPlayerEntityGetScoreboardTeamProxy(PlayerTeam team) {
      return MeteorClient.mc.player == null ? null : team;
   }

   @Inject(
      method = {"render"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void renderHead(T livingEntity, float f, float g, PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, int i, CallbackInfo ci) {
      if (Modules.get().get(NoRender.class).noDeadEntities() && livingEntity.isDeadOrDying()) {
         ci.cancel();
      }

      Chams chams = Modules.get().get(Chams.class);
      if (chams.isActive() && chams.shouldRender(livingEntity)) {
         GL11.glEnable(32823);
         GL11.glPolygonOffset(1.0F, -1100000.0F);
      }
   }

   @Inject(
      method = {"render"},
      at = {@At("TAIL")}
   )
   private void renderTail(T livingEntity, float f, float g, PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, int i, CallbackInfo ci) {
      Chams chams = Modules.get().get(Chams.class);
      if (chams.isActive() && chams.shouldRender(livingEntity)) {
         GL11.glPolygonOffset(1.0F, 1100000.0F);
         GL11.glDisable(32823);
      }
   }

   @ModifyArgs(
      method = {"render"},
      at = @At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V",
         ordinal = 1
      )
   )
   private void modifyScale(Args args, T livingEntity, float f, float g, PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, int i) {
      Chams module = Modules.get().get(Chams.class);
      if (module.isActive() && module.players.get() && livingEntity instanceof Player) {
         if (!module.ignoreSelf.get() || livingEntity != MeteorClient.mc.player) {
            args.set(0, -module.playersScale.get().floatValue());
            args.set(1, -module.playersScale.get().floatValue());
            args.set(2, module.playersScale.get().floatValue());
         }
      }
   }

   @ModifyArgs(
      method = {"render"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"
      )
   )
   private void modifyColor(Args args, T livingEntity, float f, float g, PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, int i) {
      Chams module = Modules.get().get(Chams.class);
      if (module.isActive() && module.players.get() && livingEntity instanceof Player) {
         if (!module.ignoreSelf.get() || livingEntity != MeteorClient.mc.player) {
            Color color = PlayerUtils.getPlayerColor((Player)livingEntity, module.playersColor.get());
            args.set(4, color.getPacked());
         }
      }
   }

   @Redirect(
      method = {"render"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getRenderType(Lnet/minecraft/world/entity/LivingEntity;ZZZ)Lnet/minecraft/client/renderer/RenderType;"
      )
   )
   private RenderType getRenderLayer(
      LivingEntityRenderer<T, M> livingEntityRenderer, T livingEntity, boolean showBody, boolean translucent, boolean showOutline
   ) {
      Chams module = Modules.get().get(Chams.class);
      if (!module.isActive() || !module.players.get() || !(livingEntity instanceof Player) || module.playersTexture.get()) {
         return this.getRenderType(livingEntity, showBody, translucent, showOutline);
      } else {
         return module.ignoreSelf.get() && livingEntity == MeteorClient.mc.player
            ? this.getRenderType(livingEntity, showBody, translucent, showOutline)
            : RenderType.itemEntityTranslucentCull(Chams.BLANK);
      }
   }
}
