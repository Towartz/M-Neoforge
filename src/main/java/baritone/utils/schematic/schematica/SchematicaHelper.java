package baritone.utils.schematic.schematica;

import baritone.api.schematic.IStaticSchematic;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Tuple;

public final class SchematicaHelper {
   public static boolean isSchematicaPresent() {
      return false;
   }

   public static Optional<Tuple<IStaticSchematic, BlockPos>> getOpenSchematic() {
      return Optional.empty();
   }
}
