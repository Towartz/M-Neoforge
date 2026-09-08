package baritone.cache;

import baritone.Baritone;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.cache.ICachedWorld;
import baritone.api.cache.IWorldData;
import baritone.api.utils.Helper;
import com.google.common.cache.CacheBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;

public final class CachedWorld implements ICachedWorld, Helper {
   private static final int REGION_MAX = 58594;
   private Long2ObjectMap<CachedRegion> cachedRegions = new Long2ObjectOpenHashMap();
   private final String directory;
   private final LinkedBlockingQueue<ChunkPos> toPackQueue = new LinkedBlockingQueue<>();
   private final Map<ChunkPos, LevelChunk> toPackMap = CacheBuilder.newBuilder().softValues().<ChunkPos, LevelChunk>build().asMap();
   private final DimensionType dimension;

   CachedWorld(Path directory, DimensionType dimension) {
      if (!Files.exists(directory)) {
         try {
            Files.createDirectories(directory);
         } catch (IOException var4) {
         }
      }

      this.directory = directory.toString();
      this.dimension = dimension;
      System.out.println("Cached world directory: " + directory);
      Baritone.getExecutor().execute(new CachedWorld.PackerThread());
      Baritone.getExecutor().execute(() -> {
         try {
            Thread.sleep(30000L);

            while (true) {
               this.save();
               Thread.sleep(600000L);
            }
         } catch (InterruptedException var2x) {
            var2x.printStackTrace();
         }
      });
   }

   @Override
   public final void queueForPacking(LevelChunk chunk) {
      if (this.toPackMap.put(chunk.getPos(), chunk) == null) {
         this.toPackQueue.add(chunk.getPos());
      }
   }

   @Override
   public final boolean isCached(int blockX, int blockZ) {
      CachedRegion region = this.getRegion(blockX >> 9, blockZ >> 9);
      return region == null ? false : region.isCached(blockX & 511, blockZ & 511);
   }

   public final boolean regionLoaded(int blockX, int blockZ) {
      return this.getRegion(blockX >> 9, blockZ >> 9) != null;
   }

   @Override
   public final ArrayList<BlockPos> getLocationsOf(String block, int maximum, int centerX, int centerZ, int maxRegionDistanceSq) {
      ArrayList<BlockPos> res = new ArrayList<>();
      int centerRegionX = centerX >> 9;
      int centerRegionZ = centerZ >> 9;

      for (int searchRadius = 0; searchRadius <= maxRegionDistanceSq; searchRadius++) {
         for (int xoff = -searchRadius; xoff <= searchRadius; xoff++) {
            for (int zoff = -searchRadius; zoff <= searchRadius; zoff++) {
               int distance = xoff * xoff + zoff * zoff;
               if (distance == searchRadius) {
                  int regionX = xoff + centerRegionX;
                  int regionZ = zoff + centerRegionZ;
                  CachedRegion region = this.getOrCreateRegion(regionX, regionZ);
                  if (region != null) {
                     res.addAll(region.getLocationsOf(block));
                  }
               }
            }
         }

         if (res.size() >= maximum) {
            return res;
         }
      }

      return res;
   }

   private void updateCachedChunk(CachedChunk chunk) {
      CachedRegion region = this.getOrCreateRegion(chunk.x >> 5, chunk.z >> 5);
      region.updateCachedChunk(chunk.x & 31, chunk.z & 31, chunk);
   }

   @Override
   public final void save() {
      if (!Baritone.settings().chunkCaching.value) {
         System.out.println("Not saving to disk; chunk caching is disabled.");
         this.allRegions().forEach(region -> {
            if (region != null) {
               region.removeExpired();
            }
         });
         this.prune();
      } else {
         long start = System.nanoTime() / 1000000L;
         this.allRegions().parallelStream().forEach(region -> {
            if (region != null) {
               region.save(this.directory);
            }
         });
         long now = System.nanoTime() / 1000000L;
         System.out.println("World save took " + (now - start) + "ms");
         this.prune();
      }
   }

   private synchronized void prune() {
      if (Baritone.settings().pruneRegionsFromRAM.value) {
         BlockPos pruneCenter = this.guessPosition();

         for (CachedRegion region : this.allRegions()) {
            if (region != null) {
               int distX = (region.getX() << 9) + 256 - pruneCenter.getX();
               int distZ = (region.getZ() << 9) + 256 - pruneCenter.getZ();
               double dist = Math.sqrt((double)(distX * distX + distZ * distZ));
               if (dist > 1024.0) {
                  this.logDebug("Deleting cached region from ram");
                  this.cachedRegions.remove(this.getRegionID(region.getX(), region.getZ()));
               }
            }
         }
      }
   }

   private BlockPos guessPosition() {
      for (IBaritone ibaritone : BaritoneAPI.getProvider().getAllBaritones()) {
         IWorldData data = ibaritone.getWorldProvider().getCurrentWorld();
         if (data != null && data.getCachedWorld() == this && ibaritone.getPlayerContext().player() != null) {
            return ibaritone.getPlayerContext().playerFeet();
         }
      }

      CachedChunk mostRecentlyModified = null;

      for (CachedRegion region : this.allRegions()) {
         if (region != null) {
            CachedChunk ch = region.mostRecentlyModified();
            if (ch != null && (mostRecentlyModified == null || mostRecentlyModified.cacheTimestamp < ch.cacheTimestamp)) {
               mostRecentlyModified = ch;
            }
         }
      }

      return mostRecentlyModified == null ? new BlockPos(0, 0, 0) : new BlockPos((mostRecentlyModified.x << 4) + 8, 0, (mostRecentlyModified.z << 4) + 8);
   }

   private synchronized List<CachedRegion> allRegions() {
      return new ArrayList<>(this.cachedRegions.values());
   }

   @Override
   public final void reloadAllFromDisk() {
      long start = System.nanoTime() / 1000000L;
      this.allRegions().forEach(region -> {
         if (region != null) {
            region.load(this.directory);
         }
      });
      long now = System.nanoTime() / 1000000L;
      System.out.println("World load took " + (now - start) + "ms");
   }

   public final synchronized CachedRegion getRegion(int regionX, int regionZ) {
      return (CachedRegion)this.cachedRegions.get(this.getRegionID(regionX, regionZ));
   }

   private synchronized CachedRegion getOrCreateRegion(int regionX, int regionZ) {
      return (CachedRegion)this.cachedRegions.computeIfAbsent(this.getRegionID(regionX, regionZ), id -> {
         CachedRegion newRegion = new CachedRegion(regionX, regionZ, this.dimension);
         try {
            newRegion.load(this.directory);
         } catch (Exception e) {
            System.err.println("Failed to load region " + regionX + "," + regionZ + ": " + e.getMessage());
         }
         return newRegion;
      });
   }

   public void tryLoadFromDisk(int regionX, int regionZ) {
      this.getOrCreateRegion(regionX, regionZ);
   }

   private long getRegionID(int regionX, int regionZ) {
      return !this.isRegionInWorld(regionX, regionZ) ? 0L : (long)regionX & 4294967295L | ((long)regionZ & 4294967295L) << 32;
   }

   private boolean isRegionInWorld(int regionX, int regionZ) {
      return regionX <= 58594 && regionX >= -58594 && regionZ <= 58594 && regionZ >= -58594;
   }

   private class PackerThread implements Runnable {
      @Override
      public void run() {
         while (true) {
            try {
               ChunkPos pos = CachedWorld.this.toPackQueue.take();
               LevelChunk chunk = CachedWorld.this.toPackMap.remove(pos);
               if (CachedWorld.this.toPackQueue.size() <= Baritone.settings().chunkPackerQueueMaxSize.value) {
                  CachedChunk cached = ChunkPacker.pack(chunk);
                  CachedWorld.this.updateCachedChunk(cached);
               }
            } catch (InterruptedException var4) {
               var4.printStackTrace();
               return;
            } catch (Throwable var5) {
               var5.printStackTrace();
            }
         }
      }
   }
}
