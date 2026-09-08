package baritone.api.pathing.goals;

import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.SettingsUtil;
import baritone.api.utils.interfaces.IGoalRenderPos;
import net.minecraft.core.BlockPos;

public class GoalGetToBlock implements Goal, IGoalRenderPos {
   public final int x;
   public final int y;
   public final int z;

   public GoalGetToBlock(BlockPos pos) {
      this.x = pos.getX();
      this.y = pos.getY();
      this.z = pos.getZ();
   }

   @Override
   public BlockPos getGoalPos() {
      return new BlockPos(this.x, this.y, this.z);
   }

   @Override
   public boolean isInGoal(int x, int y, int z) {
      int xDiff = x - this.x;
      int yDiff = y - this.y;
      int zDiff = z - this.z;
      return Math.abs(xDiff) + Math.abs(yDiff < 0 ? yDiff + 1 : yDiff) + Math.abs(zDiff) <= 1;
   }

   @Override
   public double heuristic(int x, int y, int z) {
      int xDiff = x - this.x;
      int yDiff = y - this.y;
      int zDiff = z - this.z;
      return GoalBlock.calculate((double)xDiff, yDiff < 0 ? yDiff + 1 : yDiff, (double)zDiff);
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         GoalGetToBlock goal = (GoalGetToBlock)o;
         return this.x == goal.x && this.y == goal.y && this.z == goal.z;
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return (int)BetterBlockPos.longHash(this.x, this.y, this.z) * -49639096;
   }

   @Override
   public String toString() {
      return String.format(
         "GoalGetToBlock{x=%s,y=%s,z=%s}", SettingsUtil.maybeCensor(this.x), SettingsUtil.maybeCensor(this.y), SettingsUtil.maybeCensor(this.z)
      );
   }
}
