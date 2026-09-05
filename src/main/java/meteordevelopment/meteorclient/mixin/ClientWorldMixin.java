package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.NoRender;
import meteordevelopment.meteorclient.systems.modules.world.Ambience;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.DimensionSpecialEffects.EndEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin({ClientLevel.class})
public abstract class ClientWorldMixin {
   @Unique
   private final DimensionSpecialEffects endSky = new EndEffects();
   @Unique
   private final DimensionSpecialEffects customSky = new Ambience.Custom();

   @Shadow
   @Nullable
   public abstract Entity getEntity(int var1);

   @Inject(
      method = {"addEntity"},
      at = {@At("TAIL")}
   )
   private void onAddEntity(Entity entity, CallbackInfo info) {
      if (entity != null) {
         MeteorClient.EVENT_BUS.post(EntityAddedEvent.get(entity));
      }
   }

   @Inject(
      method = {"removeEntity"},
      at = {@At("HEAD")}
   )
   private void onRemoveEntity(int entityId, RemovalReason removalReason, CallbackInfo info) {
      if (this.getEntity(entityId) != null) {
         MeteorClient.EVENT_BUS.post(EntityRemovedEvent.get(this.getEntity(entityId)));
      }
   }

   @Inject(
      method = {"getDimensionEffects"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onGetSkyProperties(CallbackInfoReturnable<DimensionSpecialEffects> info) {
      Ambience ambience = Modules.get().get(Ambience.class);
      if (ambience.isActive() && ambience.endSky.get()) {
         info.setReturnValue(ambience.customSkyColor.get() ? this.customSky : this.endSky);
      }
   }

   @Inject(
      method = {"getSkyColor"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onGetSkyColor(Vec3 cameraPos, float tickDelta, CallbackInfoReturnable<Vec3> info) {
      Ambience ambience = Modules.get().get(Ambience.class);
      if (ambience.isActive() && ambience.customSkyColor.get()) {
         info.setReturnValue(ambience.skyColor().getVec3d());
      }
   }

   @Inject(
      method = {"getCloudsColor"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onGetCloudsColor(float tickDelta, CallbackInfoReturnable<Vec3> info) {
      Ambience ambience = Modules.get().get(Ambience.class);
      if (ambience.isActive() && ambience.customCloudColor.get()) {
         info.setReturnValue(ambience.cloudColor.get().getVec3d());
      }
   }

   @ModifyArgs(
      method = {"doRandomBlockDisplayTicks"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/world/ClientWorld;randomBlockDisplayTick(IIIILnet/minecraft/util/math/random/Random;Lnet/minecraft/block/Block;Lnet/minecraft/util/math/BlockPos$Mutable;)V"
      )
   )
   private void doRandomBlockDisplayTicks(Args args) {
      if (Modules.get().get(NoRender.class).noBarrierInvis()) {
         args.set(5, Blocks.BARRIER);
      }
   }
}
