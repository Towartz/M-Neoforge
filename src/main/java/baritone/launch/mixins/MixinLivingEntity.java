package baritone.launch.mixins;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.event.events.RotationMoveEvent;
import java.util.Optional;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LivingEntity.class})
public abstract class MixinLivingEntity extends Entity {
   @Unique
   private RotationMoveEvent jumpRotationEvent;
   @Unique
   private RotationMoveEvent elytraRotationEvent;

   private MixinLivingEntity(EntityType<?> entityTypeIn, Level worldIn) {
      super(entityTypeIn, worldIn);
   }

   @Inject(
      method = {"jumpFromGround"},
      at = {@At("HEAD")}
   )
   private void preMoveRelative(CallbackInfo ci) {
      this.getBaritone().ifPresent(baritone -> {
         this.jumpRotationEvent = new RotationMoveEvent(RotationMoveEvent.Type.JUMP, this.getYRot(), this.getXRot());
         baritone.getGameEventHandler().onPlayerRotationMove(this.jumpRotationEvent);
      });
   }

   @Redirect(
      method = {"jumpFromGround"},
      at = @At(
         value = "INVOKE",
         target = "net/minecraft/world/entity/LivingEntity.getYRot()F"
      )
   )
   private float overrideYaw(LivingEntity self) {
      return self instanceof LocalPlayer && BaritoneAPI.getProvider().getBaritoneForPlayer((LocalPlayer)(Object)this) != null
         ? this.jumpRotationEvent.getYaw()
         : self.getYRot();
   }

   @Inject(
      method = {"travel"},
      at = {@At(
         value = "INVOKE",
         target = "net/minecraft/world/entity/LivingEntity.getLookAngle()Lnet/minecraft/world/phys/Vec3;"
      )}
   )
   private void onPreElytraMove(Vec3 direction, CallbackInfo ci) {
      this.getBaritone().ifPresent(baritone -> {
         this.elytraRotationEvent = new RotationMoveEvent(RotationMoveEvent.Type.MOTION_UPDATE, this.getYRot(), this.getXRot());
         baritone.getGameEventHandler().onPlayerRotationMove(this.elytraRotationEvent);
         this.setYRot(this.elytraRotationEvent.getYaw());
         this.setXRot(this.elytraRotationEvent.getPitch());
      });
   }

   @Inject(
      method = {"travel"},
      at = {@At(
         value = "INVOKE",
         target = "net/minecraft/world/entity/LivingEntity.move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
         shift = Shift.AFTER
      )}
   )
   private void onPostElytraMove(Vec3 direction, CallbackInfo ci) {
      if (this.elytraRotationEvent != null) {
         this.setYRot(this.elytraRotationEvent.getOriginal().getYaw());
         this.setXRot(this.elytraRotationEvent.getOriginal().getPitch());
         this.elytraRotationEvent = null;
      }
   }

   @Unique
   private Optional<IBaritone> getBaritone() {
      return LocalPlayer.class.isInstance(this) ? Optional.ofNullable(BaritoneAPI.getProvider().getBaritoneForPlayer((LocalPlayer)(Object)this)) : Optional.empty();
   }
}
