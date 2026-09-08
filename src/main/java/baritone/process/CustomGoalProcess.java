package baritone.process;

import baritone.Baritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.process.ICustomGoalProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.utils.BaritoneProcessHelper;
import net.minecraft.ChatFormatting;

public final class CustomGoalProcess extends BaritoneProcessHelper implements ICustomGoalProcess {
   private Goal goal;
   private Goal mostRecentGoal;
   private CustomGoalProcess.State state;

   public CustomGoalProcess(Baritone baritone) {
      super(baritone);
   }

   @Override
   public void setGoal(Goal goal) {
      this.goal = goal;
      this.mostRecentGoal = goal;
      if (this.baritone.getElytraProcess().isActive()) {
         try {
            this.baritone.getElytraProcess().pathTo(goal);
         } catch (IllegalArgumentException var3) {
            this.logDirect("Failed to update elytra goal because: " + var3.getMessage(), ChatFormatting.RED);
         }
      }

      if (this.state == CustomGoalProcess.State.NONE) {
         this.state = CustomGoalProcess.State.GOAL_SET;
      }

      if (this.state == CustomGoalProcess.State.EXECUTING) {
         this.state = CustomGoalProcess.State.PATH_REQUESTED;
      }
   }

   @Override
   public void path() {
      this.state = CustomGoalProcess.State.PATH_REQUESTED;
   }

   @Override
   public Goal getGoal() {
      return this.goal;
   }

   @Override
   public Goal mostRecentGoal() {
      return this.mostRecentGoal;
   }

   @Override
   public boolean isActive() {
      return this.state != CustomGoalProcess.State.NONE;
   }

   @Override
   public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
      switch (this.state) {
         case GOAL_SET:
            return new PathingCommand(this.goal, PathingCommandType.CANCEL_AND_SET_GOAL);
         case PATH_REQUESTED:
            PathingCommand ret = new PathingCommand(this.goal, PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH);
            this.state = CustomGoalProcess.State.EXECUTING;
            return ret;
         case EXECUTING:
            if (calcFailed) {
               this.onLostControl();
               return new PathingCommand(this.goal, PathingCommandType.CANCEL_AND_SET_GOAL);
            } else {
               if (this.goal == null || this.goal.isInGoal(this.ctx.playerFeet()) && this.goal.isInGoal(this.baritone.getPathingBehavior().pathStart())) {
                  this.onLostControl();
                  if (Baritone.settings().disconnectOnArrival.value) {
                     this.ctx.world().disconnect();
                  }

                  if (Baritone.settings().notificationOnPathComplete.value) {
                     this.logNotification("Pathing complete", false);
                  }

                  return new PathingCommand(this.goal, PathingCommandType.CANCEL_AND_SET_GOAL);
               }

               return new PathingCommand(this.goal, PathingCommandType.SET_GOAL_AND_PATH);
            }
         default:
            throw new IllegalStateException("Unexpected state " + this.state);
      }
   }

   @Override
   public void onLostControl() {
      this.state = CustomGoalProcess.State.NONE;
      this.goal = null;
   }

   @Override
   public String displayName0() {
      return "Custom Goal " + this.goal;
   }

   protected static enum State {
      NONE,
      GOAL_SET,
      PATH_REQUESTED,
      EXECUTING;
   }
}
