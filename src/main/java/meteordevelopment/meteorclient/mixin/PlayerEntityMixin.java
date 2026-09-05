package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.DropItemsEvent;
import meteordevelopment.meteorclient.events.entity.player.ClipAtLedgeEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.Anchor;
import meteordevelopment.meteorclient.systems.modules.movement.Flight;
import meteordevelopment.meteorclient.systems.modules.movement.NoSlow;
import meteordevelopment.meteorclient.systems.modules.movement.Scaffold;
import meteordevelopment.meteorclient.systems.modules.movement.Sprint;
import meteordevelopment.meteorclient.systems.modules.player.Reach;
import meteordevelopment.meteorclient.systems.modules.player.SpeedMine;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Player.class})
public abstract class PlayerEntityMixin extends LivingEntity {
   @Shadow
   public abstract Abilities getAbilities();

   protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, Level world) {
      super(entityType, world);
   }

   @Inject(
      method = {"clipAtLedge"},
      at = {@At("HEAD")},
      cancellable = true
   )
   protected void clipAtLedge(CallbackInfoReturnable<Boolean> info) {
      if (this.level().isClientSide) {
         ClipAtLedgeEvent event = MeteorClient.EVENT_BUS.post(ClipAtLedgeEvent.get());
         if (event.isSet()) {
            info.setReturnValue(event.isClip());
         }
      }
   }

   @Inject(
      method = {"dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onDropItem(ItemStack stack, boolean bl, boolean bl2, CallbackInfoReturnable<ItemEntity> info) {
      if (this.level().isClientSide && !stack.isEmpty() && MeteorClient.EVENT_BUS.post(DropItemsEvent.get(stack)).isCancelled()) {
         info.setReturnValue(null);
      }
   }

   @ModifyReturnValue(
      method = {"getBlockBreakingSpeed"},
      at = {@At("RETURN")}
   )
   public float onGetBlockBreakingSpeed(float breakSpeed, BlockState block) {
      if (!this.level().isClientSide) {
         return breakSpeed;
      } else {
         SpeedMine speedMine = Modules.get().get(SpeedMine.class);
         if (speedMine.isActive() && speedMine.mode.get() == SpeedMine.Mode.Normal && speedMine.filter(block.getBlock())) {
            float breakSpeedMod = (float)((double)breakSpeed * speedMine.modifier.get());
            if (MeteorClient.mc.hitResult instanceof BlockHitResult bhr) {
               BlockPos pos = bhr.getBlockPos();
               return !(speedMine.modifier.get() < 1.0) && BlockUtils.canInstaBreak(pos, breakSpeed) != BlockUtils.canInstaBreak(pos, breakSpeedMod)
                  ? 0.9F / BlockUtils.calcBlockBreakingDelta2(pos, 1.0F)
                  : breakSpeedMod;
            } else {
               return breakSpeed;
            }
         } else {
            return breakSpeed;
         }
      }
   }

   @Inject(
      method = {"jump"},
      at = {@At("HEAD")},
      cancellable = true
   )
   public void dontJump(CallbackInfo info) {
      if (this.level().isClientSide) {
         Anchor module = Modules.get().get(Anchor.class);
         if (module.isActive() && module.cancelJump) {
            info.cancel();
         } else if (Modules.get().get(Scaffold.class).towering()) {
            info.cancel();
         }
      }
   }

   @ModifyReturnValue(
      method = {"getMovementSpeed"},
      at = {@At("RETURN")}
   )
   private float onGetMovementSpeed(float original) {
      if (!this.level().isClientSide) {
         return original;
      } else if (!Modules.get().get(NoSlow.class).slowness()) {
         return original;
      } else {
         float walkSpeed = this.getAbilities().getWalkingSpeed();
         if (original < walkSpeed) {
            return this.isSprinting() ? (float)((double)walkSpeed * 1.300000011920929) : walkSpeed;
         } else {
            return original;
         }
      }
   }

   @Inject(
      method = {"getOffGroundSpeed"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onGetOffGroundSpeed(CallbackInfoReturnable<Float> info) {
      if (this.level().isClientSide) {
         float speed = Modules.get().get(Flight.class).getOffGroundSpeed();
         if (speed != -1.0F) {
            info.setReturnValue(speed);
         }
      }
   }

   @WrapWithCondition(
      method = {"attack"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/entity/player/PlayerEntity;setVelocity(Lnet/minecraft/util/math/Vec3d;)V"
      )}
   )
   private boolean keepSprint$setVelocity(Player instance, Vec3 vec3d) {
      return Modules.get().get(Sprint.class).stopSprinting();
   }

   @WrapWithCondition(
      method = {"attack"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/entity/player/PlayerEntity;setSprinting(Z)V"
      )}
   )
   private boolean keepSprint$setSprinting(Player instance, boolean b) {
      return Modules.get().get(Sprint.class).stopSprinting();
   }

   @ModifyReturnValue(
      method = {"getBlockInteractionRange"},
      at = {@At("RETURN")}
   )
   private double modifyBlockInteractionRange(double original) {
      return Math.max(0.0, original + Modules.get().get(Reach.class).blockReach());
   }

   @ModifyReturnValue(
      method = {"getEntityInteractionRange"},
      at = {@At("RETURN")}
   )
   private double modifyEntityInteractionRange(double original) {
      return Math.max(0.0, original + Modules.get().get(Reach.class).entityReach());
   }
}
