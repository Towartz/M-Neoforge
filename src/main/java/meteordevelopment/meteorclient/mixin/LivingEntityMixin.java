package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.DamageEvent;
import meteordevelopment.meteorclient.events.entity.player.CanWalkOnFluidEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.Sprint;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFlightModes;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.modes.Bounce;
import meteordevelopment.meteorclient.systems.modules.player.NoStatusEffects;
import meteordevelopment.meteorclient.systems.modules.player.OffhandCrash;
import meteordevelopment.meteorclient.systems.modules.player.PotionSpoof;
import meteordevelopment.meteorclient.systems.modules.render.HandView;
import meteordevelopment.meteorclient.systems.modules.render.NoRender;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LivingEntity.class})
public abstract class LivingEntityMixin extends Entity {
   @Unique
   private boolean previousElytra = false;

   public LivingEntityMixin(EntityType<?> type, Level world) {
      super(type, world);
   }

   @Inject(
      method = {"hurt"},
      at = {@At("HEAD")}
   )
   private void onDamageHead(DamageSource source, float amount, CallbackInfoReturnable<Boolean> info) {
      if (Utils.canUpdate() && this.level().isClientSide) {
         MeteorClient.EVENT_BUS.post(DamageEvent.get((LivingEntity)(Object)this, source));
      }
   }

   @ModifyReturnValue(
      method = {"canStandOnFluid"},
      at = {@At("RETURN")}
   )
   private boolean onCanWalkOnFluid(boolean original, FluidState fluidState) {
      if ((Object)this != MeteorClient.mc.player) {
         return original;
      } else {
         CanWalkOnFluidEvent event = MeteorClient.EVENT_BUS.post(CanWalkOnFluidEvent.get(fluidState));
         return event.walkOnFluid;
      }
   }

   @Unique
   private static NoStatusEffects meteor$cachedNoStatusEffects;
   @Unique
   private static NoRender meteor$cachedNoRender;

   @ModifyReturnValue(
      method = {"hasEffect"},
      at = {@At("RETURN")}
   )
   private boolean onHasEffect(boolean original, Holder<MobEffect> effect) {
      if ((Object)this != MeteorClient.mc.player || effect == null) {
         return original;
      }
      try {
         if (!effect.isBound() || effect.value() == null) {
            return original;
         }
      } catch (Throwable t) {
         return original;
      }
      NoStatusEffects module = meteor$cachedNoStatusEffects;
      if (module == null && Modules.get() != null) {
         meteor$cachedNoStatusEffects = module = Modules.get().get(NoStatusEffects.class);
      }
      if (module != null && module.isActive() && module.shouldBlock(effect.value())) {
         return false;
      }
      PotionSpoof potionSpoof = Modules.get() != null ? Modules.get().get(PotionSpoof.class) : null;
      if (potionSpoof != null && potionSpoof.isActive() && potionSpoof.shouldBlock(effect.value())) {
         return false;
      }
      NoRender noRender = meteor$cachedNoRender;
      if (noRender == null && Modules.get() != null) {
         meteor$cachedNoRender = noRender = Modules.get().get(NoRender.class);
      }
      if (noRender != null && noRender.isActive()) {
         if (effect.is(MobEffects.BLINDNESS) && noRender.noBlindness()) {
            return false;
         }
         if (effect.is(MobEffects.DARKNESS) && noRender.noDarkness()) {
            return false;
         }
      }
      return original;
   }

   @ModifyReturnValue(
      method = {"getEffect"},
      at = {@At("RETURN")}
   )
   private MobEffectInstance onGetEffect(MobEffectInstance original, Holder<MobEffect> effect) {
      if ((Object)this != MeteorClient.mc.player || effect == null) {
         return original;
      }
      try {
         if (!effect.isBound() || effect.value() == null) {
            return original;
         }
      } catch (Throwable t) {
         return original;
      }
      NoStatusEffects module = meteor$cachedNoStatusEffects;
      if (module == null && Modules.get() != null) {
         meteor$cachedNoStatusEffects = module = Modules.get().get(NoStatusEffects.class);
      }
      if (module != null && module.isActive() && module.shouldBlock(effect.value())) {
         return null;
      }
      PotionSpoof potionSpoof = Modules.get() != null ? Modules.get().get(PotionSpoof.class) : null;
      if (potionSpoof != null && potionSpoof.isActive() && potionSpoof.shouldBlock(effect.value())) {
         return null;
      }
      NoRender noRender = meteor$cachedNoRender;
      if (noRender == null && Modules.get() != null) {
         meteor$cachedNoRender = noRender = Modules.get().get(NoRender.class);
      }
      if (noRender != null && noRender.isActive()) {
         if (effect.is(MobEffects.BLINDNESS) && noRender.noBlindness()) {
            return null;
         }
         if (effect.is(MobEffects.DARKNESS) && noRender.noDarkness()) {
            return null;
         }
      }
      return original;
   }

   @Inject(
      method = {"spawnItemParticles"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void spawnItemParticles(ItemStack stack, int count, CallbackInfo info) {
      NoRender noRender = Modules.get().get(NoRender.class);
      if (noRender.noEatParticles() && stack.getComponents().has(DataComponents.FOOD)) {
         info.cancel();
      }
   }

   @Inject(
      method = {"onEquipItem"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onEquipStack(EquipmentSlot slot, ItemStack oldStack, ItemStack newStack, CallbackInfo info) {
      if ((Object)this == MeteorClient.mc.player && Modules.get().get(OffhandCrash.class).isAntiCrash()) {
         info.cancel();
      }
   }

   @ModifyArg(
      method = {"swing(Lnet/minecraft/world/InteractionHand;)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/LivingEntity;swing(Lnet/minecraft/world/InteractionHand;Z)V"
      )
   )
   private InteractionHand setHand(InteractionHand hand) {
      HandView handView = Modules.get().get(HandView.class);
      if ((Object)this == MeteorClient.mc.player && handView.isActive()) {
         if (handView.swingMode.get() == HandView.SwingMode.None) {
            return hand;
         } else {
            return handView.swingMode.get() == HandView.SwingMode.Offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
         }
      } else {
         return hand;
      }
   }

   @ModifyConstant(
      method = {"getCurrentSwingDuration"},
      constant = {@Constant(
         intValue = 6
      )}
   )
   private int getHandSwingDuration(int constant) {
      if ((Object)this != MeteorClient.mc.player) {
         return constant;
      } else {
         return Modules.get().get(HandView.class).isActive() && MeteorClient.mc.options.getCameraType().isFirstPerson()
            ? Modules.get().get(HandView.class).swingSpeed.get()
            : constant;
      }
   }

   @ModifyReturnValue(
      method = {"isFallFlying"},
      at = {@At("RETURN")}
   )
   private boolean isFallFlyingHook(boolean original) {
      return (Object)this == MeteorClient.mc.player && Modules.get().get(ElytraFly.class).canPacketEfly() ? true : original;
   }

   @Inject(
      method = {"isFallFlying"},
      at = {@At("TAIL")},
      cancellable = true
   )
   public void recastOnLand(CallbackInfoReturnable<Boolean> cir) {
      boolean elytra = (Boolean)cir.getReturnValue();
      ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
      if (this.previousElytra && !elytra && elytraFly.isActive() && elytraFly.flightMode.get() == ElytraFlightModes.Bounce) {
         cir.setReturnValue(Bounce.recastElytra(MeteorClient.mc.player));
      }

      this.previousElytra = elytra;
   }

   @ModifyExpressionValue(
      method = {"jumpFromGround"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"
      )}
   )
   private float modifyGetYaw(float original) {
      if ((Object)this != MeteorClient.mc.player) {
         return original;
      } else {
         Sprint s = Modules.get().get(Sprint.class);
         if (s.rageSprint() && s.jumpFix.get()) {
            float forward = Math.signum(MeteorClient.mc.player.input.forwardImpulse);
            float strafe = 90.0F * Math.signum(MeteorClient.mc.player.input.leftImpulse);
            if (forward != 0.0F) {
               strafe *= forward * 0.5F;
            }

            original -= strafe;
            if (forward < 0.0F) {
               original -= 180.0F;
            }

            return original;
         } else {
            return original;
         }
      }
   }

   @ModifyExpressionValue(
      method = {"jumpFromGround"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/LivingEntity;isSprinting()Z"
      )}
   )
   private boolean modifyIsSprinting(boolean original) {
      return (Object)this == MeteorClient.mc.player && Modules.get().get(Sprint.class).rageSprint()
         ? original && (Math.abs(MeteorClient.mc.player.input.forwardImpulse) > 1.0E-5F || Math.abs(MeteorClient.mc.player.input.leftImpulse) > 1.0E-5F)
         : original;
   }
}
