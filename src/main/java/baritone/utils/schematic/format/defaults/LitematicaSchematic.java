package baritone.utils.schematic.format.defaults;

import baritone.api.schematic.CompositeSchematic;
import baritone.api.schematic.IStaticSchematic;
import baritone.utils.schematic.StaticSchematic;
import java.util.Collections;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.apache.commons.lang3.Validate;

public final class LitematicaSchematic extends CompositeSchematic implements IStaticSchematic {
   public LitematicaSchematic(CompoundTag nbt) {
      super(0, 0, 0);
      this.fillInSchematic(nbt);
   }

   private static CompoundTag[] getRegions(CompoundTag nbt) {
      return nbt.getCompound("Regions").getAllKeys().stream().map(nbt.getCompound("Regions")::getCompound).toArray(CompoundTag[]::new);
   }

   private static int getMinOfSubregion(CompoundTag subReg, String s) {
      int a = subReg.getCompound("Position").getInt(s);
      int b = subReg.getCompound("Size").getInt(s);
      return Math.min(a, a + b + 1);
   }

   private static BlockState[] getBlockList(ListTag blockStatePalette) {
      BlockState[] blockList = new BlockState[blockStatePalette.size()];

      for (int i = 0; i < blockStatePalette.size(); i++) {
         Block block = (Block)BuiltInRegistries.BLOCK.get(ResourceLocation.parse(((CompoundTag)blockStatePalette.get(i)).getString("Name")));
         CompoundTag properties = ((CompoundTag)blockStatePalette.get(i)).getCompound("Properties");
         blockList[i] = getBlockState(block, properties);
      }

      return blockList;
   }

   private static BlockState getBlockState(Block block, CompoundTag properties) {
      BlockState blockState = block.defaultBlockState();

      for (Object key : properties.getAllKeys()) {
         Property<?> property = block.getStateDefinition().getProperty((String)key);
         String propertyValue = properties.getString((String)key);
         if (property != null) {
            blockState = setPropertyValue(blockState, property, propertyValue);
         }
      }

      return blockState;
   }

   private static <T extends Comparable<T>> BlockState setPropertyValue(BlockState state, Property<T> property, String value) {
      Optional<T> parsed = property.getValue(value);
      if (parsed.isPresent()) {
         return (BlockState)state.setValue(property, parsed.get());
      } else {
         throw new IllegalArgumentException("Invalid value for property " + property);
      }
   }

   private static int getBitsPerBlock(int amountOfBlockTypes) {
      return (int)Math.max(2.0, Math.ceil(Math.log((double)amountOfBlockTypes) / Math.log(2.0)));
   }

   private static long getVolume(CompoundTag subReg) {
      CompoundTag size = subReg.getCompound("Size");
      return (long)Math.abs(size.getInt("x") * size.getInt("y") * size.getInt("z"));
   }

   private static int getMinOfSchematic(CompoundTag nbt, String s) {
      int n = Integer.MAX_VALUE;

      for (CompoundTag subReg : getRegions(nbt)) {
         n = Math.min(n, getMinOfSubregion(subReg, s));
      }

      return n;
   }

   private void fillInSchematic(CompoundTag nbt) {
      Vec3i offsetMinCorner = new Vec3i(getMinOfSchematic(nbt, "x"), getMinOfSchematic(nbt, "y"), getMinOfSchematic(nbt, "z"));

      for (CompoundTag subReg : getRegions(nbt)) {
         ListTag usedBlockTypes = subReg.getList("BlockStatePalette", 10);
         BlockState[] blockList = getBlockList(usedBlockTypes);
         int bitsPerBlock = getBitsPerBlock(usedBlockTypes.size());
         long regionVolume = getVolume(subReg);
         long[] blockStateArray = subReg.getLongArray("BlockStates");
         LitematicaSchematic.LitematicaBitArray bitArray = new LitematicaSchematic.LitematicaBitArray(bitsPerBlock, regionVolume, blockStateArray);
         this.writeSubregionIntoSchematic(subReg, offsetMinCorner, blockList, bitArray);
      }
   }

   private void writeSubregionIntoSchematic(CompoundTag subReg, Vec3i offsetMinCorner, BlockState[] blockList, LitematicaSchematic.LitematicaBitArray bitArray) {
      int offsetX = getMinOfSubregion(subReg, "x") - offsetMinCorner.getX();
      int offsetY = getMinOfSubregion(subReg, "y") - offsetMinCorner.getY();
      int offsetZ = getMinOfSubregion(subReg, "z") - offsetMinCorner.getZ();
      CompoundTag size = subReg.getCompound("Size");
      int sizeX = Math.abs(size.getInt("x"));
      int sizeY = Math.abs(size.getInt("y"));
      int sizeZ = Math.abs(size.getInt("z"));
      BlockState[][][] states = new BlockState[sizeX][sizeZ][sizeY];
      int index = 0;

      for (int y = 0; y < sizeY; y++) {
         for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
               states[x][z][y] = blockList[bitArray.getAt((long)index)];
               index++;
            }
         }
      }

      this.put(new StaticSchematic(states), offsetX, offsetY, offsetZ);
   }

   @Override
   public BlockState getDirect(int x, int y, int z) {
      return this.desiredState(x, y, z, null, Collections.emptyList());
   }

   private static class LitematicaBitArray {
      private final long[] longArray;
      private final int bitsPerEntry;
      private final long maxEntryValue;
      private final long arraySize;

      public LitematicaBitArray(int bitsPerEntryIn, long arraySizeIn, @Nullable long[] longArrayIn) {
         Validate.inclusiveBetween(1L, 32L, (long)bitsPerEntryIn);
         this.arraySize = arraySizeIn;
         this.bitsPerEntry = bitsPerEntryIn;
         this.maxEntryValue = (1L << bitsPerEntryIn) - 1L;
         if (longArrayIn != null) {
            this.longArray = longArrayIn;
         } else {
            this.longArray = new long[(int)(roundUp(arraySizeIn * (long)bitsPerEntryIn, 64L) / 64L)];
         }
      }

      public static long roundUp(long number, long interval) {
         int sign = 1;
         if (interval == 0L) {
            return 0L;
         } else if (number == 0L) {
            return interval;
         } else {
            if (number < 0L) {
               sign = -1;
            }

            long i = number % (interval * (long)sign);
            return i == 0L ? number : number + interval * (long)sign - i;
         }
      }

      public int getAt(long index) {
         Validate.inclusiveBetween(0L, this.arraySize - 1L, index);
         long startOffset = index * (long)this.bitsPerEntry;
         int startArrIndex = (int)(startOffset >> 6);
         int endArrIndex = (int)((index + 1L) * (long)this.bitsPerEntry - 1L >> 6);
         int startBitOffset = (int)(startOffset & 63L);
         if (startArrIndex == endArrIndex) {
            return (int)(this.longArray[startArrIndex] >>> startBitOffset & this.maxEntryValue);
         } else {
            int endOffset = 64 - startBitOffset;
            return (int)((this.longArray[startArrIndex] >>> startBitOffset | this.longArray[endArrIndex] << endOffset) & this.maxEntryValue);
         }
      }

      public long size() {
         return this.arraySize;
      }
   }
}
