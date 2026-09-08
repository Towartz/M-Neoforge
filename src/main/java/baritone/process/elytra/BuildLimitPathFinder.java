package baritone.process.elytra;

import baritone.Baritone;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.IPlayerContext;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap.Types;

public class BuildLimitPathFinder implements IElytraPathFinder {
   final int flightLevel;
   final IPlayerContext playerCtx;
   final NetherPathfinderContext netherCtx;

   public BuildLimitPathFinder(IPlayerContext ctx, NetherPathfinderContext netherCtx) {
      if (ctx == null) {
         throw new IllegalArgumentException("IPlayerContext cannot be null");
      } else {
         this.playerCtx = ctx;
         if (netherCtx == null) {
            throw new IllegalArgumentException("NetherPathfinderContext cannot be null");
         } else {
            this.flightLevel = ctx.world().getMaxBuildHeight() + 16;
            this.netherCtx = netherCtx;
            if (netherCtx.getMaxHeight() + ctx.world().getMinBuildHeight() < ctx.world().getMaxBuildHeight()) {
               throw new IllegalStateException("Nether pathfinder max height is below world build limit, cannot proceed");
            }
         }
      }
   }

   public Tuple<List<BetterBlockPos>, Boolean> generateDirectPath(BetterBlockPos start, BetterBlockPos destination, int bufferDistance, int maxPathSize) {
      LinkedList<BetterBlockPos> path = new LinkedList<>();
      int stepDistance = 32;
      BetterBlockPos startFixed = start.y == this.flightLevel ? start : new BetterBlockPos(start.getX(), this.flightLevel, start.getZ());
      BetterBlockPos destinationFixed = destination.y == this.flightLevel
         ? destination
         : new BetterBlockPos(destination.getX(), this.flightLevel, destination.getZ());
      BetterBlockPos cur = startFixed;
      path.add(startFixed);

      while (path.size() < maxPathSize) {
         double deltaX = (double)(destinationFixed.getX() - cur.getX());
         double deltaZ = (double)(destinationFixed.getZ() - cur.getZ());
         double remainingDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
         double remainingDistanceSq = deltaX * deltaX + deltaZ * deltaZ;
         if (remainingDistanceSq <= (double)(bufferDistance * bufferDistance)) {
            return new Tuple(path, true);
         }

         if (remainingDistance <= 32.0) {
            path.add(destinationFixed);
            return new Tuple(path, true);
         }

         double stepRatio = 32.0 / remainingDistance;
         int nextX = (int)Math.round((double)cur.getX() + deltaX * stepRatio);
         int nextZ = (int)Math.round((double)cur.getZ() + deltaZ * stepRatio);
         cur = new BetterBlockPos(nextX, this.flightLevel, nextZ);
         path.add(cur);
      }

      return new Tuple(path, false);
   }

   public Tuple<List<BetterBlockPos>, Boolean> generateTransitionUp(BetterBlockPos start, BetterBlockPos destination) {
      double deltaX = (double)(destination.getX() - start.getX());
      double deltaZ = (double)(destination.getZ() - start.getZ());
      double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
      double scale = 8.0 / distance;
      double stepX = deltaX * scale;
      double stepZ = deltaZ * scale;
      int netherMaxHeight = this.netherCtx.getMaxHeight() + this.playerCtx.world().getMinBuildHeight() - 1;
      ChunkPos startChunk = new ChunkPos(start.x >> 4, start.z >> 4);
      if (!this.isSkyClear(startChunk, start.y)) {
         return new Tuple(new LinkedList(), false);
      } else {
         LinkedList<BetterBlockPos> path = new LinkedList<>();
         BlockPos middlePos = startChunk.getMiddleBlockPosition(netherMaxHeight + 4);

         for (int i = 2; i <= 2; i++) {
            BetterBlockPos next = new BetterBlockPos(
               (int)((double)middlePos.getX() + stepX * (double)i), netherMaxHeight + i * 8, (int)((double)middlePos.getZ() + stepZ * (double)i)
            );
            path.add(next);
         }

         return new Tuple(path, true);
      }
   }

   public Tuple<List<BetterBlockPos>, Boolean> generateTransitionDown(BetterBlockPos start) {
      int netherMaxHeight = this.netherCtx.getMaxHeight() + this.playerCtx.world().getMinBuildHeight() - 1;
      ChunkPos startChunk = new ChunkPos(start.x >> 4, start.z >> 4);
      LinkedList<BetterBlockPos> path = new LinkedList<>();
      if (!this.isSkyClear(new ChunkPos(start.x >> 4, start.z >> 4), netherMaxHeight - 16)) {
         return new Tuple(new LinkedList(), false);
      } else {
         path.add(new BetterBlockPos(startChunk.getMiddleBlockPosition(netherMaxHeight - 8)));
         return new Tuple(path, true);
      }
   }

   public boolean isSkyClear(ChunkPos pos, int y) {
      if (!this.playerCtx.world().getChunkSource().hasChunk(pos.x, pos.z)) {
         return false;
      } else {
         for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
               BlockPos blockPos = pos.getBlockAt(x, y, z);
               int height = this.playerCtx.world().getHeight(Types.MOTION_BLOCKING, blockPos.getX(), blockPos.getZ());
               if (height > y) {
                  return false;
               }
            }
         }

         return true;
      }
   }

   @Override
   public CompletableFuture<UnpackedSegment> pathFindAsync(BlockPos src, BlockPos dst) {
      int netherMaxHeight = this.netherCtx.getMaxHeight() + this.playerCtx.world().getMinBuildHeight() - 1;
      int maxDirectPathSize = 500;
      double maxDistance = Baritone.settings().elytraLongDistanceThreshold.value >= 32
         ? (double)Baritone.settings().elytraLongDistanceThreshold.value.intValue()
         : Double.POSITIVE_INFINITY;
      double distanceXZ = src.distSqr(new Vec3i(dst.getX(), src.getY(), dst.getZ()));
      boolean isLongDistance = distanceXZ > maxDistance * maxDistance;
      boolean srcAboveSupportedHeight = src.getY() >= netherMaxHeight;
      boolean dstAboveSupportedHeight = dst.getY() >= netherMaxHeight;
      if (srcAboveSupportedHeight && dstAboveSupportedHeight) {
         Tuple<List<BetterBlockPos>, Boolean> path = this.generateDirectPath(new BetterBlockPos(src), new BetterBlockPos(dst), 0, 500);
         return CompletableFuture.completedFuture(new UnpackedSegment(((List)path.getA()).stream(), (Boolean)path.getB()));
      } else if (isLongDistance) {
         if (srcAboveSupportedHeight) {
            Tuple<List<BetterBlockPos>, Boolean> directPath = this.generateDirectPath(new BetterBlockPos(src), new BetterBlockPos(dst), (int)maxDistance, 500);
            return CompletableFuture.completedFuture(
               new UnpackedSegment(((List)directPath.getA()).stream(), dstAboveSupportedHeight ? (Boolean)directPath.getB() : false)
            );
         } else {
            Tuple<List<BetterBlockPos>, Boolean> transition = this.generateTransitionUp(new BetterBlockPos(src), new BetterBlockPos(dst));
            List<BetterBlockPos> path = (List<BetterBlockPos>)transition.getA();
            Boolean success = (Boolean)transition.getB();
            if (success) {
               Tuple<List<BetterBlockPos>, Boolean> directPath = this.generateDirectPath(
                  path.get(path.size() - 1), new BetterBlockPos(dst), (int)maxDistance, 500
               );
               path.addAll((Collection<? extends BetterBlockPos>)directPath.getA());
               return CompletableFuture.completedFuture(new UnpackedSegment(path.stream(), dstAboveSupportedHeight ? (Boolean)directPath.getB() : false));
            } else {
               double deltaX = (double)(dst.getX() - src.getX());
               double deltaZ = (double)(dst.getZ() - src.getZ());
               double scale = maxDistance / 2.0 / Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
               double stepX = deltaX * scale;
               double stepZ = deltaZ * scale;
               BlockPos midDst = new BlockPos((int)((double)src.getX() + stepX), netherMaxHeight, (int)((double)src.getZ() + stepZ));
               return this.incompletePathfind(src, midDst);
            }
         }
      } else if (srcAboveSupportedHeight) {
         Tuple<List<BetterBlockPos>, Boolean> transition = this.generateTransitionDown(new BetterBlockPos(src));
         List<BetterBlockPos> path = (List<BetterBlockPos>)transition.getA();
         boolean success = (Boolean)transition.getB();
         if (!success) {
            BetterBlockPos newDest = distanceXZ > 32.0
               ? new BetterBlockPos(dst)
               : new BetterBlockPos(dst.getX(), this.playerCtx.world().getMaxBuildHeight(), dst.getZ());
            Tuple<List<BetterBlockPos>, Boolean> directPath = this.generateDirectPath(new BetterBlockPos(src), newDest, 0, 2);
            return CompletableFuture.completedFuture(new UnpackedSegment(((List)directPath.getA()).stream(), (Boolean)directPath.getB()));
         } else {
            return CompletableFuture.supplyAsync(() -> {
               UnpackedSegment np = this.blockingPathFind(path.get(path.size() - 1), dst);
               path.addAll(np.collect());
               return new UnpackedSegment(path.stream(), np.isFinished());
            });
         }
      } else if (dstAboveSupportedHeight) {
         Tuple<List<BetterBlockPos>, Boolean> transition = this.generateTransitionUp(new BetterBlockPos(src), new BetterBlockPos(dst));
         List<BetterBlockPos> path = (List<BetterBlockPos>)transition.getA();
         Boolean success = (Boolean)transition.getB();
         if (success) {
            Tuple<List<BetterBlockPos>, Boolean> directPath = this.generateDirectPath(path.get(path.size() - 1), new BetterBlockPos(dst), 0, 500);
            path.addAll((Collection<? extends BetterBlockPos>)directPath.getA());
            return CompletableFuture.completedFuture(new UnpackedSegment(path.stream(), (Boolean)directPath.getB()));
         } else {
            return this.netherCtx.pathFindAsync(src, new BetterBlockPos(dst.getX(), netherMaxHeight, dst.getZ()));
         }
      } else {
         return this.netherCtx.pathFindAsync(src, dst);
      }
   }

   private CompletableFuture<UnpackedSegment> incompletePathfind(BlockPos src, BlockPos dst) {
      return CompletableFuture.supplyAsync(() -> {
         UnpackedSegment packed = this.blockingPathFind(src, dst);
         return new UnpackedSegment(packed.collect().stream(), false);
      });
   }

   private UnpackedSegment blockingPathFind(BlockPos src, BlockPos dst) {
      try {
         return this.netherCtx.pathFindAsync(src, dst).get();
      } catch (InterruptedException var5) {
         Thread.currentThread().interrupt();
         throw new RuntimeException(var5);
      } catch (ExecutionException var6) {
         Throwable cause = var6.getCause();
         if (cause instanceof PathCalculationException) {
            throw (PathCalculationException)cause;
         } else {
            throw new RuntimeException(var6);
         }
      }
   }
}
