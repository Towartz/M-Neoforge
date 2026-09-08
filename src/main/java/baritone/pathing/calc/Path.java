package baritone.pathing.calc;

import baritone.api.pathing.calc.IPath;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.movement.IMovement;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.Helper;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.Movement;
import baritone.pathing.movement.Moves;
import baritone.pathing.path.CutoffPath;
import baritone.utils.pathing.PathBase;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class Path extends PathBase {
   private final BetterBlockPos start;
   private final BetterBlockPos end;
   private final List<BetterBlockPos> path;
   private final List<Movement> movements;
   private final List<PathNode> nodes;
   private final Goal goal;
   private final int numNodes;
   private final CalculationContext context;
   private volatile boolean verified;

   Path(BetterBlockPos realStart, PathNode start, PathNode end, int numNodes, Goal goal, CalculationContext context) {
      this.end = new BetterBlockPos(end.x, end.y, end.z);
      this.numNodes = numNodes;
      this.movements = new ArrayList<>();
      this.goal = goal;
      this.context = context;
      PathNode current = end;
      List<BetterBlockPos> tempPath = new ArrayList<>();

      List<PathNode> tempNodes;
      for (tempNodes = new ArrayList<>(); current != null; current = current.previous) {
         tempNodes.add(current);
         tempPath.add(new BetterBlockPos(current.x, current.y, current.z));
      }

      BetterBlockPos startNodePos = new BetterBlockPos(start.x, start.y, start.z);
      if (!realStart.equals(startNodePos) && start.equals(end)) {
         this.start = realStart;
         PathNode fakeNode = new PathNode(realStart.x, realStart.y, realStart.z, goal);
         fakeNode.cost = 0.0;
         tempNodes.add(fakeNode);
         tempPath.add(realStart);
      } else {
         this.start = startNodePos;
      }

      this.path = Lists.reverse(tempPath);
      this.nodes = Lists.reverse(tempNodes);
   }

   @Override
   public Goal getGoal() {
      return this.goal;
   }

   private boolean assembleMovements() {
      if (!this.path.isEmpty() && this.movements.isEmpty()) {
         for (int i = 0; i < this.path.size() - 1; i++) {
            double cost = this.nodes.get(i + 1).cost - this.nodes.get(i).cost;
            Movement move = this.runBackwards(this.path.get(i), this.path.get(i + 1), cost);
            if (move == null) {
               return true;
            }

            this.movements.add(move);
         }

         return false;
      } else {
         throw new IllegalStateException("Path must not be empty");
      }
   }

   private Movement runBackwards(BetterBlockPos src, BetterBlockPos dest, double cost) {
      for (Moves moves : Moves.values()) {
         Movement move = moves.apply0(this.context, src);
         if (move.getDest().equals(dest)) {
            move.override(Math.min(move.calculateCost(this.context), cost));
            return move;
         }
      }

      Helper.HELPER.logDebug("Movement became impossible during calculation " + src + " " + dest + " " + dest.subtract(src));
      return null;
   }

   @Override
   public IPath postProcess() {
      if (this.verified) {
         throw new IllegalStateException("Path must not be verified twice");
      } else {
         this.verified = true;
         boolean failed = this.assembleMovements();
         this.movements.forEach(m -> m.checkLoadedChunk(this.context));
         if (failed) {
            CutoffPath res = new CutoffPath(this, this.movements().size());
            if (res.movements().size() != this.movements.size()) {
               throw new IllegalStateException("Path has wrong size after cutoff");
            } else {
               return res;
            }
         } else {
            this.sanityCheck();
            return this;
         }
      }
   }

   @Override
   public List<IMovement> movements() {
      if (!this.verified) {
         throw new IllegalStateException("Path not yet verified");
      } else {
         return Collections.unmodifiableList(this.movements);
      }
   }

   @Override
   public List<BetterBlockPos> positions() {
      return Collections.unmodifiableList(this.path);
   }

   @Override
   public int getNumNodesConsidered() {
      return this.numNodes;
   }

   @Override
   public BetterBlockPos getSrc() {
      return this.start;
   }

   @Override
   public BetterBlockPos getDest() {
      return this.end;
   }
}
