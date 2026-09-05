package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.ElytraBoost;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({FireworkRocketEntity.class})
public abstract class FireworkRocketEntityMixin {
   @Shadow
   private int life;
   @Shadow
   private int lifetime;

   @Shadow
   protected abstract void explode();

   @Inject(
      method = {"tick"},
      at = {@At("TAIL")}
   )
   private void onTick(CallbackInfo info) {
      if (Modules.get().get(ElytraBoost.class).isFirework((FireworkRocketEntity)(Object)this) && this.life > this.lifetime) {
         this.explode();
      }
   }

   @Inject(
      method = {"onEntityHit"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onEntityHit(EntityHitResult entityHitResult, CallbackInfo info) {
      if (Modules.get().get(ElytraBoost.class).isFirework((FireworkRocketEntity)(Object)this)) {
         this.explode();
         info.cancel();
      }
   }

   @Inject(
      method = {"onBlockHit"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onBlockHit(BlockHitResult blockHitResult, CallbackInfo info) {
      if (Modules.get().get(ElytraBoost.class).isFirework((FireworkRocketEntity)(Object)this)) {
         this.explode();
         info.cancel();
      }
   }
}
