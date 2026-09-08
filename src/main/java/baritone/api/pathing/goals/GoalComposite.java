package baritone.api.pathing.goals;

import java.util.Arrays;

public class GoalComposite implements Goal {
   private final Goal[] goals;

   public GoalComposite(Goal... goals) {
      this.goals = goals;
   }

   @Override
   public boolean isInGoal(int x, int y, int z) {
      for (Goal goal : this.goals) {
         if (goal.isInGoal(x, y, z)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public double heuristic(int x, int y, int z) {
      double min = Double.MAX_VALUE;

      for (Goal g : this.goals) {
         min = Math.min(min, g.heuristic(x, y, z));
      }

      return min;
   }

   @Override
   public double heuristic() {
      double min = Double.MAX_VALUE;

      for (Goal g : this.goals) {
         min = Math.min(min, g.heuristic());
      }

      return min;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         GoalComposite goal = (GoalComposite)o;
         return Arrays.equals((Object[])this.goals, (Object[])goal.goals);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Arrays.hashCode((Object[])this.goals);
   }

   @Override
   public String toString() {
      return "GoalComposite" + Arrays.toString((Object[])this.goals);
   }

   public Goal[] goals() {
      return this.goals;
   }
}
