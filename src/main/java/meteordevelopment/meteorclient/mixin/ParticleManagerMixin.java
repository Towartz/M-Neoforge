package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.ParticleEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.NoRender;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ParticleEngine.class})
public abstract class ParticleManagerMixin {
   @Shadow
   @Nullable
   protected abstract <T extends ParticleOptions> Particle makeParticle(T var1, double var2, double var4, double var6, double var8, double var10, double var12);

   @Inject(
      method = {"addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onAddParticle(
      ParticleOptions parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ, CallbackInfoReturnable<Particle> info
   ) {
      ParticleEvent event = MeteorClient.EVENT_BUS.post(ParticleEvent.get(parameters));
      if (event.isCancelled()) {
         if (parameters.getType() == ParticleTypes.FLASH) {
            info.setReturnValue(this.makeParticle(parameters, x, y, z, velocityX, velocityY, velocityZ));
         } else {
            info.cancel();
         }
      }
   }

   @Inject(
      method = {"addBlockBreakParticles"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onAddBlockBreakParticles(BlockPos blockPos, BlockState state, CallbackInfo info) {
      if (Modules.get().get(NoRender.class).noBlockBreakParticles()) {
         info.cancel();
      }
   }

   @Inject(
      method = {"addBlockBreakingParticles"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onAddBlockBreakingParticles(BlockPos blockPos, Direction direction, CallbackInfo info) {
      if (Modules.get().get(NoRender.class).noBlockBreakParticles()) {
         info.cancel();
      }
   }
}
