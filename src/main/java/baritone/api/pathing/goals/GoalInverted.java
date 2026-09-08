package baritone.api.pathing.goals;

import java.util.Objects;

public class GoalInverted implements Goal {
   public final Goal origin;

   public GoalInverted(Goal origin) {
      this.origin = origin;
   }

   @Override
   public boolean isInGoal(int x, int y, int z) {
      return false;
   }

   @Override
   public double heuristic(int x, int y, int z) {
      return -this.origin.heuristic(x, y, z);
   }

   @Override
   public double heuristic() {
      return Double.NEGATIVE_INFINITY;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         GoalInverted goal = (GoalInverted)o;
         return Objects.equals(this.origin, goal.origin);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return this.origin.hashCode() * 495796690;
   }

   @Override
   public String toString() {
      return String.format("GoalInverted{%s}", this.origin.toString());
   }
}
