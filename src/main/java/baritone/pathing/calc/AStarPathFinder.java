package baritone.pathing.calc;

import baritone.Baritone;
import baritone.api.pathing.calc.IPath;
import baritone.api.pathing.goals.Goal;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.SettingsUtil;
import baritone.pathing.calc.openset.BinaryHeapOpenSet;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.Moves;
import baritone.utils.pathing.BetterWorldBorder;
import baritone.utils.pathing.Favoring;
import baritone.utils.pathing.MutableMoveResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AStarPathFinder extends AbstractNodeCostSearch {
   private final Favoring favoring;
   private final CalculationContext calcContext;

   private static Moves[] getViableMoves(CalculationContext ctx) {
      List<Moves> list = new ArrayList<>(22);
      for (Moves m : Moves.values()) {
         if (!ctx.allowParkour && (m == Moves.PARKOUR_NORTH || m == Moves.PARKOUR_SOUTH || m == Moves.PARKOUR_EAST || m == Moves.PARKOUR_WEST)) {
            continue;
         }
         if (!ctx.allowDownward && m == Moves.DOWNWARD) {
            continue;
         }
         if (!ctx.hasThrowaway && m == Moves.PILLAR) {
            continue;
         }
         list.add(m);
      }
      return list.toArray(new Moves[0]);
   }

   public AStarPathFinder(BetterBlockPos realStart, int startX, int startY, int startZ, Goal goal, Favoring favoring, CalculationContext context) {
      super(realStart, startX, startY, startZ, goal, context);
      this.favoring = favoring;
      this.calcContext = context;
   }

   @Override
   protected Optional<IPath> calculate0(long primaryTimeout, long failureTimeout) {
      int minY = this.calcContext.world.dimensionType().minY();
      int height = this.calcContext.world.dimensionType().height();
      this.startNode = this.getNodeAtPosition(this.startX, this.startY, this.startZ, BetterBlockPos.longHash(this.startX, this.startY, this.startZ));
      this.startNode.cost = 0.0;
      this.startNode.combinedCost = this.startNode.estimatedCostToGoal;
      BinaryHeapOpenSet openSet = new BinaryHeapOpenSet();
      openSet.insert(this.startNode);
      double[] bestHeuristicSoFar = new double[COEFFICIENTS.length];

      for (int i = 0; i < bestHeuristicSoFar.length; i++) {
         bestHeuristicSoFar[i] = this.startNode.estimatedCostToGoal;
         this.bestSoFar[i] = this.startNode;
      }

      MutableMoveResult res = new MutableMoveResult();
      BetterWorldBorder worldBorder = new BetterWorldBorder(this.calcContext.world.getWorldBorder());
      long startTime = System.currentTimeMillis();
      boolean slowPath = Baritone.settings().slowPath.value;
      if (slowPath) {
         this.logDebug("slowPath is on, path timeout will be " + Baritone.settings().slowPathTimeoutMS.value + "ms instead of " + primaryTimeout + "ms");
      }

      long primaryTimeoutTime = startTime + (slowPath ? Baritone.settings().slowPathTimeoutMS.value : primaryTimeout);
      long failureTimeoutTime = startTime + (slowPath ? Baritone.settings().slowPathTimeoutMS.value : failureTimeout);
      boolean failing = true;
      int numNodes = 0;
      int numMovementsConsidered = 0;
      int numEmptyChunk = 0;
      boolean isFavoring = !this.favoring.isEmpty();
      int timeCheckInterval = 128;
      int pathingMaxChunkBorderFetch = Baritone.settings().pathingMaxChunkBorderFetch.value;
      double minimumImprovement = Baritone.settings().minimumImprovementRepropagation.value ? 0.01 : 0.0;
      Moves[] allMoves = getViableMoves(this.calcContext);

      while (!openSet.isEmpty() && numEmptyChunk < pathingMaxChunkBorderFetch && !this.cancelRequested) {
         if ((numNodes & timeCheckInterval - 1) == 0) {
            long now = System.currentTimeMillis();
            if (now - failureTimeoutTime >= 0L || !failing && now - primaryTimeoutTime >= 0L) {
               break;
            }
         }

         if (slowPath) {
            try {
               Thread.sleep(Baritone.settings().slowPathTimeDelayMS.value);
            } catch (InterruptedException var45) {
            }
         }

         PathNode currentNode = openSet.removeLowest();
         this.mostRecentConsidered = currentNode;
         numNodes++;

         if (Baritone.settings().renderScannedNodes.value && (numNodes & 1) == 0) {
            ScannedNodeTracker.getInstance().addNode(currentNode.x, currentNode.y, currentNode.z);
         }

         if (this.goal.isInGoal(currentNode.x, currentNode.y, currentNode.z)) {
            this.logDebug("Took " + (System.currentTimeMillis() - startTime) + "ms, " + numMovementsConsidered + " movements considered");
            return Optional.of(new Path(this.realStart, this.startNode, currentNode, numNodes, this.goal, this.calcContext));
         }

         int parentX = currentNode.previous != null ? currentNode.previous.x : Integer.MIN_VALUE;
         int parentY = currentNode.previous != null ? currentNode.previous.y : Integer.MIN_VALUE;
         int parentZ = currentNode.previous != null ? currentNode.previous.z : Integer.MIN_VALUE;

         for (Moves moves : allMoves) {
            if (parentX != Integer.MIN_VALUE && !moves.dynamicXZ && !moves.dynamicY) {
               if (currentNode.x + moves.xOffset == parentX &&
                   currentNode.y + moves.yOffset == parentY &&
                   currentNode.z + moves.zOffset == parentZ) {
                  continue; // Prune reverse move straight back to parent
               }
            }

            int newX = currentNode.x + moves.xOffset;
            int newZ = currentNode.z + moves.zOffset;
            if ((newX >> 4 != currentNode.x >> 4 || newZ >> 4 != currentNode.z >> 4) && !this.calcContext.isLoaded(newX, newZ)) {
               if (!moves.dynamicXZ) {
                  numEmptyChunk++;
               }
            } else if ((moves.dynamicXZ || worldBorder.entirelyContains(newX, newZ))
               && currentNode.y + moves.yOffset <= height
               && currentNode.y + moves.yOffset >= minY) {
               res.reset();
               moves.apply(this.calcContext, currentNode.x, currentNode.y, currentNode.z, res);
               numMovementsConsidered++;
               double actionCost = res.cost;
               if (!(actionCost >= 1000000.0)) {
                  if (actionCost <= 0.0 || Double.isNaN(actionCost)) {
                     throw new IllegalStateException(
                        String.format(
                           "%s from %s %s %s calculated implausible cost %s",
                           moves,
                           SettingsUtil.maybeCensor(currentNode.x),
                           SettingsUtil.maybeCensor(currentNode.y),
                           SettingsUtil.maybeCensor(currentNode.z),
                           actionCost
                        )
                     );
                  }

                  if (!moves.dynamicXZ || worldBorder.entirelyContains(res.x, res.z)) {
                     if (!moves.dynamicXZ && (res.x != newX || res.z != newZ)) {
                        throw new IllegalStateException(
                           String.format(
                              "%s from %s %s %s ended at x z %s %s instead of %s %s",
                              moves,
                              SettingsUtil.maybeCensor(currentNode.x),
                              SettingsUtil.maybeCensor(currentNode.y),
                              SettingsUtil.maybeCensor(currentNode.z),
                              SettingsUtil.maybeCensor(res.x),
                              SettingsUtil.maybeCensor(res.z),
                              SettingsUtil.maybeCensor(newX),
                              SettingsUtil.maybeCensor(newZ)
                           )
                        );
                     }

                     if (!moves.dynamicY && res.y != currentNode.y + moves.yOffset) {
                        throw new IllegalStateException(
                           String.format(
                              "%s from %s %s %s ended at y %s instead of %s",
                              moves,
                              SettingsUtil.maybeCensor(currentNode.x),
                              SettingsUtil.maybeCensor(currentNode.y),
                              SettingsUtil.maybeCensor(currentNode.z),
                              SettingsUtil.maybeCensor(res.y),
                              SettingsUtil.maybeCensor(currentNode.y + moves.yOffset)
                           )
                        );
                     }

                     long hashCode = BetterBlockPos.longHash(res.x, res.y, res.z);
                     if (isFavoring) {
                        actionCost *= this.favoring.calculate(hashCode);
                     }

                     PathNode neighbor = this.getNodeAtPosition(res.x, res.y, res.z, hashCode);
                     double tentativeCost = currentNode.cost + actionCost;
                     if (neighbor.cost - tentativeCost > minimumImprovement) {
                        neighbor.previous = currentNode;
                        neighbor.cost = tentativeCost;
                        neighbor.combinedCost = tentativeCost + neighbor.estimatedCostToGoal;
                        if (neighbor.isOpen()) {
                           openSet.update(neighbor);
                        } else {
                           openSet.insert(neighbor);
                        }

                        for (int i = 0; i < COEFFICIENTS.length; i++) {
                           double heuristic = neighbor.estimatedCostToGoal + neighbor.cost / COEFFICIENTS[i];
                           if (bestHeuristicSoFar[i] - heuristic > minimumImprovement) {
                              bestHeuristicSoFar[i] = heuristic;
                              this.bestSoFar[i] = neighbor;
                              if (failing && this.getDistFromStartSq(neighbor) > 25.0) {
                                 failing = false;
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      if (this.cancelRequested) {
         return Optional.empty();
      } else {
         this.logDebug(numMovementsConsidered + " movements considered");
         this.logDebug("Open set size: " + openSet.size());
         this.logDebug("PathNode map size: " + this.mapSize());
         this.logDebug((int)((double)numNodes * 1.0 / (double)((float)(System.currentTimeMillis() - startTime) / 1000.0F)) + " nodes per second");
         Optional<IPath> result = this.bestSoFar(true, numNodes);
         if (result.isPresent()) {
            this.logDebug("Took " + (System.currentTimeMillis() - startTime) + "ms, " + numMovementsConsidered + " movements considered");
         }

         return result;
      }
   }
}
