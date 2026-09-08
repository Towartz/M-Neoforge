package baritone.api.process;

import baritone.api.pathing.goals.Goal;
import baritone.api.utils.BetterBlockPos;
import java.util.List;
import net.minecraft.core.BlockPos;

public interface IElytraProcess extends IBaritoneProcess {
   void repackChunks();

   BlockPos currentDestination();

   List<BetterBlockPos> getPath();

   void pathTo(BlockPos var1);

   void pathTo(Goal var1);

   void resetState();

   boolean isLoaded();

   boolean isSafeToCancel();
}
