package baritone.api.process;

import baritone.api.schematic.ISchematic;
import java.io.File;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;

public interface IBuilderProcess extends IBaritoneProcess {
   void build(String var1, ISchematic var2, Vec3i var3);

   boolean build(String var1, File var2, Vec3i var3);

   @Deprecated
   default boolean build(String schematicFile, BlockPos origin) {
      File file = new File(new File(Minecraft.getInstance().gameDirectory, "schematics"), schematicFile);
      return this.build(schematicFile, file, origin);
   }

   void buildOpenSchematic();

   void buildOpenLitematic(int var1);

   void pause();

   boolean isPaused();

   void resume();

   void clearArea(BlockPos var1, BlockPos var2);

   List<BlockState> getApproxPlaceable();

   Optional<Integer> getMinLayer();

   Optional<Integer> getMaxLayer();
}
