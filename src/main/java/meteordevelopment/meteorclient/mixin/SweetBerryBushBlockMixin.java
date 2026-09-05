package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.NoSlow;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({SweetBerryBushBlock.class})
public abstract class SweetBerryBushBlockMixin {
   @Inject(
      method = {"onEntityCollision"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/entity/Entity;slowMovement(Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/Vec3d;)V"
      )},
      cancellable = true
   )
   private void onEntityCollision(BlockState state, Level world, BlockPos pos, Entity entity, CallbackInfo info) {
      if (entity == MeteorClient.mc.player && Modules.get().get(NoSlow.class).berryBush()) {
         info.cancel();
      }
   }
}
