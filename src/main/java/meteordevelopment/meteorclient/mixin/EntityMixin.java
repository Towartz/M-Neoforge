package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.LivingEntityMoveEvent;
import meteordevelopment.meteorclient.events.entity.player.JumpVelocityMultiplierEvent;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.mixininterface.ICamera;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.Hitboxes;
import meteordevelopment.meteorclient.systems.modules.movement.Flight;
import meteordevelopment.meteorclient.systems.modules.movement.Jesus;
import meteordevelopment.meteorclient.systems.modules.movement.NoFall;
import meteordevelopment.meteorclient.systems.modules.movement.NoSlow;
import meteordevelopment.meteorclient.systems.modules.movement.Velocity;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import meteordevelopment.meteorclient.systems.modules.render.ESP;
import meteordevelopment.meteorclient.systems.modules.render.FreeLook;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.meteorclient.systems.modules.render.NoRender;
import meteordevelopment.meteorclient.systems.modules.world.HighwayBuilder;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.postprocess.PostProcessShaders;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin({Entity.class})
public abstract class EntityMixin {
   @ModifyExpressionValue(
      method = {"updateInWaterStateAndDoWaterCurrentPushing"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/level/material/FluidState;getFlow(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/Vec3;"
      )}
   )
   private Vec3 updateMovementInFluidFluidStateGetVelocity(Vec3 vec) {
      if ((Object)this != MeteorClient.mc.player) {
         return vec;
      } else {
         Velocity velocity = Modules.get().get(Velocity.class);
         if (velocity.isActive() && velocity.liquids.get()) {
            vec = vec.multiply(
               velocity.getHorizontal(velocity.liquidsHorizontal),
               velocity.getVertical(velocity.liquidsVertical),
               velocity.getHorizontal(velocity.liquidsHorizontal)
            );
         }

         return vec;
      }
   }

   @Inject(
      method = {"isTouchingWater"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void isTouchingWater(CallbackInfoReturnable<Boolean> info) {
      if ((Object)this == MeteorClient.mc.player) {
         if (Modules.get().get(Flight.class).isActive()) {
            info.setReturnValue(false);
         }

         if (Modules.get().get(NoSlow.class).fluidDrag()) {
            info.setReturnValue(false);
         }
      }
   }

   @Inject(
      method = {"isInLava"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void isInLava(CallbackInfoReturnable<Boolean> info) {
      if ((Object)this == MeteorClient.mc.player) {
         if (Modules.get().get(Flight.class).isActive()) {
            info.setReturnValue(false);
         }

         if (Modules.get().get(NoSlow.class).fluidDrag()) {
            info.setReturnValue(false);
         }
      }
   }

   @Inject(
      method = {"onBubbleColumnSurfaceCollision"},
      at = {@At("HEAD")}
   )
   private void onBubbleColumnSurfaceCollision(CallbackInfo info) {
      if ((Object)this == MeteorClient.mc.player) {
         Jesus jesus = Modules.get().get(Jesus.class);
         if (jesus.isActive()) {
            jesus.isInBubbleColumn = true;
         }
      }
   }

   @Inject(
      method = {"onBubbleColumnCollision"},
      at = {@At("HEAD")}
   )
   private void onBubbleColumnCollision(CallbackInfo info) {
      if ((Object)this == MeteorClient.mc.player) {
         Jesus jesus = Modules.get().get(Jesus.class);
         if (jesus.isActive()) {
            jesus.isInBubbleColumn = true;
         }
      }
   }

   @ModifyExpressionValue(
      method = {"updateSwimming"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/Entity;isUnderWater()Z"
      )}
   )
   private boolean isSubmergedInWater(boolean submerged) {
      if ((Object)this != MeteorClient.mc.player) {
         return submerged;
      } else if (Modules.get().get(NoSlow.class).fluidDrag()) {
         return false;
      } else {
         return Modules.get().get(Flight.class).isActive() ? false : submerged;
      }
   }

   @ModifyArgs(
      method = {"push(Lnet/minecraft/world/entity/Entity;)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/Entity;push(DDD)V"
      )
   )
   private void onPushAwayFrom(Args args, Entity entity) {
      Velocity velocity = Modules.get().get(Velocity.class);
      if ((Object)this == MeteorClient.mc.player && velocity.isActive() && velocity.entityPush.get()) {
         double multiplier = velocity.entityPushAmount.get();
         args.set(0, (Double)args.get(0) * multiplier);
         args.set(2, (Double)args.get(2) * multiplier);
      } else if (entity instanceof FakePlayerEntity player && player.doNotPush) {
         args.set(0, 0.0);
         args.set(2, 0.0);
      }
   }

   @ModifyReturnValue(
      method = {"getBlockJumpFactor"},
      at = {@At("RETURN")}
   )
   private float onGetJumpVelocityMultiplier(float original) {
      if ((Object)this == MeteorClient.mc.player) {
         JumpVelocityMultiplierEvent event = MeteorClient.EVENT_BUS.post(JumpVelocityMultiplierEvent.get());
         return original * event.multiplier;
      } else {
         return original;
      }
   }

   @Inject(
      method = {"move"},
      at = {@At("HEAD")}
   )
   private void onMove(MoverType type, Vec3 movement, CallbackInfo info) {
      if ((Object)this == MeteorClient.mc.player) {
         MeteorClient.EVENT_BUS.post(PlayerMoveEvent.get(type, movement));
      } else if ((Object)this instanceof LivingEntity) {
         MeteorClient.EVENT_BUS.post(LivingEntityMoveEvent.get((LivingEntity)(Object)this, movement));
      }
   }

   @Inject(
      method = {"getTeamColor", "getTeamColorValue"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void onGetTeamColorValue(CallbackInfoReturnable<Integer> info) {
      if (PostProcessShaders.rendering) {
         Color color = Modules.get().get(ESP.class).getColor((Entity)(Object)this);
         if (color != null) {
            info.setReturnValue(color.getPacked());
         }
      }
   }

   @Redirect(
      method = {"getBlockSpeedFactor"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/level/block/state/BlockState;getBlock()Lnet/minecraft/world/level/block/Block;"
      )
   )
   private Block getVelocityMultiplierGetBlockProxy(BlockState blockState) {
      if ((Object)this != MeteorClient.mc.player) {
         return blockState.getBlock();
      } else if (blockState.getBlock() == Blocks.SOUL_SAND && Modules.get().get(NoSlow.class).soulSand()) {
         return Blocks.STONE;
      } else {
         return blockState.getBlock() == Blocks.HONEY_BLOCK && Modules.get().get(NoSlow.class).honeyBlock() ? Blocks.STONE : blockState.getBlock();
      }
   }

   @ModifyReturnValue(
      method = {"isInvisibleTo(Lnet/minecraft/world/entity/player/Player;)Z"},
      at = {@At("RETURN")}
   )
   private boolean isInvisibleToCanceller(boolean original) {
      if (!Utils.canUpdate()) {
         return original;
      } else {
         ESP esp = Modules.get().get(ESP.class);
         return !Modules.get().get(NoRender.class).noInvisibility() && (!esp.isActive() || esp.shouldSkip((Entity)(Object)this)) ? original : false;
      }
   }

   @Inject(
      method = {"isGlowing"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void isGlowing(CallbackInfoReturnable<Boolean> info) {
      if (Modules.get().get(NoRender.class).noGlowing()) {
         info.setReturnValue(false);
      }
   }

   @Inject(
      method = {"getTargetingMargin"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onGetTargetingMargin(CallbackInfoReturnable<Float> info) {
      double v = Modules.get().get(Hitboxes.class).getEntityValue((Entity)(Object)this);
      if (v != 0.0) {
         info.setReturnValue((float)v);
      }
   }

   @Inject(
      method = {"isInvisibleTo"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onIsInvisibleTo(Player player, CallbackInfoReturnable<Boolean> info) {
      if (player == null) {
         info.setReturnValue(false);
      }
   }

   @Inject(
      method = {"getPose"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void getPoseHook(CallbackInfoReturnable<Pose> info) {
      if ((Object)this == MeteorClient.mc.player) {
         if (Modules.get().get(ElytraFly.class).canPacketEfly()) {
            info.setReturnValue(Pose.FALL_FLYING);
         }
      }
   }

   @ModifyReturnValue(
      method = {"getPose"},
      at = {@At("RETURN")}
   )
   private Pose modifyGetPose(Pose original) {
      if ((Object)this != MeteorClient.mc.player) {
         return original;
      } else {
         return original == Pose.CROUCHING && !MeteorClient.mc.player.isShiftKeyDown() ? Pose.STANDING : original;
      }
   }

   @ModifyReturnValue(
      method = {"isSuppressingBounce"},
      at = {@At("RETURN")}
   )
   private boolean cancelBounce(boolean original) {
      return Modules.get().get(NoFall.class).cancelBounce() || original;
   }

   @Inject(
      method = {"turn", "changeLookDirection"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void updateChangeLookDirection(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
      if ((Object)this == MeteorClient.mc.player) {
         Freecam freecam = Modules.get().get(Freecam.class);
         FreeLook freeLook = Modules.get().get(FreeLook.class);
         if (freecam.isActive()) {
            if (freecam.controlMode.get() == Freecam.ControlMode.Player) {
               return;
            }
            freecam.changeLookDirection(cursorDeltaX * 0.15, cursorDeltaY * 0.15);
            ci.cancel();
         } else if (Modules.get().isActive(HighwayBuilder.class)) {
            Camera camera = MeteorClient.mc.gameRenderer.getMainCamera();
            ((ICamera)camera).setRot((double)camera.getYRot() + cursorDeltaX * 0.15, (double)camera.getXRot() + cursorDeltaY * 0.15);
            ci.cancel();
         } else if (freeLook.cameraMode()) {
            freeLook.cameraYaw = freeLook.cameraYaw + (float)(cursorDeltaX / (double)freeLook.sensitivity.get().floatValue());
            freeLook.cameraPitch = freeLook.cameraPitch + (float)(cursorDeltaY / (double)freeLook.sensitivity.get().floatValue());
            if (Math.abs(freeLook.cameraPitch) > 90.0F) {
               freeLook.cameraPitch = freeLook.cameraPitch > 0.0F ? 90.0F : -90.0F;
            }

            ci.cancel();
         }
      }
   }
}
