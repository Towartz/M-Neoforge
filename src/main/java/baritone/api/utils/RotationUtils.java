package baritone.api.utils;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import java.util.Optional;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class RotationUtils {
   public static final double DEG_TO_RAD = Math.PI / 180.0;
   public static final float DEG_TO_RAD_F = (float) (Math.PI / 180.0);
   public static final double RAD_TO_DEG = 180.0 / Math.PI;
   public static final float RAD_TO_DEG_F = (float) (180.0 / Math.PI);
   private static final Vec3[] BLOCK_SIDE_MULTIPLIERS = new Vec3[]{
      new Vec3(0.5, 0.0, 0.5), new Vec3(0.5, 1.0, 0.5), new Vec3(0.5, 0.5, 0.0), new Vec3(0.5, 0.5, 1.0), new Vec3(0.0, 0.5, 0.5), new Vec3(1.0, 0.5, 0.5)
   };

   private RotationUtils() {
   }

   public static Rotation calcRotationFromCoords(BlockPos orig, BlockPos dest) {
      return calcRotationFromVec3d(
         new Vec3((double)orig.getX(), (double)orig.getY(), (double)orig.getZ()), new Vec3((double)dest.getX(), (double)dest.getY(), (double)dest.getZ())
      );
   }

   public static Rotation wrapAnglesToRelative(Rotation current, Rotation target) {
      return current.yawIsReallyClose(target) ? new Rotation(current.getYaw(), target.getPitch()) : target.subtract(current).normalize().add(current);
   }

   public static Rotation calcRotationFromVec3d(Vec3 orig, Vec3 dest, Rotation current) {
      return wrapAnglesToRelative(current, calcRotationFromVec3d(orig, dest));
   }

   private static Rotation calcRotationFromVec3d(Vec3 orig, Vec3 dest) {
      double[] delta = new double[]{orig.x - dest.x, orig.y - dest.y, orig.z - dest.z};
      double yaw = Mth.atan2(delta[0], -delta[2]);
      double dist = Math.sqrt(delta[0] * delta[0] + delta[2] * delta[2]);
      double pitch = Mth.atan2(delta[1], dist);
      return new Rotation((float)(yaw * (180.0 / Math.PI)), (float)(pitch * (180.0 / Math.PI)));
   }

   public static Vec3 calcLookDirectionFromRotation(Rotation rotation) {
      float flatZ = Mth.cos(-rotation.getYaw() * (float) (Math.PI / 180.0) - (float) Math.PI);
      float flatX = Mth.sin(-rotation.getYaw() * (float) (Math.PI / 180.0) - (float) Math.PI);
      float pitchBase = -Mth.cos(-rotation.getPitch() * (float) (Math.PI / 180.0));
      float pitchHeight = Mth.sin(-rotation.getPitch() * (float) (Math.PI / 180.0));
      return new Vec3((double)(flatX * pitchBase), (double)pitchHeight, (double)(flatZ * pitchBase));
   }

   @Deprecated
   public static Vec3 calcVec3dFromRotation(Rotation rotation) {
      return calcLookDirectionFromRotation(rotation);
   }

   public static Optional<Rotation> reachable(IPlayerContext ctx, BlockPos pos) {
      return reachable(ctx, pos, false);
   }

   public static Optional<Rotation> reachable(IPlayerContext ctx, BlockPos pos, boolean wouldSneak) {
      return reachable(ctx, pos, ctx.playerController().getBlockReachDistance(), wouldSneak);
   }

   public static Optional<Rotation> reachable(IPlayerContext ctx, BlockPos pos, double blockReachDistance) {
      return reachable(ctx, pos, blockReachDistance, false);
   }

   public static Optional<Rotation> reachable(IPlayerContext ctx, BlockPos pos, double blockReachDistance, boolean wouldSneak) {
      if (pos instanceof BetterBlockPos) {
         pos = new BlockPos(pos.getX(), pos.getY(), pos.getZ());
      }

      if (BaritoneAPI.getSettings().remainWithExistingLookDirection.value && ctx.isLookingAt(pos)) {
         Rotation hypothetical = ctx.playerRotations().add(new Rotation(0.0F, 1.0E-4F));
         if (!wouldSneak) {
            return Optional.of(hypothetical);
         }

         HitResult result = RayTraceUtils.rayTraceTowards(ctx.player(), hypothetical, blockReachDistance, true);
         if (result != null && result.getType() == Type.BLOCK && ((BlockHitResult)result).getBlockPos().equals(pos)) {
            return Optional.of(hypothetical);
         }
      }

      Optional<Rotation> possibleRotation = reachableCenter(ctx, pos, blockReachDistance, wouldSneak);
      if (possibleRotation.isPresent()) {
         return possibleRotation;
      } else {
         BlockState state = ctx.world().getBlockState(pos);
         VoxelShape shape = state.getShape(ctx.world(), pos);
         if (shape.isEmpty()) {
            shape = Shapes.block();
         }

         for (Vec3 sideOffset : BLOCK_SIDE_MULTIPLIERS) {
            double xDiff = shape.min(Axis.X) * sideOffset.x + shape.max(Axis.X) * (1.0 - sideOffset.x);
            double yDiff = shape.min(Axis.Y) * sideOffset.y + shape.max(Axis.Y) * (1.0 - sideOffset.y);
            double zDiff = shape.min(Axis.Z) * sideOffset.z + shape.max(Axis.Z) * (1.0 - sideOffset.z);
            possibleRotation = reachableOffset(
               ctx, pos, new Vec3((double)pos.getX(), (double)pos.getY(), (double)pos.getZ()).add(xDiff, yDiff, zDiff), blockReachDistance, wouldSneak
            );
            if (possibleRotation.isPresent()) {
               return possibleRotation;
            }
         }

         return Optional.empty();
      }
   }

   public static Optional<Rotation> reachableOffset(IPlayerContext ctx, BlockPos pos, Vec3 offsetPos, double blockReachDistance, boolean wouldSneak) {
      Vec3 eyes = wouldSneak ? RayTraceUtils.inferSneakingEyePosition(ctx.player()) : ctx.player().getEyePosition(1.0F);
      Rotation rotation = calcRotationFromVec3d(eyes, offsetPos, ctx.playerRotations());
      Rotation actualRotation = BaritoneAPI.getProvider().getBaritoneForPlayer(ctx.player()).getLookBehavior().getAimProcessor().peekRotation(rotation);
      HitResult result = RayTraceUtils.rayTraceTowards(ctx.player(), actualRotation, blockReachDistance, wouldSneak);
      if (result != null && result.getType() == Type.BLOCK) {
         if (((BlockHitResult)result).getBlockPos().equals(pos)) {
            return Optional.of(rotation);
         }

         if (ctx.world().getBlockState(pos).getBlock() instanceof BaseFireBlock && ((BlockHitResult)result).getBlockPos().equals(pos.below())) {
            return Optional.of(rotation);
         }
      }

      return Optional.empty();
   }

   public static Optional<Rotation> reachableCenter(IPlayerContext ctx, BlockPos pos, double blockReachDistance, boolean wouldSneak) {
      return reachableOffset(ctx, pos, VecUtils.calculateBlockCenter(ctx.world(), pos), blockReachDistance, wouldSneak);
   }

   @Deprecated
   public static Optional<Rotation> reachable(LocalPlayer entity, BlockPos pos, double blockReachDistance) {
      return reachable(entity, pos, blockReachDistance, false);
   }

   @Deprecated
   public static Optional<Rotation> reachable(LocalPlayer entity, BlockPos pos, double blockReachDistance, boolean wouldSneak) {
      IBaritone baritone = BaritoneAPI.getProvider().getBaritoneForPlayer(entity);
      IPlayerContext ctx = baritone.getPlayerContext();
      return reachable(ctx, pos, blockReachDistance, wouldSneak);
   }

   @Deprecated
   public static Optional<Rotation> reachableOffset(Entity entity, BlockPos pos, Vec3 offsetPos, double blockReachDistance, boolean wouldSneak) {
      Vec3 eyes = wouldSneak ? RayTraceUtils.inferSneakingEyePosition(entity) : entity.getEyePosition(1.0F);
      Rotation rotation = calcRotationFromVec3d(eyes, offsetPos, new Rotation(entity.getYRot(), entity.getXRot()));
      HitResult result = RayTraceUtils.rayTraceTowards(entity, rotation, blockReachDistance, wouldSneak);
      if (result != null && result.getType() == Type.BLOCK) {
         if (((BlockHitResult)result).getBlockPos().equals(pos)) {
            return Optional.of(rotation);
         }

         if (entity.level().getBlockState(pos).getBlock() instanceof BaseFireBlock && ((BlockHitResult)result).getBlockPos().equals(pos.below())) {
            return Optional.of(rotation);
         }
      }

      return Optional.empty();
   }

   @Deprecated
   public static Optional<Rotation> reachableCenter(Entity entity, BlockPos pos, double blockReachDistance, boolean wouldSneak) {
      return reachableOffset(entity, pos, VecUtils.calculateBlockCenter(entity.level(), pos), blockReachDistance, wouldSneak);
   }
}
