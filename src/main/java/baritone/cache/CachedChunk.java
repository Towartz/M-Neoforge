package baritone.cache;

import baritone.api.utils.BlockUtils;
import baritone.utils.pathing.PathingBlockType;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;

public final class CachedChunk {
   public static final ImmutableSet<Block> BLOCKS_TO_KEEP_TRACK_OF = ImmutableSet.of(
      Blocks.ENDER_CHEST,
      Blocks.FURNACE,
      Blocks.CHEST,
      Blocks.TRAPPED_CHEST,
      Blocks.END_PORTAL,
      Blocks.END_PORTAL_FRAME,
      new Block[]{
         Blocks.SPAWNER,
         Blocks.BARRIER,
         Blocks.OBSERVER,
         Blocks.WHITE_SHULKER_BOX,
         Blocks.ORANGE_SHULKER_BOX,
         Blocks.MAGENTA_SHULKER_BOX,
         Blocks.LIGHT_BLUE_SHULKER_BOX,
         Blocks.YELLOW_SHULKER_BOX,
         Blocks.LIME_SHULKER_BOX,
         Blocks.PINK_SHULKER_BOX,
         Blocks.GRAY_SHULKER_BOX,
         Blocks.LIGHT_GRAY_SHULKER_BOX,
         Blocks.CYAN_SHULKER_BOX,
         Blocks.PURPLE_SHULKER_BOX,
         Blocks.BLUE_SHULKER_BOX,
         Blocks.BROWN_SHULKER_BOX,
         Blocks.GREEN_SHULKER_BOX,
         Blocks.RED_SHULKER_BOX,
         Blocks.BLACK_SHULKER_BOX,
         Blocks.NETHER_PORTAL,
         Blocks.HOPPER,
         Blocks.BEACON,
         Blocks.BREWING_STAND,
         Blocks.CREEPER_HEAD,
         Blocks.CREEPER_WALL_HEAD,
         Blocks.DRAGON_HEAD,
         Blocks.DRAGON_WALL_HEAD,
         Blocks.PLAYER_HEAD,
         Blocks.PLAYER_WALL_HEAD,
         Blocks.ZOMBIE_HEAD,
         Blocks.ZOMBIE_WALL_HEAD,
         Blocks.SKELETON_SKULL,
         Blocks.SKELETON_WALL_SKULL,
         Blocks.WITHER_SKELETON_SKULL,
         Blocks.WITHER_SKELETON_WALL_SKULL,
         Blocks.ENCHANTING_TABLE,
         Blocks.ANVIL,
         Blocks.WHITE_BED,
         Blocks.ORANGE_BED,
         Blocks.MAGENTA_BED,
         Blocks.LIGHT_BLUE_BED,
         Blocks.YELLOW_BED,
         Blocks.LIME_BED,
         Blocks.PINK_BED,
         Blocks.GRAY_BED,
         Blocks.LIGHT_GRAY_BED,
         Blocks.CYAN_BED,
         Blocks.PURPLE_BED,
         Blocks.BLUE_BED,
         Blocks.BROWN_BED,
         Blocks.GREEN_BED,
         Blocks.RED_BED,
         Blocks.BLACK_BED,
         Blocks.DRAGON_EGG,
         Blocks.JUKEBOX,
         Blocks.END_GATEWAY,
         Blocks.COBWEB,
         Blocks.NETHER_WART,
         Blocks.LADDER,
         Blocks.VINE
      }
   );
   public final int height;
   public final int size;
   public final int sizeInBytes;
   public final int x;
   public final int z;
   private final BitSet data;
   private final Int2ObjectOpenHashMap<String> special;
   private final BlockState[] overview;
   private final int[] heightMap;
   private final Map<String, List<BlockPos>> specialBlockLocations;
   public final long cacheTimestamp;

   CachedChunk(int x, int z, int height, BitSet data, BlockState[] overview, Map<String, List<BlockPos>> specialBlockLocations, long cacheTimestamp) {
      this.size = size(height);
      this.sizeInBytes = sizeInBytes(this.size);
      this.validateSize(data);
      this.x = x;
      this.z = z;
      this.height = height;
      this.data = data;
      this.overview = overview;
      this.heightMap = new int[256];
      this.specialBlockLocations = specialBlockLocations;
      this.cacheTimestamp = cacheTimestamp;
      if (specialBlockLocations.isEmpty()) {
         this.special = null;
      } else {
         this.special = new Int2ObjectOpenHashMap();
         this.setSpecial();
      }

      this.calculateHeightMap();
   }

   public static int size(int dimension_height) {
      return 512 * dimension_height;
   }

   public static int sizeInBytes(int size) {
      return size / 8;
   }

   private final void setSpecial() {
      for (Entry<String, List<BlockPos>> entry : this.specialBlockLocations.entrySet()) {
         for (BlockPos pos : entry.getValue()) {
            this.special.put(getPositionIndex(pos.getX(), pos.getY(), pos.getZ()), entry.getKey());
         }
      }
   }

   public final BlockState getBlock(int x, int y, int z, DimensionType dimension) {
      int index = getPositionIndex(x, y, z);
      PathingBlockType type = this.getType(index);
      int internalPos = z << 4 | x;
      if (this.heightMap[internalPos] == y && type != PathingBlockType.AVOID) {
         return this.overview[internalPos];
      } else {
         if (this.special != null) {
            String str = (String)this.special.get(index);
            if (str != null) {
               return BlockUtils.stringToBlockRequired(str).defaultBlockState();
            }
         }

         if (type == PathingBlockType.SOLID) {
            if (y == dimension.logicalHeight() - 1 && dimension.hasCeiling()) {
               return Blocks.BEDROCK.defaultBlockState();
            }

            if (y < -59 && dimension.natural()) {
               return Blocks.OBSIDIAN.defaultBlockState();
            }
         }

         return ChunkPacker.pathingTypeToBlock(type, dimension);
      }
   }

   private PathingBlockType getType(int index) {
      return PathingBlockType.fromBits(this.data.get(index), this.data.get(index + 1));
   }

   private void calculateHeightMap() {
      for (int z = 0; z < 16; z++) {
         for (int x = 0; x < 16; x++) {
            int index = z << 4 | x;
            this.heightMap[index] = 0;

            for (int y = this.height; y >= 0; y--) {
               int i = getPositionIndex(x, y, z);
               if (this.data.get(i) || this.data.get(i + 1)) {
                  this.heightMap[index] = y;
                  break;
               }
            }
         }
      }
   }

   public final BlockState[] getOverview() {
      return this.overview;
   }

   public final Map<String, List<BlockPos>> getRelativeBlocks() {
      return this.specialBlockLocations;
   }

   public final ArrayList<BlockPos> getAbsoluteBlocks(String blockType) {
      if (this.specialBlockLocations.get(blockType) == null) {
         return null;
      } else {
         ArrayList<BlockPos> res = new ArrayList<>();

         for (BlockPos pos : this.specialBlockLocations.get(blockType)) {
            res.add(new BlockPos(pos.getX() + this.x * 16, pos.getY(), pos.getZ() + this.z * 16));
         }

         return res;
      }
   }

   public final byte[] toByteArray() {
      return this.data.toByteArray();
   }

   public static int getPositionIndex(int x, int y, int z) {
      return x << 1 | z << 5 | y << 9;
   }

   private void validateSize(BitSet data) {
      if (data.size() > this.size) {
         throw new IllegalArgumentException("BitSet of invalid length provided");
      }
   }
}
