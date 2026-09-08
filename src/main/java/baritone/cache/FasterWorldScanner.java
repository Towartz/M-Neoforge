package baritone.cache;

import baritone.api.cache.ICachedWorld;
import baritone.api.cache.IWorldScanner;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.BlockOptionalMeta;
import baritone.api.utils.BlockOptionalMetaLookup;
import baritone.api.utils.IPlayerContext;
import baritone.utils.accessor.IPalettedContainer;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.GlobalPalette;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.SingleValuePalette;

public enum FasterWorldScanner implements IWorldScanner {
   INSTANCE;

   private static final BlockState[] PALETTE_REGISTRY_SENTINEL = new BlockState[0];

   @Override
   public List<BlockPos> scanChunkRadius(IPlayerContext ctx, BlockOptionalMetaLookup filter, int max, int yLevelThreshold, int maxSearchRadius) {
      assert ctx.world() != null;

      if (maxSearchRadius < 0) {
         throw new IllegalArgumentException("chunkRange must be >= 0");
      } else {
         return this.scanChunksInternal(ctx, filter, getChunkRange(ctx.playerFeet().x >> 4, ctx.playerFeet().z >> 4, maxSearchRadius), max);
      }
   }

   @Override
   public List<BlockPos> scanChunk(IPlayerContext ctx, BlockOptionalMetaLookup filter, ChunkPos pos, int max, int yLevelThreshold) {
      Stream<BlockPos> stream = this.scanChunkInternal(ctx, filter, pos);
      if (max >= 0) {
         stream = stream.limit((long)max);
      }

      return stream.collect(Collectors.toList());
   }

   @Override
   public int repack(IPlayerContext ctx) {
      return this.repack(ctx, 40);
   }

   @Override
   public int repack(IPlayerContext ctx, int range) {
      ChunkSource chunkProvider = ctx.world().getChunkSource();
      ICachedWorld cachedWorld = ctx.worldData().getCachedWorld();
      BetterBlockPos playerPos = ctx.playerFeet();
      int playerChunkX = playerPos.getX() >> 4;
      int playerChunkZ = playerPos.getZ() >> 4;
      int minX = playerChunkX - range;
      int minZ = playerChunkZ - range;
      int maxX = playerChunkX + range;
      int maxZ = playerChunkZ + range;
      int queued = 0;

      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            LevelChunk chunk = chunkProvider.getChunk(x, z, false);
            if (chunk != null && !chunk.isEmpty()) {
               queued++;
               cachedWorld.queueForPacking(chunk);
            }
         }
      }

      return queued;
   }

   public static List<ChunkPos> getChunkRange(int centerX, int centerZ, int chunkRadius) {
      List<ChunkPos> chunks = new ArrayList<>();
      chunks.add(new ChunkPos(centerX, centerZ));

      for (int i = 1; i < chunkRadius; i++) {
         for (int j = 0; j <= i; j++) {
            chunks.add(new ChunkPos(centerX - j, centerZ - i));
            if (j != 0) {
               chunks.add(new ChunkPos(centerX + j, centerZ - i));
               chunks.add(new ChunkPos(centerX - j, centerZ + i));
            }

            chunks.add(new ChunkPos(centerX + j, centerZ + i));
            if (j != i) {
               chunks.add(new ChunkPos(centerX - i, centerZ - j));
               chunks.add(new ChunkPos(centerX + i, centerZ - j));
               if (j != 0) {
                  chunks.add(new ChunkPos(centerX - i, centerZ + j));
                  chunks.add(new ChunkPos(centerX + i, centerZ + j));
               }
            }
         }
      }

      return chunks;
   }

   private List<BlockPos> scanChunksInternal(IPlayerContext ctx, BlockOptionalMetaLookup lookup, List<ChunkPos> chunkPositions, int maxBlocks) {
      assert ctx.world() != null;

      try {
         Stream<BlockPos> posStream = chunkPositions.parallelStream().flatMap(p -> this.scanChunkInternal(ctx, lookup, p));
         if (maxBlocks >= 0) {
            posStream = posStream.limit((long)maxBlocks);
         }

         return posStream.collect(Collectors.toList());
      } catch (Exception var6) {
         var6.printStackTrace();
         throw var6;
      }
   }

   private Stream<BlockPos> scanChunkInternal(IPlayerContext ctx, BlockOptionalMetaLookup lookup, ChunkPos pos) {
      ChunkSource chunkProvider = ctx.world().getChunkSource();
      if (!chunkProvider.hasChunk(pos.x, pos.z)) {
         return Stream.empty();
      } else {
         long chunkX = (long)pos.x << 4;
         long chunkZ = (long)pos.z << 4;
         int playerSectionY = ctx.playerFeet().y - ctx.world().getMinBuildHeight() >> 4;
         return this.collectChunkSections(lookup, chunkProvider.getChunk(pos.x, pos.z, false), chunkX, chunkZ, playerSectionY).stream();
      }
   }

   private List<BlockPos> collectChunkSections(BlockOptionalMetaLookup lookup, LevelChunk chunk, long chunkX, long chunkZ, int playerSection) {
      List<BlockPos> blocks = new ArrayList<>();
      int chunkY = chunk.getMinBuildHeight();
      LevelChunkSection[] sections = chunk.getSections();
      int l = sections.length;
      int i = playerSection - 1;

      for (int j = playerSection; i >= 0 || j < l; i--) {
         if (j < l) {
            this.visitSection(lookup, sections[j], blocks, chunkX, chunkY + j * 16, chunkZ);
         }

         if (i >= 0) {
            this.visitSection(lookup, sections[i], blocks, chunkX, chunkY + i * 16, chunkZ);
         }

         j++;
      }

      return blocks;
   }

   private void visitSection(BlockOptionalMetaLookup lookup, LevelChunkSection section, List<BlockPos> blocks, long chunkX, int sectionY, long chunkZ) {
      if (section != null && !section.hasOnlyAir()) {
         PalettedContainer<BlockState> sectionContainer = section.getStates();
         if (((IPalettedContainer)sectionContainer).getStorage() != null) {
            Palette<BlockState> palette = ((IPalettedContainer)sectionContainer).getPalette();
            if (palette instanceof SingleValuePalette) {
               if (lookup.has((BlockState)palette.valueFor(0))) {
                  for (int x = 0; x < 16; x++) {
                     for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                           blocks.add(new BlockPos((int)chunkX + x, sectionY + y, (int)chunkZ + z));
                        }
                     }
                  }
               }
            } else {
               boolean[] isInFilter = this.getIncludedFilterIndices(lookup, palette);
               if (isInFilter.length != 0) {
                  BitStorage array = ((IPalettedContainer)section.getStates()).getStorage();
                  long[] longArray = array.getRaw();
                  int arraySize = array.getSize();
                  int bitsPerEntry = array.getBits();
                  long maxEntryValue = (1L << bitsPerEntry) - 1L;
                  int i = 0;

                  for (int idx = 0; i < longArray.length && idx < arraySize; i++) {
                     long l = longArray[i];

                     for (int offset = 0; offset <= 64 - bitsPerEntry && idx < arraySize; idx++) {
                        int value = (int)(l >> offset & maxEntryValue);
                        if (isInFilter[value]) {
                           blocks.add(new BlockPos((int)chunkX + (idx & 0xFF & 15), sectionY + (idx >> 8), (int)chunkZ + ((idx & 0xFF) >> 4)));
                        }

                        offset += bitsPerEntry;
                     }
                  }
               }
            }
         }
      }
   }

   private boolean[] getIncludedFilterIndices(BlockOptionalMetaLookup lookup, Palette<BlockState> palette) {
      boolean commonBlockFound = false;
      BlockState[] paletteMap = getPalette(palette);
      if (paletteMap == PALETTE_REGISTRY_SENTINEL) {
         return this.getIncludedFilterIndicesFromRegistry(lookup);
      } else {
         int size = paletteMap.length;
         boolean[] isInFilter = new boolean[size];

         for (int i = 0; i < size; i++) {
            BlockState state = paletteMap[i];
            if (lookup.has(state)) {
               isInFilter[i] = true;
               commonBlockFound = true;
            } else {
               isInFilter[i] = false;
            }
         }

         return !commonBlockFound ? new boolean[0] : isInFilter;
      }
   }

   private boolean[] getIncludedFilterIndicesFromRegistry(BlockOptionalMetaLookup lookup) {
      boolean[] isInFilter = new boolean[Block.BLOCK_STATE_REGISTRY.size()];

      for (BlockOptionalMeta bom : lookup.blocks()) {
         for (BlockState state : bom.getAllBlockStates()) {
            isInFilter[Block.BLOCK_STATE_REGISTRY.getId(state)] = true;
         }
      }

      return isInFilter;
   }

   private static BlockState[] getPalette(Palette<BlockState> palette) {
      if (palette instanceof GlobalPalette) {
         return PALETTE_REGISTRY_SENTINEL;
      } else {
         FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
         palette.write(buf);
         int size = buf.readVarInt();
         BlockState[] states = new BlockState[size];

         for (int i = 0; i < size; i++) {
            BlockState state = (BlockState)Block.BLOCK_STATE_REGISTRY.byId(buf.readVarInt());

            assert state != null;

            states[i] = state;
         }

         return states;
      }
   }
}
