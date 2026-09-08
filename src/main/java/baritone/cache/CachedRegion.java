package baritone.cache;

import baritone.Baritone;
import baritone.api.cache.ICachedRegion;
import baritone.api.utils.BlockUtils;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;

public final class CachedRegion implements ICachedRegion {
   private static final byte CHUNK_NOT_PRESENT = 0;
   private static final byte CHUNK_PRESENT = 1;
   private static final int CACHED_REGION_MAGIC = 456022911;
   private final CachedChunk[][] chunks = new CachedChunk[32][32];
   private final int x;
   private final int z;
   private final DimensionType dimension;
   private boolean hasUnsavedChanges;

   CachedRegion(int x, int z, DimensionType dimension) {
      this.x = x;
      this.z = z;
      this.hasUnsavedChanges = false;
      this.dimension = dimension;
   }

   @Override
   public final BlockState getBlock(int x, int y, int z) {
      int adjY = y - this.dimension.minY();
      CachedChunk chunk = this.chunks[x >> 4][z >> 4];
      return chunk != null ? chunk.getBlock(x & 15, adjY, z & 15, this.dimension) : null;
   }

   @Override
   public final boolean isCached(int x, int z) {
      return this.chunks[x >> 4][z >> 4] != null;
   }

   public final ArrayList<BlockPos> getLocationsOf(String block) {
      ArrayList<BlockPos> res = new ArrayList<>();

      for (int chunkX = 0; chunkX < 32; chunkX++) {
         for (int chunkZ = 0; chunkZ < 32; chunkZ++) {
            if (this.chunks[chunkX][chunkZ] != null) {
               ArrayList<BlockPos> locs = this.chunks[chunkX][chunkZ].getAbsoluteBlocks(block);
               if (locs != null) {
                  res.addAll(locs);
               }
            }
         }
      }

      return res;
   }

   public final synchronized void updateCachedChunk(int chunkX, int chunkZ, CachedChunk chunk) {
      this.chunks[chunkX][chunkZ] = chunk;
      this.hasUnsavedChanges = true;
   }

   public final synchronized void save(String directory) {
      if (this.hasUnsavedChanges) {
         this.removeExpired();

         try {
            Path path = Paths.get(directory);
            if (!Files.exists(path)) {
               Files.createDirectories(path);
            }

            System.out.println("Saving region " + this.x + "," + this.z + " to disk " + path);
            Path regionFile = getRegionFile(path, this.x, this.z);
            if (!Files.exists(regionFile)) {
               Files.createFile(regionFile);
            }

            try (
               FileOutputStream fileOut = new FileOutputStream(regionFile.toFile());
               GZIPOutputStream gzipOut = new GZIPOutputStream(fileOut, 16384);
               DataOutputStream out = new DataOutputStream(gzipOut);
            ) {
               out.writeInt(456022911);

               for (int x = 0; x < 32; x++) {
                  for (int z = 0; z < 32; z++) {
                     CachedChunk chunk = this.chunks[x][z];
                     if (chunk == null) {
                        out.write(0);
                     } else {
                        out.write(1);
                        byte[] chunkBytes = chunk.toByteArray();
                        out.write(chunkBytes);
                        out.write(new byte[chunk.sizeInBytes - chunkBytes.length]);
                     }
                  }
               }

               for (int x = 0; x < 32; x++) {
                  for (int zx = 0; zx < 32; zx++) {
                     if (this.chunks[x][zx] != null) {
                        for (int i = 0; i < 256; i++) {
                           out.writeUTF(BlockUtils.blockToString(this.chunks[x][zx].getOverview()[i].getBlock()));
                        }
                     }
                  }
               }

               for (int x = 0; x < 32; x++) {
                  for (int zxx = 0; zxx < 32; zxx++) {
                     if (this.chunks[x][zxx] != null) {
                        Map<String, List<BlockPos>> locs = this.chunks[x][zxx].getRelativeBlocks();
                        out.writeShort(locs.entrySet().size());

                        for (Entry<String, List<BlockPos>> entry : locs.entrySet()) {
                           out.writeUTF(entry.getKey());
                           out.writeShort(entry.getValue().size());

                           for (BlockPos pos : entry.getValue()) {
                              out.writeByte((byte)(pos.getZ() << 4 | pos.getX()));
                              out.writeInt(pos.getY() - this.dimension.minY());
                           }
                        }
                     }
                  }
               }

               for (int x = 0; x < 32; x++) {
                  for (int zxxx = 0; zxxx < 32; zxxx++) {
                     if (this.chunks[x][zxxx] != null) {
                        out.writeLong(this.chunks[x][zxxx].cacheTimestamp);
                     }
                  }
               }
            }

            this.hasUnsavedChanges = false;
            System.out.println("Saved region successfully");
         } catch (Exception var20) {
            var20.printStackTrace();
         }
      }
   }

   public synchronized void load(String directory) {
      try {
         Path path = Paths.get(directory);
         if (!Files.exists(path)) {
            Files.createDirectories(path);
         }

         Path regionFile = getRegionFile(path, this.x, this.z);
         if (!Files.exists(regionFile)) {
            return;
         }

         System.out.println("Loading region " + this.x + "," + this.z + " from disk " + path);
         long start = System.nanoTime() / 1000000L;

         try (
            FileInputStream fileIn = new FileInputStream(regionFile.toFile());
            GZIPInputStream gzipIn = new GZIPInputStream(fileIn, 32768);
            DataInputStream in = new DataInputStream(gzipIn);
         ) {
            int magic = in.readInt();
            if (magic != 456022911) {
               throw new IOException("Bad magic value " + magic);
            }

            boolean[][] present = new boolean[32][32];
            BitSet[][] bitSets = new BitSet[32][32];
            Map<String, List<BlockPos>>[][] location = new Map[32][32];
            BlockState[][][] overview = new BlockState[32][32][];
            long[][] cacheTimestamp = new long[32][32];

            for (int x = 0; x < 32; x++) {
               for (int z = 0; z < 32; z++) {
                  int isChunkPresent = in.read();
                  switch (isChunkPresent) {
                     case 1:
                        byte[] bytes = new byte[CachedChunk.sizeInBytes(CachedChunk.size(this.dimension.height()))];
                        in.readFully(bytes);
                        bitSets[x][z] = BitSet.valueOf(bytes);
                        location[x][z] = new HashMap<>();
                        overview[x][z] = new BlockState[256];
                        present[x][z] = true;
                        break;
                     case 0:
                        break;
                     default:
                        throw new IOException("Malformed stream: unexpected byte " + isChunkPresent);
                  }
               }
            }

            for (int x = 0; x < 32; x++) {
               for (int z = 0; z < 32; z++) {
                  if (present[x][z]) {
                     for (int i = 0; i < 256; i++) {
                        overview[x][z][i] = BlockUtils.stringToBlockRequired(in.readUTF()).defaultBlockState();
                     }
                  }
               }
            }

            for (int x = 0; x < 32; x++) {
               for (int zx = 0; zx < 32; zx++) {
                  if (present[x][zx]) {
                     int numSpecialBlockTypes = in.readShort() & '\uffff';

                     for (int i = 0; i < numSpecialBlockTypes; i++) {
                        String blockName = in.readUTF();
                        BlockUtils.stringToBlockRequired(blockName);
                        List<BlockPos> locs = new ArrayList<>();
                        location[x][zx].put(blockName, locs);
                        int numLocations = in.readShort() & '\uffff';
                        if (numLocations == 0) {
                           numLocations = 65536;
                        }

                        for (int j = 0; j < numLocations; j++) {
                           byte xz = in.readByte();
                           int X = xz & 15;
                           int Z = xz >>> 4 & 15;
                           int Y = in.readInt();
                           locs.add(new BlockPos(X, Y + this.dimension.minY(), Z));
                        }
                     }
                  }
               }
            }

            for (int x = 0; x < 32; x++) {
               for (int zxx = 0; zxx < 32; zxx++) {
                  if (present[x][zxx]) {
                     cacheTimestamp[x][zxx] = in.readLong();
                  }
               }
            }

            for (int x = 0; x < 32; x++) {
               for (int zxxx = 0; zxxx < 32; zxxx++) {
                  if (present[x][zxxx]) {
                     int regionX = this.x;
                     int regionZ = this.z;
                     int chunkX = x + 32 * regionX;
                     int chunkZ = zxxx + 32 * regionZ;
                     this.chunks[x][zxxx] = new CachedChunk(
                        chunkX, chunkZ, this.dimension.height(), bitSets[x][zxxx], overview[x][zxxx], location[x][zxxx], cacheTimestamp[x][zxxx]
                     );
                  }
               }
            }
         }

         this.removeExpired();
         this.hasUnsavedChanges = false;
         long var34 = System.nanoTime() / 1000000L;
         System.out.println("Loaded region successfully in " + (var34 - start) + "ms");
      } catch (Exception var33) {
         var33.printStackTrace();
      }
   }

   public final synchronized void removeExpired() {
      long expiry = Baritone.settings().cachedChunksExpirySeconds.value;
      if (expiry >= 0L) {
         long now = System.currentTimeMillis();
         long oldestAcceptableAge = now - expiry * 1000L;

         for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
               if (this.chunks[x][z] != null && this.chunks[x][z].cacheTimestamp < oldestAcceptableAge) {
                  System.out
                     .println(
                        "Removing chunk "
                           + (x + 32 * this.x)
                           + ","
                           + (z + 32 * this.z)
                           + " because it was cached "
                           + (now - this.chunks[x][z].cacheTimestamp) / 1000L
                           + " seconds ago, and max age is "
                           + expiry
                     );
                  this.chunks[x][z] = null;
               }
            }
         }
      }
   }

   public final synchronized CachedChunk mostRecentlyModified() {
      CachedChunk recent = null;

      for (int x = 0; x < 32; x++) {
         for (int z = 0; z < 32; z++) {
            if (this.chunks[x][z] != null && (recent == null || this.chunks[x][z].cacheTimestamp > recent.cacheTimestamp)) {
               recent = this.chunks[x][z];
            }
         }
      }

      return recent;
   }

   @Override
   public final int getX() {
      return this.x;
   }

   @Override
   public final int getZ() {
      return this.z;
   }

   private static Path getRegionFile(Path cacheDir, int regionX, int regionZ) {
      return Paths.get(cacheDir.toString(), "r." + regionX + "." + regionZ + ".bcr");
   }
}
