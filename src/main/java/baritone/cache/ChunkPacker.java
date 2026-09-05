package baritone.cache;

import baritone.api.utils.BlockUtils;
import baritone.pathing.movement.MovementHelper;
import baritone.utils.pathing.PathingBlockType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;

import java.util.*;

import static baritone.utils.BlockStateInterface.getFromChunk;

/**
 * Clean Java 21 implementation of ChunkPacker for NeoForge.
 * Replaces enum switch with direct if-else checks to prevent synthetic inner classes ($1)
 * that fail to load under ModuleClassLoader during dimension transitions.
 */
public final class ChunkPacker {

    private ChunkPacker() {}

    public static CachedChunk pack(LevelChunk chunk) {
        Map<String, List<BlockPos>> specialBlocks = new HashMap<>();
        final int height = chunk.getLevel().dimensionType().height();
        BitSet bitSet = new BitSet(CachedChunk.size(height));
        try {
            LevelChunkSection[] chunkInternalStorageArray = chunk.getSections();
            for (int y0 = 0; y0 < height / 16; y0++) {
                LevelChunkSection extendedblockstorage = chunkInternalStorageArray[y0];
                if (extendedblockstorage == null) {
                    continue;
                }
                PalettedContainer<BlockState> bsc = extendedblockstorage.getStates();
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            int adjY = y0 * 16 + y;
                            BlockState state = bsc.get(x, y, z);
                            Block block = state.getBlock();
                            if (CachedChunk.BLOCKS_TO_KEEP_TRACK_OF.contains(block)) {
                                String blockName = BlockUtils.blockToString(block);
                                specialBlocks.computeIfAbsent(blockName, k -> new ArrayList<>()).add(new BlockPos(x, adjY + chunk.getMinBuildHeight(), z));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        BlockState[] surface = new BlockState[256];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                for (int y = height - 1; y >= 0; y--) {
                    int index = CachedChunk.getPositionIndex(x, y, z);
                    if (bitSet.get(index) || bitSet.get(index + 1)) {
                        surface[(z << 4) | x] = getFromChunk(chunk, x, y, z);
                        break;
                    }
                    if (y == 0) {
                        surface[(z << 4) | x] = Blocks.AIR.defaultBlockState();
                    }
                }
            }
        }
        return new CachedChunk(chunk.getPos().x, chunk.getPos().z, height, bitSet, surface, specialBlocks, System.currentTimeMillis());
    }

    private static PathingBlockType getPathingBlockType(BlockState state, LevelChunk chunk, int x, int y, int z) {
        Block block = state.getBlock();
        if (state.liquid()) {
            int adjY = y + chunk.getMinBuildHeight();
            if (MovementHelper.possiblyFlowing(state)
                    || MovementHelper.possiblyFlowing(getFromChunk(chunk, x, adjY + 1, z))
                    || (x != 15 && MovementHelper.possiblyFlowing(getFromChunk(chunk, x + 1, adjY, z)))
                    || (x != 0 && MovementHelper.possiblyFlowing(getFromChunk(chunk, x - 1, adjY, z)))
                    || (z != 15 && MovementHelper.possiblyFlowing(getFromChunk(chunk, x, adjY, z + 1)))
                    || (z != 0 && MovementHelper.possiblyFlowing(getFromChunk(chunk, x, adjY, z - 1)))
            ) {
                return PathingBlockType.AVOID;
            }
            if (x == 0 || x == 15 || z == 0 || z == 15) {
                Vec3 flow = state.getFluidState().getFlow(chunk.getLevel(), new BlockPos(x + (chunk.getPos().x << 4), y, z + (chunk.getPos().z << 4)));
                if (flow.x != 0.0 || flow.z != 0.0) {
                    return PathingBlockType.WATER;
                }
                return PathingBlockType.AVOID;
            }
            return PathingBlockType.WATER;
        }

        if (MovementHelper.avoidWalkingInto(state) || MovementHelper.isBottomSlab(state)) {
            return PathingBlockType.AVOID;
        }
        if (block instanceof AirBlock || block instanceof TallGrassBlock || block instanceof DoublePlantBlock || block instanceof FlowerBlock) {
            return PathingBlockType.AIR;
        }

        return PathingBlockType.SOLID;
    }

    public static BlockState pathingTypeToBlock(PathingBlockType type, DimensionType dimension) {
        if (type == null) return null;
        if (type == PathingBlockType.AIR) {
            return Blocks.AIR.defaultBlockState();
        }
        if (type == PathingBlockType.WATER) {
            return Blocks.WATER.defaultBlockState();
        }
        if (type == PathingBlockType.AVOID) {
            return Blocks.LAVA.defaultBlockState();
        }
        if (type == PathingBlockType.SOLID) {
            if (dimension != null) {
                if (dimension.natural()) {
                    return Blocks.STONE.defaultBlockState();
                }
                if (dimension.ultraWarm()) {
                    return Blocks.NETHERRACK.defaultBlockState();
                }
                if (BuiltinDimensionTypes.END_EFFECTS.equals(dimension.effectsLocation())) {
                    return Blocks.END_STONE.defaultBlockState();
                }
            }
            return Blocks.STONE.defaultBlockState();
        }
        return null;
    }
}
