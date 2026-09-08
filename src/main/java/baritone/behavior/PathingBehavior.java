package baritone.behavior;

import baritone.Baritone;
import baritone.api.behavior.IPathingBehavior;
import baritone.api.event.events.PathEvent;
import baritone.api.event.events.PlayerUpdateEvent;
import baritone.api.event.events.RenderEvent;
import baritone.api.event.events.SprintStateEvent;
import baritone.api.event.events.TickEvent;
import baritone.api.pathing.calc.IPath;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.process.PathingCommand;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.Helper;
import baritone.api.utils.PathCalculationResult;
import baritone.api.utils.interfaces.IGoalRenderPos;
import baritone.pathing.calc.AStarPathFinder;
import baritone.pathing.calc.AbstractNodeCostSearch;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.MovementHelper;
import baritone.pathing.path.PathExecutor;
import baritone.utils.PathRenderer;
import baritone.utils.PathingCommandContext;
import baritone.utils.pathing.Favoring;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import net.minecraft.core.BlockPos;

public final class PathingBehavior extends Behavior implements IPathingBehavior, Helper {
   private PathExecutor current;
   private PathExecutor next;
   private Goal goal;
   private CalculationContext context;
   private int ticksElapsedSoFar;
   private BetterBlockPos startPosition;
   private boolean safeToCancel;
   private boolean pauseRequestedLastTick;
   private boolean unpausedLastTick;
   private boolean pausedThisTick;
   private boolean cancelRequested;
   private boolean calcFailedLastTick;
   private volatile AbstractNodeCostSearch inProgress;
   private final Object pathCalcLock = new Object();
   private final Object pathPlanLock = new Object();
   private boolean lastAutoJump;
   private BetterBlockPos expectedSegmentStart;
   private final LinkedBlockingQueue<PathEvent> toDispatch = new LinkedBlockingQueue<>();

   public PathingBehavior(Baritone baritone) {
      super(baritone);
   }

   private void queuePathEvent(PathEvent event) {
      this.toDispatch.add(event);
   }

   private void dispatchEvents() {
      ArrayList<PathEvent> curr = new ArrayList<>();
      this.toDispatch.drainTo(curr);
      this.calcFailedLastTick = curr.contains(PathEvent.CALC_FAILED);

      for (PathEvent event : curr) {
         this.baritone.getGameEventHandler().onPathEvent(event);
      }
   }

   @Override
   public void onTick(TickEvent event) {
      this.dispatchEvents();
      if (event.getType() == TickEvent.Type.OUT) {
         this.secretInternalSegmentCancel();
         this.baritone.getPathingControlManager().cancelEverything();
      } else {
         this.expectedSegmentStart = this.pathStart();
         this.baritone.getPathingControlManager().preTick();
         this.tickPath();
         this.ticksElapsedSoFar++;
         this.dispatchEvents();
      }
   }

   @Override
   public void onPlayerSprintState(SprintStateEvent event) {
      if (this.isPathing()) {
         event.setState(this.current.isSprinting());
      }
   }

   private void tickPath() {
      this.pausedThisTick = false;
      if (this.pauseRequestedLastTick && this.safeToCancel) {
         this.pauseRequestedLastTick = false;
         if (this.unpausedLastTick) {
            this.baritone.getInputOverrideHandler().clearAllKeys();
            this.baritone.getInputOverrideHandler().getBlockBreakHelper().stopBreakingBlock();
         }

         this.unpausedLastTick = false;
         this.pausedThisTick = true;
      } else {
         this.unpausedLastTick = true;
         if (this.cancelRequested) {
            this.cancelRequested = false;
            this.baritone.getInputOverrideHandler().clearAllKeys();
         }

         synchronized (this.pathPlanLock) {
            synchronized (this.pathCalcLock) {
               if (this.inProgress != null) {
                  BetterBlockPos calcFrom = this.inProgress.getStart();
                  Optional<IPath> currentBest = this.inProgress.bestPathSoFar();
                  if ((this.current == null || !this.current.getPath().getDest().equals(calcFrom))
                     && !calcFrom.equals(this.ctx.playerFeet())
                     && !calcFrom.equals(this.expectedSegmentStart)
                     && (
                        !currentBest.isPresent()
                           || !currentBest.get().positions().contains(this.ctx.playerFeet())
                              && !currentBest.get().positions().contains(this.expectedSegmentStart)
                     )) {
                     this.inProgress.cancel();
                  }
               }
            }

            if (this.current != null) {
               this.safeToCancel = this.current.onTick();
               if (this.current.failed() || this.current.finished()) {
                  this.current = null;
                  if (this.goal != null && !this.goal.isInGoal(this.ctx.playerFeet())) {
                     if (this.next != null
                        && !this.next.getPath().positions().contains(this.ctx.playerFeet())
                        && !this.next.getPath().positions().contains(this.expectedSegmentStart)) {
                        this.logDebug("Discarding next path as it does not contain current position");
                        this.queuePathEvent(PathEvent.DISCARD_NEXT);
                        this.next = null;
                     }

                     if (this.next != null) {
                        this.logDebug("Continuing on to planned next path");
                        this.queuePathEvent(PathEvent.CONTINUING_ONTO_PLANNED_NEXT);
                        this.current = this.next;
                        this.next = null;
                        this.current.onTick();
                     } else {
                        synchronized (this.pathCalcLock) {
                           if (this.inProgress != null) {
                              this.queuePathEvent(PathEvent.PATH_FINISHED_NEXT_STILL_CALCULATING);
                              return;
                           }

                           this.queuePathEvent(PathEvent.CALC_STARTED);
                           this.findPathInNewThread(this.expectedSegmentStart, true, this.context);
                        }
                     }
                  } else {
                     this.logDebug("All done. At " + this.goal);
                     this.queuePathEvent(PathEvent.AT_GOAL);
                     this.next = null;
                     if (Baritone.settings().disconnectOnArrival.value) {
                        this.ctx.world().disconnect();
                     }
                  }
               } else if (this.safeToCancel && this.next != null && this.next.snipsnapifpossible()) {
                  this.logDebug("Splicing into planned next path early...");
                  this.queuePathEvent(PathEvent.SPLICING_ONTO_NEXT_EARLY);
                  this.current = this.next;
                  this.next = null;
                  this.current.onTick();
               } else {
                  if (Baritone.settings().splicePath.value) {
                     this.current = this.current.trySplice(this.next);
                  }

                  if (this.next != null && this.current.getPath().getDest().equals(this.next.getPath().getDest())) {
                     this.next = null;
                  }

                  synchronized (this.pathCalcLock) {
                     if (this.inProgress != null) {
                        return;
                     }

                     if (this.next != null) {
                        return;
                     }

                     if (this.goal == null || this.goal.isInGoal(this.current.getPath().getDest())) {
                        return;
                     }

                     if (this.ticksRemainingInSegment(false).get() < (double)Baritone.settings().planningTickLookahead.value.intValue()) {
                        this.logDebug("Path almost over. Planning ahead...");
                        this.queuePathEvent(PathEvent.NEXT_SEGMENT_CALC_STARTED);
                        this.findPathInNewThread(this.current.getPath().getDest(), false, this.context);
                     }
                  }
               }
            }
         }
      }
   }

   @Override
   public void onPlayerUpdate(PlayerUpdateEvent event) {
      if (this.current != null) {
         switch (event.getState()) {
            case PRE:
               this.lastAutoJump = (Boolean)this.ctx.minecraft().options.autoJump().get();
               this.ctx.minecraft().options.autoJump().set(false);
               break;
            case POST:
               this.ctx.minecraft().options.autoJump().set(this.lastAutoJump);
         }
      }
   }

   public void secretInternalSetGoal(Goal goal) {
      this.goal = goal;
   }

   public boolean secretInternalSetGoalAndPath(PathingCommand command) {
      this.secretInternalSetGoal(command.goal);
      if (command instanceof PathingCommandContext) {
         this.context = ((PathingCommandContext)command).desiredCalcContext;
      } else {
         this.context = new CalculationContext(this.baritone, true);
      }

      if (this.goal == null) {
         return false;
      } else if (this.goal.isInGoal(this.ctx.playerFeet())) {
         return false;
      } else {
         synchronized (this.pathPlanLock) {
            if (this.current != null) {
               return false;
            } else {
               boolean var10000;
               synchronized (this.pathCalcLock) {
                  if (this.inProgress != null) {
                     return false;
                  }

                  this.queuePathEvent(PathEvent.CALC_STARTED);
                  this.findPathInNewThread(this.expectedSegmentStart, true, this.context);
                  var10000 = true;
               }

               return var10000;
            }
         }
      }
   }

   @Override
   public Goal getGoal() {
      return this.goal;
   }

   @Override
   public boolean isPathing() {
      return this.hasPath() && !this.pausedThisTick;
   }

   public PathExecutor getCurrent() {
      return this.current;
   }

   public PathExecutor getNext() {
      return this.next;
   }

   @Override
   public Optional<AbstractNodeCostSearch> getInProgress() {
      return Optional.ofNullable(this.inProgress);
   }

   public boolean isSafeToCancel() {
      return this.current != null ? this.safeToCancel : !this.baritone.getElytraProcess().isActive() || this.baritone.getElytraProcess().isSafeToCancel();
   }

   public void requestPause() {
      this.pauseRequestedLastTick = true;
   }

   public boolean cancelSegmentIfSafe() {
      if (this.isSafeToCancel()) {
         this.secretInternalSegmentCancel();
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean cancelEverything() {
      boolean doIt = this.isSafeToCancel();
      if (doIt) {
         this.secretInternalSegmentCancel();
      }

      this.baritone.getPathingControlManager().cancelEverything();
      return doIt;
   }

   public boolean calcFailedLastTick() {
      return this.calcFailedLastTick;
   }

   public void softCancelIfSafe() {
      synchronized (this.pathPlanLock) {
         this.getInProgress().ifPresent(AbstractNodeCostSearch::cancel);
         if (!this.isSafeToCancel()) {
            return;
         }

         this.current = null;
         this.next = null;
      }

      this.cancelRequested = true;
   }

   public void secretInternalSegmentCancel() {
      this.queuePathEvent(PathEvent.CANCELED);
      synchronized (this.pathPlanLock) {
         this.getInProgress().ifPresent(AbstractNodeCostSearch::cancel);
         if (this.current != null) {
            this.current = null;
            this.next = null;
            this.baritone.getInputOverrideHandler().clearAllKeys();
            this.baritone.getInputOverrideHandler().getBlockBreakHelper().stopBreakingBlock();
         }
      }
   }

   @Override
   public void forceCancel() {
      this.cancelEverything();
      this.secretInternalSegmentCancel();
      synchronized (this.pathCalcLock) {
         this.inProgress = null;
      }
   }

   public CalculationContext secretInternalGetCalculationContext() {
      return this.context;
   }

   @Override
   public Optional<Double> estimatedTicksToGoal() {
      BetterBlockPos currentPos = this.ctx.playerFeet();
      if (this.goal != null && currentPos != null && this.startPosition != null) {
         if (this.goal.isInGoal(this.ctx.playerFeet())) {
            this.resetEstimatedTicksToGoal();
            return Optional.of(0.0);
         } else if (this.ticksElapsedSoFar == 0) {
            return Optional.empty();
         } else {
            double current = this.goal.heuristic(currentPos.x, currentPos.y, currentPos.z);
            double start = this.goal.heuristic(this.startPosition.x, this.startPosition.y, this.startPosition.z);
            if (current == start) {
               return Optional.empty();
            } else {
               double eta = Math.abs(current - this.goal.heuristic()) * (double)this.ticksElapsedSoFar / Math.abs(start - current);
               return Optional.of(eta);
            }
         }
      } else {
         return Optional.empty();
      }
   }

   private void resetEstimatedTicksToGoal() {
      this.resetEstimatedTicksToGoal(this.expectedSegmentStart);
   }

   private void resetEstimatedTicksToGoal(BlockPos start) {
      this.resetEstimatedTicksToGoal(new BetterBlockPos(start));
   }

   private void resetEstimatedTicksToGoal(BetterBlockPos start) {
      this.ticksElapsedSoFar = 0;
      this.startPosition = start;
   }

   public BetterBlockPos pathStart() {
      BetterBlockPos feet = this.ctx.playerFeet();
      if (!MovementHelper.canWalkOn(this.ctx, feet.below())) {
         if (this.ctx.player().onGround()) {
            double playerX = this.ctx.player().position().x;
            double playerZ = this.ctx.player().position().z;
            ArrayList<BetterBlockPos> closest = new ArrayList<>();

            for (int dx = -1; dx <= 1; dx++) {
               for (int dz = -1; dz <= 1; dz++) {
                  closest.add(new BetterBlockPos(feet.x + dx, feet.y, feet.z + dz));
               }
            }

            closest.sort(
               Comparator.comparingDouble(
                  pos -> ((double)pos.x + 0.5 - playerX) * ((double)pos.x + 0.5 - playerX) + ((double)pos.z + 0.5 - playerZ) * ((double)pos.z + 0.5 - playerZ)
               )
            );

            for (int i = 0; i < 4; i++) {
               BetterBlockPos possibleSupport = closest.get(i);
               double xDist = Math.abs((double)possibleSupport.x + 0.5 - playerX);
               double zDist = Math.abs((double)possibleSupport.z + 0.5 - playerZ);
               if ((!(xDist > 0.8) || !(zDist > 0.8))
                  && MovementHelper.canWalkOn(this.ctx, possibleSupport.below())
                  && MovementHelper.canWalkThrough(this.ctx, possibleSupport)
                  && MovementHelper.canWalkThrough(this.ctx, possibleSupport.above())) {
                  return possibleSupport;
               }
            }
         } else if (MovementHelper.canWalkOn(this.ctx, feet.below().below())) {
            return feet.below();
         }
      }

      return feet;
   }

   private void findPathInNewThread(BlockPos start, boolean talkAboutIt, CalculationContext context) {
      if (!Thread.holdsLock(this.pathCalcLock)) {
         throw new IllegalStateException("Must be called with synchronization on pathCalcLock");
      } else if (this.inProgress != null) {
         throw new IllegalStateException("Already doing it");
      } else if (!context.safeForThreadedUse) {
         throw new IllegalStateException("Improper context thread safety level");
      } else {
         Goal goal = this.goal;
         if (goal == null) {
            this.logDebug("no goal");
         } else {
            long primaryTimeout;
            long failureTimeout;
            if (this.current == null) {
               primaryTimeout = Baritone.settings().primaryTimeoutMS.value;
               failureTimeout = Baritone.settings().failureTimeoutMS.value;
            } else {
               primaryTimeout = Baritone.settings().planAheadPrimaryTimeoutMS.value;
               failureTimeout = Baritone.settings().planAheadFailureTimeoutMS.value;
            }

            AbstractNodeCostSearch pathfinder = this.createPathfinder(start, goal, this.current == null ? null : this.current.getPath(), context);
            if (!Objects.equals(pathfinder.getGoal(), goal)) {
               this.logDebug("Simplifying " + goal.getClass() + " to GoalXZ due to distance");
            }

            this.inProgress = pathfinder;
            Baritone.getExecutor()
               .execute(
                  () -> {
                     if (talkAboutIt) {
                        this.logDebug("Starting to search for path from " + start + " to " + goal);
                     }

                     PathCalculationResult calcResult = pathfinder.calculate(primaryTimeout, failureTimeout);
                     synchronized (this.pathPlanLock) {
                        Optional<PathExecutor> executor = calcResult.getPath().map(p -> new PathExecutor(this, p));
                        if (this.current == null) {
                           if (executor.isPresent()) {
                              if (executor.get().getPath().positions().contains(this.expectedSegmentStart)) {
                                 this.queuePathEvent(PathEvent.CALC_FINISHED_NOW_EXECUTING);
                                 this.current = executor.get();
                                 this.resetEstimatedTicksToGoal(start);
                              } else {
                                 this.logDebug("Warning: discarding orphan path segment with incorrect start");
                              }
                           } else if (calcResult.getType() != PathCalculationResult.Type.CANCELLATION
                              && calcResult.getType() != PathCalculationResult.Type.EXCEPTION) {
                              this.queuePathEvent(PathEvent.CALC_FAILED);
                           }
                        } else if (this.next == null) {
                           if (executor.isPresent()) {
                              if (executor.get().getPath().getSrc().equals(this.current.getPath().getDest())) {
                                 this.queuePathEvent(PathEvent.NEXT_SEGMENT_CALC_FINISHED);
                                 this.next = executor.get();
                              } else {
                                 this.logDebug("Warning: discarding orphan next segment with incorrect start");
                              }
                           } else {
                              this.queuePathEvent(PathEvent.NEXT_CALC_FAILED);
                           }
                        } else {
                           this.logDirect("Warning: PathingBehavior illegal state! Discarding invalid path!");
                        }

                        if (talkAboutIt && this.current != null && this.current.getPath() != null) {
                           if (goal.isInGoal(this.current.getPath().getDest())) {
                              this.logDebug(
                                 "Finished finding a path from "
                                    + start
                                    + " to "
                                    + goal
                                    + ". "
                                    + this.current.getPath().getNumNodesConsidered()
                                    + " nodes considered"
                              );
                           } else {
                              this.logDebug(
                                 "Found path segment from "
                                    + start
                                    + " towards "
                                    + goal
                                    + ". "
                                    + this.current.getPath().getNumNodesConsidered()
                                    + " nodes considered"
                              );
                           }
                        }

                        synchronized (this.pathCalcLock) {
                           this.inProgress = null;
                        }
                     }
                  }
               );
         }
      }
   }

   private AbstractNodeCostSearch createPathfinder(BlockPos start, Goal goal, IPath previous, CalculationContext context) {
      Goal transformed = goal;
      if (Baritone.settings().simplifyUnloadedYCoord.value && goal instanceof IGoalRenderPos) {
         BlockPos pos = ((IGoalRenderPos)goal).getGoalPos();
         if (!context.bsi.worldContainsLoadedChunk(pos.getX(), pos.getZ())) {
            transformed = new GoalXZ(pos.getX(), pos.getZ());
         }
      }

      Favoring favoring = new Favoring(context.getBaritone().getPlayerContext(), previous, context);
      BetterBlockPos feet = this.ctx.playerFeet();
      BetterBlockPos realStart = new BetterBlockPos(start);
      BlockPos sub = feet.subtract(realStart);
      if (feet.getY() == realStart.getY() && Math.abs(sub.getX()) <= 1 && Math.abs(sub.getZ()) <= 1) {
         realStart = feet;
      }

      return new AStarPathFinder(realStart, start.getX(), start.getY(), start.getZ(), transformed, favoring, context);
   }

   @Override
   public void onRenderPass(RenderEvent event) {
      PathRenderer.render(event, this);
   }
}
