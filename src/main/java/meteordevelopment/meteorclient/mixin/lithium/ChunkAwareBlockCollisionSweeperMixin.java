package meteordevelopment.meteorclient.mixin.lithium;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.CollisionShapeEvent;
import net.caffeinemc.mods.lithium.common.entity.movement.ChunkAwareBlockCollisionSweeper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({ChunkAwareBlockCollisionSweeper.class})
public abstract class ChunkAwareBlockCollisionSweeperMixin {
   @Redirect(
      method = {"computeNext()Lnet/minecraft/util/shape/VoxelShape;"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/block/BlockState;getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;"
      )
   )
   private VoxelShape onComputeNextCollisionBox(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
      VoxelShape shape = state.getCollisionShape(world, pos, context);
      if (world != Minecraft.getInstance().level) {
         return shape;
      } else {
         CollisionShapeEvent event = MeteorClient.EVENT_BUS.post(CollisionShapeEvent.get(state, pos, shape));
         return event.isCancelled() ? Shapes.empty() : event.shape;
      }
   }
}
