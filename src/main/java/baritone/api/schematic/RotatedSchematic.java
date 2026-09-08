package baritone.api.schematic;

import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

public class RotatedSchematic implements ISchematic {
   private final ISchematic schematic;
   private final Rotation rotation;
   private final Rotation inverseRotation;

   public RotatedSchematic(ISchematic schematic, Rotation rotation) {
      this.schematic = schematic;
      this.rotation = rotation;
      this.inverseRotation = rotation.getRotated(rotation).getRotated(rotation);
   }

   @Override
   public boolean inSchematic(int x, int y, int z, BlockState currentState) {
      return this.schematic
         .inSchematic(
            rotateX(x, z, this.widthX(), this.lengthZ(), this.inverseRotation),
            y,
            rotateZ(x, z, this.widthX(), this.lengthZ(), this.inverseRotation),
            rotate(currentState, this.inverseRotation)
         );
   }

   @Override
   public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
      return rotate(
         this.schematic
            .desiredState(
               rotateX(x, z, this.widthX(), this.lengthZ(), this.inverseRotation),
               y,
               rotateZ(x, z, this.widthX(), this.lengthZ(), this.inverseRotation),
               rotate(current, this.inverseRotation),
               rotate(approxPlaceable, this.inverseRotation)
            ),
         this.rotation
      );
   }

   @Override
   public void reset() {
      this.schematic.reset();
   }

   @Override
   public int widthX() {
      return flipsCoordinates(this.rotation) ? this.schematic.lengthZ() : this.schematic.widthX();
   }

   @Override
   public int heightY() {
      return this.schematic.heightY();
   }

   @Override
   public int lengthZ() {
      return flipsCoordinates(this.rotation) ? this.schematic.widthX() : this.schematic.lengthZ();
   }

   private static boolean flipsCoordinates(Rotation rotation) {
      return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
   }

   private static int rotateX(int x, int z, int sizeX, int sizeZ, Rotation rotation) {
      switch (rotation) {
         case NONE:
            return x;
         case CLOCKWISE_90:
            return sizeZ - z - 1;
         case CLOCKWISE_180:
            return sizeX - x - 1;
         case COUNTERCLOCKWISE_90:
            return z;
         default:
            throw new IllegalArgumentException("Unknown rotation");
      }
   }

   private static int rotateZ(int x, int z, int sizeX, int sizeZ, Rotation rotation) {
      switch (rotation) {
         case NONE:
            return z;
         case CLOCKWISE_90:
            return x;
         case CLOCKWISE_180:
            return sizeZ - z - 1;
         case COUNTERCLOCKWISE_90:
            return sizeX - x - 1;
         default:
            throw new IllegalArgumentException("Unknown rotation");
      }
   }

   private static BlockState rotate(BlockState state, Rotation rotation) {
      return state == null ? null : state.rotate(rotation);
   }

   private static List<BlockState> rotate(List<BlockState> states, Rotation rotation) {
      return states == null ? null : states.stream().map(s -> rotate(s, rotation)).collect(Collectors.toList());
   }
}
