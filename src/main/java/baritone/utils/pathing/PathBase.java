package baritone.utils.pathing;

import baritone.Baritone;
import baritone.api.BaritoneAPI;
import baritone.api.pathing.calc.IPath;
import baritone.api.pathing.goals.Goal;
import baritone.pathing.path.CutoffPath;
import baritone.utils.BlockStateInterface;
import net.minecraft.core.BlockPos;

public abstract class PathBase implements IPath {
   public PathBase cutoffAtLoadedChunks(Object bsi0) {
      if (!Baritone.settings().cutoffAtLoadBoundary.value) {
         return this;
      } else {
         BlockStateInterface bsi = (BlockStateInterface)bsi0;

         for (int i = 0; i < this.positions().size(); i++) {
            BlockPos pos = this.positions().get(i);
            if (!bsi.worldContainsLoadedChunk(pos.getX(), pos.getZ())) {
               return new CutoffPath(this, i);
            }
         }

         return this;
      }
   }

   public PathBase staticCutoff(Goal destination) {
      int min = BaritoneAPI.getSettings().pathCutoffMinimumLength.value;
      if (this.length() < min) {
         return this;
      } else if (destination != null && !destination.isInGoal(this.getDest())) {
         double factor = BaritoneAPI.getSettings().pathCutoffFactor.value;
         int newLength = (int)((double)(this.length() - min) * factor) + min - 1;
         return new CutoffPath(this, newLength);
      } else {
         return this;
      }
   }
}
