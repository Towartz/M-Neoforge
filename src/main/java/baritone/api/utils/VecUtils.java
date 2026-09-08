package baritone.api.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class VecUtils {
   private VecUtils() {
   }

   public static Vec3 calculateBlockCenter(Level world, BlockPos pos) {
      if (pos instanceof BetterBlockPos) {
         pos = new BlockPos(pos.getX(), pos.getY(), pos.getZ());
      }

      BlockState b = world.getBlockState(pos);
      VoxelShape shape = b.getCollisionShape(world, pos);
      if (shape.isEmpty()) {
         return getBlockPosCenter(pos);
      } else {
         double xDiff = (shape.min(Axis.X) + shape.max(Axis.X)) / 2.0;
         double yDiff = (shape.min(Axis.Y) + shape.max(Axis.Y)) / 2.0;
         double zDiff = (shape.min(Axis.Z) + shape.max(Axis.Z)) / 2.0;
         if (!Double.isNaN(xDiff) && !Double.isNaN(yDiff) && !Double.isNaN(zDiff)) {
            if (b.getBlock() instanceof BaseFireBlock) {
               yDiff = 0.0;
            }

            return new Vec3((double)pos.getX() + xDiff, (double)pos.getY() + yDiff, (double)pos.getZ() + zDiff);
         } else {
            throw new IllegalStateException(b + " " + pos + " " + shape);
         }
      }
   }

   public static Vec3 getBlockPosCenter(BlockPos pos) {
      return new Vec3((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5);
   }

   public static double distanceToCenter(BlockPos pos, double x, double y, double z) {
      double xdiff = (double)pos.getX() + 0.5 - x;
      double ydiff = (double)pos.getY() + 0.5 - y;
      double zdiff = (double)pos.getZ() + 0.5 - z;
      return Math.sqrt(xdiff * xdiff + ydiff * ydiff + zdiff * zdiff);
   }

   public static double entityDistanceToCenter(Entity entity, BlockPos pos) {
      return distanceToCenter(pos, entity.position().x, entity.position().y, entity.position().z);
   }

   public static double entityFlatDistanceToCenter(Entity entity, BlockPos pos) {
      return distanceToCenter(pos, entity.position().x, (double)pos.getY() + 0.5, entity.position().z);
   }
}
