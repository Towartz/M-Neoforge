package baritone.api.schematic;

import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;

public class MirroredSchematic implements ISchematic {
   private final ISchematic schematic;
   private final Mirror mirror;

   public MirroredSchematic(ISchematic schematic, Mirror mirror) {
      this.schematic = schematic;
      this.mirror = mirror;
   }

   @Override
   public boolean inSchematic(int x, int y, int z, BlockState currentState) {
      return this.schematic.inSchematic(mirrorX(x, this.widthX(), this.mirror), y, mirrorZ(z, this.lengthZ(), this.mirror), mirror(currentState, this.mirror));
   }

   @Override
   public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
      return mirror(
         this.schematic
            .desiredState(
               mirrorX(x, this.widthX(), this.mirror),
               y,
               mirrorZ(z, this.lengthZ(), this.mirror),
               mirror(current, this.mirror),
               mirror(approxPlaceable, this.mirror)
            ),
         this.mirror
      );
   }

   @Override
   public void reset() {
      this.schematic.reset();
   }

   @Override
   public int widthX() {
      return this.schematic.widthX();
   }

   @Override
   public int heightY() {
      return this.schematic.heightY();
   }

   @Override
   public int lengthZ() {
      return this.schematic.lengthZ();
   }

   private static int mirrorX(int x, int sizeX, Mirror mirror) {
      switch (mirror) {
         case NONE:
         case LEFT_RIGHT:
            return x;
         case FRONT_BACK:
            return sizeX - x - 1;
         default:
            throw new IllegalArgumentException("Unknown mirror");
      }
   }

   private static int mirrorZ(int z, int sizeZ, Mirror mirror) {
      switch (mirror) {
         case NONE:
         case FRONT_BACK:
            return z;
         case LEFT_RIGHT:
            return sizeZ - z - 1;
         default:
            throw new IllegalArgumentException("Unknown mirror");
      }
   }

   private static BlockState mirror(BlockState state, Mirror mirror) {
      return state == null ? null : state.mirror(mirror);
   }

   private static List<BlockState> mirror(List<BlockState> states, Mirror mirror) {
      return states == null ? null : states.stream().map(s -> mirror(s, mirror)).collect(Collectors.toList());
   }
}
