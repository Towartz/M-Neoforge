package baritone.process.elytra;

import baritone.Baritone;
import baritone.api.event.events.BlockChangeEvent;
import baritone.utils.accessor.IPalettedContainer;
import dev.babbaj.pathfinder.NetherPathfinder;
import dev.babbaj.pathfinder.Octree;
import dev.babbaj.pathfinder.PathSegment;
import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.phys.Vec3;
import sun.misc.Unsafe;

public final class NetherPathfinderContext implements IElytraPathFinder {
   private static final Unsafe UNSAFE;
   private static final BlockState AIR_BLOCK_STATE;
   public final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
   public final ReadLock readLock = this.rwl.readLock();
   public final WriteLock writeLock = this.rwl.writeLock();
   private final int maxHeight;
   final long context;
   private final long seed;
   private final ExecutorService writeExecutor = Executors.newSingleThreadExecutor();
   private final ExecutorService readExecutor = Executors.newSingleThreadExecutor();
   private final ResourceKey<Level> dimension;
   final int minY;
   private final BlockStateOctreeInterface boi;

   public NetherPathfinderContext(long seed, Path cache, Level world) {
      this.dimension = world.dimension();
      this.minY = world.dimensionType().minY();
      int dim;
      if (this.dimension == Level.NETHER) {
         dim = NetherPathfinder.DIMENSION_NETHER;
      } else if (this.dimension == Level.END) {
         dim = NetherPathfinder.DIMENSION_END;
      } else {
         dim = NetherPathfinder.DIMENSION_OVERWORLD;
      }

      int height = Math.min(world.dimensionType().height(), 384);
      if (!Baritone.settings().elytraAllowAboveRoof.value && dim == NetherPathfinder.DIMENSION_NETHER) {
         height = Math.min(height, 128);
      }

      this.maxHeight = height;
      this.context = NetherPathfinder.newContext(seed, cache != null ? cache.toString() : null, dim, height, Baritone.settings().elytraCustomAllocator.value);
      this.seed = seed;
      this.boi = new BlockStateOctreeInterface(this);
   }

   public boolean hasChunk(ChunkPos pos) {
      return NetherPathfinder.hasChunkFromJava(this.context, pos.x, pos.z);
   }

   public void queueCacheCulling(int chunkX, int chunkZ, int maxDistanceBlocks) {
      this.writeExecutor.execute(() -> {
         this.writeLock.lock();

         try {
            this.boi.chunkPtr = 0L;
            NetherPathfinder.cullFarChunks(this.context, chunkX, chunkZ, maxDistanceBlocks);
         } finally {
            this.writeLock.unlock();
         }
      });
   }

   public void queueForPacking(LevelChunk chunkIn) {
      SoftReference<LevelChunk> ref = new SoftReference<>(chunkIn);
      this.writeExecutor.execute(() -> {
         LevelChunk chunk = ref.get();
         if (chunk != null) {
            this.writeLock.lock();

            try {
               this.boi.chunkPtr = 0L;
               long ptr = NetherPathfinder.allocateAndInsertChunk(this.context, chunk.getPos().x, chunk.getPos().z);
               writeChunkData(chunk, ptr);
            } finally {
               this.writeLock.unlock();
            }
         }
      });
   }

   public void queueBlockUpdate(BlockChangeEvent event) {
      this.writeExecutor.execute(() -> {
         ChunkPos chunkPos = event.getChunkPos();
         this.writeLock.lock();

         try {
            long ptr = NetherPathfinder.getChunk(this.context, chunkPos.x, chunkPos.z);
            if (ptr != 0L) {
               event.getBlocks().forEach(pair -> {
                  BlockPos pos = pair.first().below(this.minY);
                  if (pos.getY() >= 0 && pos.getY() < 384) {
                     boolean isSolid = pair.second() != AIR_BLOCK_STATE;
                     Octree.setBlock(ptr, pos.getX() & 15, pos.getY(), pos.getZ() & 15, isSolid);
                  }
               });
               return;
            }
         } finally {
            this.writeLock.unlock();
         }
      });
   }

   @Override
   public CompletableFuture<UnpackedSegment> pathFindAsync(BlockPos src, BlockPos dst) {
      BlockPos adjustedSrc = src.below(this.minY);
      BlockPos adjustedDst = dst.below(this.minY);
      boolean generate = Baritone.settings().elytraPredictTerrain.value && this.dimension == Level.NETHER;
      Lock l = (Lock)(generate ? this.writeLock : this.readLock);
      ExecutorService exec = generate ? this.writeExecutor : this.readExecutor;
      return CompletableFuture.supplyAsync(
         () -> {
            l.lock();

            UnpackedSegment var6x;
            try {
               PathSegment segment = NetherPathfinder.pathFind(
                  this.context,
                  adjustedSrc.getX(),
                  adjustedSrc.getY(),
                  adjustedSrc.getZ(),
                  adjustedDst.getX(),
                  adjustedDst.getY(),
                  adjustedDst.getZ(),
                  !Baritone.settings().elytraAllowTightSpaces.value,
                  false,
                  10000,
                  !generate,
                  8.0
               );
               if (segment == null) {
                  throw new PathCalculationException("Path calculation failed");
               }

               var6x = new UnpackedSegment(UnpackedSegment.from(segment).collect().stream().map(pos -> pos.above(this.minY)), segment.finished);
            } finally {
               l.unlock();
            }

            return var6x;
         },
         exec
      );
   }

   public boolean raytrace(double startX, double startY, double startZ, double endX, double endY, double endZ) {
      double adjustedStartY = startY - (double)this.minY;
      double adjustedEndY = endY - (double)this.minY;
      return NetherPathfinder.isVisible(this.context, NetherPathfinder.CACHE_MISS_SOLID, startX, adjustedStartY, startZ, endX, adjustedEndY, endZ);
   }

   public boolean raytrace(Vec3 start, Vec3 end) {
      Vec3 adjustedStart = start.subtract(0.0, (double)this.minY, 0.0);
      Vec3 adjustedEnd = end.subtract(0.0, (double)this.minY, 0.0);
      return NetherPathfinder.isVisible(
         this.context, NetherPathfinder.CACHE_MISS_SOLID, adjustedStart.x, adjustedStart.y, adjustedStart.z, adjustedEnd.x, adjustedEnd.y, adjustedEnd.z
      );
   }

   public boolean raytrace(int count, double[] src, double[] dst, int visibility) {
      if (src.length == count * 3 && dst.length == count * 3) {
         for (int i = 1; i < src.length; i += 3) {
            src[i] -= (double)this.minY;
            dst[i] -= (double)this.minY;
         }

         switch (visibility) {
            case 0:
               return NetherPathfinder.isVisibleMulti(this.context, NetherPathfinder.CACHE_MISS_SOLID, count, src, dst, false) == -1;
            case 1:
               return NetherPathfinder.isVisibleMulti(this.context, NetherPathfinder.CACHE_MISS_SOLID, count, src, dst, true) == -1;
            case 2:
               return NetherPathfinder.isVisibleMulti(this.context, NetherPathfinder.CACHE_MISS_SOLID, count, src, dst, true) != -1;
            default:
               throw new IllegalArgumentException("lol");
         }
      } else {
         throw new IllegalArgumentException("Bad array lengths");
      }
   }

   public void raytrace(int count, double[] src, double[] dst, boolean[] hitsOut, double[] hitPosOut) {
      if (src.length == count * 3 && dst.length == count * 3) {
         for (int i = 1; i < src.length; i += 3) {
            src[i] -= (double)this.minY;
            dst[i] -= (double)this.minY;
         }

         NetherPathfinder.raytrace(this.context, NetherPathfinder.CACHE_MISS_SOLID, count, src, dst, hitsOut, hitPosOut);
      } else {
         throw new IllegalArgumentException("Bad array lengths");
      }
   }

   public boolean passable(int x, int y, int z) {
      return !this.boi.get0(x, y, z);
   }

   public void cancel() {
      NetherPathfinder.cancel(this.context);
   }

   public void destroy() {
      this.cancel();
      this.readExecutor.shutdownNow();
      this.writeExecutor.shutdownNow();

      try {
         while (!this.readExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)) {
         }

         while (!this.writeExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)) {
         }
      } catch (InterruptedException var2) {
         var2.printStackTrace();
      }

      NetherPathfinder.freeContext(this.context);
   }

   public long getSeed() {
      return this.seed;
   }

   public void acquireReadLock() {
      this.readLock.lock();
   }

   public boolean tryAcquireReadLock() {
      return this.readLock.tryLock();
   }

   public void releaseReadLock() {
      this.readLock.unlock();
   }

   public int getMaxHeight() {
      return this.maxHeight;
   }

   private static void writeChunkData(LevelChunk chunk, long chunkPtr) {
      try {
         LevelChunkSection[] chunkInternalStorageArray = chunk.getSections();
         int maxSections = Math.min(chunkInternalStorageArray.length, 24);

         for (int y0 = 0; y0 < maxSections; y0++) {
            LevelChunkSection extendedblockstorage = chunkInternalStorageArray[y0];
            if (extendedblockstorage != null && !extendedblockstorage.hasOnlyAir()) {
               PalettedContainer<BlockState> bsc = extendedblockstorage.getStates();
               Palette<BlockState> palette = ((IPalettedContainer)bsc).getPalette();
               int airId = -1;
               int caveAirId = -1;
               int redMushroomId = -1;
               int brownMushroomId = -1;

               for (int i = 0; i < palette.getSize(); i++) {
                  BlockState bs = (BlockState)palette.valueFor(i);
                  if (bs == Blocks.AIR.defaultBlockState()) {
                     airId = i;
                  } else if (bs == Blocks.CAVE_AIR.defaultBlockState()) {
                     caveAirId = i;
                  } else if (bs == Blocks.RED_MUSHROOM.defaultBlockState()) {
                     redMushroomId = i;
                  } else if (bs == Blocks.BROWN_MUSHROOM.defaultBlockState()) {
                     brownMushroomId = i;
                  }
               }

               if (airId == -1 & caveAirId == -1) {
                  long bytesInSection = 512L;
                  UNSAFE.setMemory(chunkPtr + (long)y0 * 512L, 512L, (byte)-1);
               } else {
                  BitStorage array = ((IPalettedContainer)bsc).getStorage();
                  if (array != null) {
                     long[] longArray = array.getRaw();
                     int arraySize = array.getSize();
                     int bitsPerEntry = array.getBits();
                     long maxEntryValue = (1L << bitsPerEntry) - 1L;
                     int yReal = y0 << 4;
                     int ix = 0;

                     for (int idx = 0; ix < longArray.length && idx < arraySize; ix++) {
                        long l = longArray[ix];

                        for (int offset = 0; offset <= 64 - bitsPerEntry && idx < arraySize; idx++) {
                           int value = (int)(l >> offset & maxEntryValue);
                           int x = idx & 15;
                           int y = yReal + (idx >> 8);
                           int z = idx >> 4 & 15;
                           if (!(value == airId | value == caveAirId) & value != redMushroomId & value != brownMushroomId) {
                              Octree.setBlock(chunkPtr, x, y, z, true);
                           }

                           offset += bitsPerEntry;
                        }
                     }
                  }
               }
            }
         }
      } catch (Exception var29) {
         var29.printStackTrace();
         throw new RuntimeException(var29);
      }
   }

   public static boolean isSupported() {
      return NetherPathfinder.isThisSystemSupported();
   }

   static {
      try {
         Field f = Unsafe.class.getDeclaredField("theUnsafe");
         f.setAccessible(true);
         UNSAFE = (Unsafe)f.get(null);
      } catch (Exception var1) {
         throw new RuntimeException(var1);
      }

      AIR_BLOCK_STATE = Blocks.AIR.defaultBlockState();
   }

   public static final class Visibility {
      public static final int ALL = 0;
      public static final int NONE = 1;
      public static final int ANY = 2;

      private Visibility() {
      }
   }
}
