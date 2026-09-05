package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.NoSlow;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({SlimeBlock.class})
public abstract class SlimeBlockMixin {
   @Inject(
      method = {"onSteppedOn"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onSteppedOn(Level world, BlockPos pos, BlockState state, Entity entity, CallbackInfo info) {
      if (Modules.get().get(NoSlow.class).slimeBlock() && entity == MeteorClient.mc.player) {
         info.cancel();
      }
   }
}
