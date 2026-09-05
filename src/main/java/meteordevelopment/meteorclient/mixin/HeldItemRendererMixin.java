package meteordevelopment.meteorclient.mixin;

import com.google.common.base.MoreObjects;
import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.ArmRenderEvent;
import meteordevelopment.meteorclient.events.render.HeldItemRendererEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.HandView;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ItemInHandRenderer.class})
public abstract class HeldItemRendererMixin {
   @Shadow
   private float mainHandHeight;
   @Shadow
   private float offHandHeight;
   @Shadow
   private ItemStack mainHandItem;
   @Shadow
   private ItemStack offHandItem;

   @ModifyVariable(
      method = {"renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V"},
      at = @At(
         value = "STORE",
         ordinal = 0
      ),
      index = 6
   )
   private float modifySwing(float swingProgress) {
      HandView module = Modules.get().get(HandView.class);
      InteractionHand hand = (InteractionHand)MoreObjects.firstNonNull(MeteorClient.mc.player.swingingArm, InteractionHand.MAIN_HAND);
      if (module.isActive()) {
         if (hand == InteractionHand.OFF_HAND && !MeteorClient.mc.player.getOffhandItem().isEmpty()) {
            return swingProgress + module.offSwing.get().floatValue();
         }

         if (hand == InteractionHand.MAIN_HAND && !MeteorClient.mc.player.getMainHandItem().isEmpty()) {
            return swingProgress + module.mainSwing.get().floatValue();
         }
      }

      return swingProgress;
   }

   @Redirect(
      method = {"updateHeldItems"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/item/ItemStack;areEqual(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)Z"
      )
   )
   private boolean redirectSwapping(ItemStack left, ItemStack right) {
      return this.showSwapping(left, right);
   }

   @ModifyArg(
      method = {"updateHeldItems"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/util/math/MathHelper;clamp(FFF)F",
         ordinal = 2
      ),
      index = 0
   )
   private float modifyEquipProgressMainhand(float value) {
      float f = MeteorClient.mc.player.getAttackStrengthScale(1.0F);
      float modified = Modules.get().get(HandView.class).oldAnimations() ? 1.0F : f * f * f;
      return (this.showSwapping(this.mainHandItem, MeteorClient.mc.player.getMainHandItem()) ? modified : 0.0F) - this.mainHandHeight;
   }

   @ModifyArg(
      method = {"updateHeldItems"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/util/math/MathHelper;clamp(FFF)F",
         ordinal = 3
      ),
      index = 0
   )
   private float modifyEquipProgressOffhand(float value) {
      return (float)(this.showSwapping(this.offHandItem, MeteorClient.mc.player.getOffhandItem()) ? 1 : 0) - this.offHandHeight;
   }

   @Inject(
      method = {"renderFirstPersonItem"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"
      )}
   )
   private void onRenderItem(
      AbstractClientPlayer player,
      float tickDelta,
      float pitch,
      InteractionHand hand,
      float swingProgress,
      ItemStack item,
      float equipProgress,
      PoseStack matrices,
      MultiBufferSource vertexConsumers,
      int light,
      CallbackInfo ci
   ) {
      MeteorClient.EVENT_BUS.post(HeldItemRendererEvent.get(hand, matrices));
   }

   @Inject(
      method = {"renderFirstPersonItem"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderArmHoldingItem(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IFFLnet/minecraft/util/Arm;)V"
      )}
   )
   private void onRenderArm(
      AbstractClientPlayer player,
      float tickDelta,
      float pitch,
      InteractionHand hand,
      float swingProgress,
      ItemStack item,
      float equipProgress,
      PoseStack matrices,
      MultiBufferSource vertexConsumers,
      int light,
      CallbackInfo ci
   ) {
      MeteorClient.EVENT_BUS.post(ArmRenderEvent.get(hand, matrices));
   }

   @Inject(
      method = {"applyEatOrDrinkTransformation"},
      at = {@At(
         value = "INVOKE",
         target = "Ljava/lang/Math;pow(DD)D",
         shift = Shift.BEFORE
      )},
      cancellable = true
   )
   private void cancelTransformations(PoseStack matrices, float tickDelta, HumanoidArm arm, ItemStack stack, Player player, CallbackInfo ci) {
      if (Modules.get().get(HandView.class).disableFoodAnimation()) {
         ci.cancel();
      }
   }

   @Unique
   private boolean showSwapping(ItemStack stack1, ItemStack stack2) {
      return !Modules.get().get(HandView.class).showSwapping() || ItemStack.matches(stack1, stack2);
   }
}
