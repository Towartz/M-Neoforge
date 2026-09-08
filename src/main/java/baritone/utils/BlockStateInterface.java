package baritone.utils;

import baritone.Baritone;
import baritone.api.utils.IPlayerContext;
import baritone.cache.CachedRegion;
import baritone.cache.WorldData;
import baritone.utils.accessor.IClientChunkProvider;
import baritone.utils.pathing.BetterWorldBorder;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public class BlockStateInterface {
   private final ClientChunkCache provider;
   private final WorldData worldData;
   protected final Level world;
   public final MutableBlockPos isPassableBlockPos;
   public final BlockGetter access;
   public final BetterWorldBorder worldBorder;
   private LevelChunk prev = null;
   private CachedRegion prevCached = null;
   private final boolean useTheRealWorld;
   private static final BlockState AIR = Blocks.AIR.defaultBlockState();

   public BlockStateInterface(IPlayerContext ctx) {
      this(ctx, false);
   }

   public BlockStateInterface(IPlayerContext ctx, boolean copyLoadedChunks) {
      this.world = ctx.world();
      this.worldBorder = new BetterWorldBorder(this.world.getWorldBorder());
      this.worldData = (WorldData)ctx.worldData();
      if (copyLoadedChunks) {
         this.provider = ((IClientChunkProvider)this.world.getChunkSource()).createThreadSafeCopy();
      } else {
         this.provider = (ClientChunkCache)this.world.getChunkSource();
      }

      this.useTheRealWorld = !Baritone.settings().pathThroughCachedOnly.value;
      if (!ctx.minecraft().isSameThread()) {
         throw new IllegalStateException("BlockStateInterface must be constructed on the main thread");
      } else {
         this.isPassableBlockPos = new MutableBlockPos();
         this.access = new BlockStateInterfaceAccessWrapper(this);
      }
   }

   public boolean worldContainsLoadedChunk(int blockX, int blockZ) {
      return this.provider.hasChunk(blockX >> 4, blockZ >> 4);
   }

   public static Block getBlock(IPlayerContext ctx, BlockPos pos) {
      return get(ctx, pos).getBlock();
   }

   public static BlockState get(IPlayerContext ctx, BlockPos pos) {
      return new BlockStateInterface(ctx).get0(pos.getX(), pos.getY(), pos.getZ());
   }

   public BlockState get0(BlockPos pos) {
      return this.get0(pos.getX(), pos.getY(), pos.getZ());
   }

   public BlockState get0(int x, int y, int z) {
      y -= this.world.dimensionType().minY();
      if (y >= 0 && y < this.world.dimensionType().height()) {
         if (this.useTheRealWorld) {
            LevelChunk cached = this.prev;
            if (cached != null && cached.getPos().x == x >> 4 && cached.getPos().z == z >> 4) {
               return getFromChunk(cached, x, y, z);
            }

            LevelChunk chunk = this.provider.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false);
            if (chunk != null && !chunk.isEmpty()) {
               this.prev = chunk;
               return getFromChunk(chunk, x, y, z);
            }
         }

         CachedRegion cachedx = this.prevCached;
         if (cachedx == null || cachedx.getX() != x >> 9 || cachedx.getZ() != z >> 9) {
            if (this.worldData == null) {
               return AIR;
            }

            CachedRegion region = this.worldData.cache.getRegion(x >> 9, z >> 9);
            if (region == null) {
               return AIR;
            }

            this.prevCached = region;
            cachedx = region;
         }

         BlockState type = cachedx.getBlock(x & 511, y + this.world.dimensionType().minY(), z & 511);
         return type == null ? AIR : type;
      } else {
         return AIR;
      }
   }

   public boolean isLoaded(int x, int z) {
      LevelChunk prevChunk = this.prev;
      if (prevChunk != null && prevChunk.getPos().x == x >> 4 && prevChunk.getPos().z == z >> 4) {
         return true;
      } else {
         prevChunk = this.provider.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false);
         if (prevChunk != null && !prevChunk.isEmpty()) {
            this.prev = prevChunk;
            return true;
         } else {
            CachedRegion prevRegion = this.prevCached;
            if (prevRegion != null && prevRegion.getX() == x >> 9 && prevRegion.getZ() == z >> 9) {
               return prevRegion.isCached(x & 511, z & 511);
            } else if (this.worldData == null) {
               return false;
            } else {
               prevRegion = this.worldData.cache.getRegion(x >> 9, z >> 9);
               if (prevRegion == null) {
                  return false;
               } else {
                  this.prevCached = prevRegion;
                  return prevRegion.isCached(x & 511, z & 511);
               }
            }
         }
      }
   }

   public static BlockState getFromChunk(LevelChunk chunk, int x, int y, int z) {
      LevelChunkSection section = chunk.getSections()[y >> 4];
      return section.hasOnlyAir() ? AIR : section.getBlockState(x & 15, y & 15, z & 15);
   }
}
