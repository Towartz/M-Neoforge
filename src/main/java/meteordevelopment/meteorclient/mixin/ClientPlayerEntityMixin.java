package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.authlib.GameProfile;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.DamageEvent;
import meteordevelopment.meteorclient.events.entity.DropItemsEvent;
import meteordevelopment.meteorclient.events.entity.player.PlayerTickMovementEvent;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.Flight;
import meteordevelopment.meteorclient.systems.modules.movement.GUIMove;
import meteordevelopment.meteorclient.systems.modules.movement.NoSlow;
import meteordevelopment.meteorclient.systems.modules.movement.Scaffold;
import meteordevelopment.meteorclient.systems.modules.movement.Sneak;
import meteordevelopment.meteorclient.systems.modules.movement.Sprint;
import meteordevelopment.meteorclient.systems.modules.movement.Velocity;
import meteordevelopment.meteorclient.systems.modules.player.Portals;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LocalPlayer.class})
public abstract class ClientPlayerEntityMixin extends AbstractClientPlayer {
   @Shadow
   public Input input;

   public ClientPlayerEntityMixin(ClientLevel world, GameProfile profile) {
      super(world, profile);
   }

   @Inject(
      method = {"dropSelectedItem"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onDropSelectedItem(boolean dropEntireStack, CallbackInfoReturnable<Boolean> info) {
      if (MeteorClient.EVENT_BUS.post(DropItemsEvent.get(this.getMainHandItem())).isCancelled()) {
         info.setReturnValue(false);
      }
   }

   @Redirect(
      method = {"tickNausea"},
      at = @At(
         value = "FIELD",
         target = "Lnet/minecraft/client/Minecraft;screen:Lnet/minecraft/client/gui/screens/Screen;"
      ),
      require = 0
   )
   private Screen updateNauseaGetCurrentScreenProxy(Minecraft client) {
      return Modules.get().isActive(Portals.class) ? null : client.screen;
   }

   @ModifyExpressionValue(
      method = {"tickMovement"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"
      )}
   )
   private boolean redirectUsingItem(boolean isUsingItem) {
      return Modules.get().get(NoSlow.class).items() ? false : isUsingItem;
   }

   @Inject(
      method = {"isSneaking"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onIsSneaking(CallbackInfoReturnable<Boolean> info) {
      if (Modules.get().get(Scaffold.class).scaffolding()) {
         info.setReturnValue(false);
      }

      if (Modules.get().get(Flight.class).noSneak()) {
         info.setReturnValue(false);
      }
   }

   @Inject(
      method = {"shouldSlowDown"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onShouldSlowDown(CallbackInfoReturnable<Boolean> info) {
      if (Modules.get().get(NoSlow.class).sneaking()) {
         info.setReturnValue(this.isVisuallyCrawling());
      }
   }

   @Inject(
      method = {"pushOutOfBlocks"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onPushOutOfBlocks(double x, double d, CallbackInfo info) {
      Velocity velocity = Modules.get().get(Velocity.class);
      if (velocity.isActive() && velocity.blocks.get()) {
         info.cancel();
      }
   }

   @Inject(
      method = {"damage"},
      at = {@At("HEAD")}
   )
   private void onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> info) {
      if (Utils.canUpdate() && this.level().isClientSide && this.canBeSeenAsEnemy()) {
         MeteorClient.EVENT_BUS.post(DamageEvent.get(this, source));
      }
   }

   @ModifyExpressionValue(
      method = {"canSprint"},
      at = {@At(
         value = "CONSTANT",
         args = {"floatValue=6.0f"}
      )}
   )
   private float onHunger(float constant) {
      return Modules.get().get(NoSlow.class).hunger() ? -1.0F : constant;
   }

   @ModifyExpressionValue(
      method = {"sendMovementPackets"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/network/ClientPlayerEntity;isSneaking()Z"
      )}
   )
   private boolean isSneaking(boolean sneaking) {
      return Modules.get().get(Sneak.class).doPacket() || Modules.get().get(NoSlow.class).airStrict() || sneaking;
   }

   @Inject(
      method = {"tickMovement"},
      at = {@At("HEAD")}
   )
   private void preTickMovement(CallbackInfo ci) {
      MeteorClient.EVENT_BUS.post(PlayerTickMovementEvent.get());
   }

   @Inject(
      method = {"tickMovement"},
      at = {@At("TAIL")}
   )
   private void postTickMovement(CallbackInfo ci) {
      if (Modules.get() != null) {
         GUIMove guiMove = Modules.get().get(GUIMove.class);
         if (guiMove != null && guiMove.isActive() && !guiMove.skip() && guiMove.isScreenValid() && guiMove.sprint.get()) {
            if (meteordevelopment.meteorclient.utils.misc.input.Input.isPressed(Minecraft.getInstance().options.keySprint) && this.input.hasForwardImpulse() && !this.isPassenger()) {
               this.setSprinting(true);
            }
         }
      }
   }

   @ModifyExpressionValue(
      method = {"canStartSprinting"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/network/ClientPlayerEntity;isWalking()Z"
      )}
   )
   private boolean modifyIsWalking(boolean original) {
      if (!Modules.get().get(Sprint.class).rageSprint()) {
         return original;
      } else {
         float forwards = Math.abs(this.input.leftImpulse);
         float sideways = Math.abs(this.input.forwardImpulse);
         return this.isUnderWater() ? forwards > 1.0E-5F || sideways > 1.0E-5F : (double)forwards > 0.8 || (double)sideways > 0.8;
      }
   }

   @ModifyExpressionValue(
      method = {"tickMovement"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/input/Input;hasForwardMovement()Z"
      )}
   )
   private boolean modifyMovement(boolean original) {
      return !Modules.get().get(Sprint.class).rageSprint()
         ? original
         : Math.abs(this.input.leftImpulse) > 1.0E-5F || Math.abs(this.input.forwardImpulse) > 1.0E-5F;
   }

   @WrapWithCondition(
      method = {"tickMovement"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/network/ClientPlayerEntity;setSprinting(Z)V",
         ordinal = 3
      )}
   )
   private boolean wrapSetSprinting(LocalPlayer instance, boolean b) {
      return !Modules.get().get(Sprint.class).rageSprint();
   }

   @Inject(
      method = {"sendPosition"},
      at = {@At("HEAD")}
   )
   private void onSendMovementPacketsHead(CallbackInfo info) {
      MeteorClient.EVENT_BUS.post(SendMovementPacketsEvent.Pre.get());
   }

   @Inject(
      method = {"tick"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V",
         ordinal = 0
      )}
   )
   private void onTickHasVehicleBeforeSendPackets(CallbackInfo info) {
      MeteorClient.EVENT_BUS.post(SendMovementPacketsEvent.Pre.get());
   }

   @Inject(
      method = {"sendPosition"},
      at = {@At("TAIL")}
   )
   private void onSendMovementPacketsTail(CallbackInfo info) {
      MeteorClient.EVENT_BUS.post(SendMovementPacketsEvent.Post.get());
   }

   @Inject(
      method = {"tick"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V",
         ordinal = 1,
         shift = Shift.AFTER
      )}
   )
   private void onTickHasVehicleAfterSendPackets(CallbackInfo info) {
      MeteorClient.EVENT_BUS.post(SendMovementPacketsEvent.Post.get());
   }
}
