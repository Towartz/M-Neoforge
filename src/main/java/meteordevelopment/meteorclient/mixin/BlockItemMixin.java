package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.PlaceBlockEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.NoGhostBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({BlockItem.class})
public abstract class BlockItemMixin {
   @Shadow
   protected abstract BlockState getPlacementState(BlockPlaceContext var1);

   @Inject(
      method = {"place(Lnet/minecraft/item/ItemPlacementContext;Lnet/minecraft/block/BlockState;)Z"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onPlace(BlockPlaceContext context, BlockState state, CallbackInfoReturnable<Boolean> info) {
      if (context.getLevel().isClientSide) {
         if (MeteorClient.EVENT_BUS.post(PlaceBlockEvent.get(context.getClickedPos(), state.getBlock())).isCancelled()) {
            info.setReturnValue(true);
         }
      }
   }

   @ModifyVariable(
      method = {"place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;"},
      ordinal = 1,
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/block/BlockState;isOf(Lnet/minecraft/block/Block;)Z"
      )
   )
   private BlockState modifyState(BlockState state, BlockPlaceContext context) {
      NoGhostBlocks noGhostBlocks = Modules.get().get(NoGhostBlocks.class);
      return noGhostBlocks.isActive() && noGhostBlocks.placing.get() ? this.getPlacementState(context) : state;
   }
}
