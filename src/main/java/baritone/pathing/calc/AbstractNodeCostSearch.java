package baritone.pathing.calc;

import baritone.Baritone;
import baritone.api.pathing.calc.IPath;
import baritone.api.pathing.calc.IPathFinder;
import baritone.api.pathing.goals.Goal;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.Helper;
import baritone.api.utils.PathCalculationResult;
import baritone.pathing.movement.CalculationContext;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Optional;

public abstract class AbstractNodeCostSearch implements IPathFinder, Helper {
   protected final BetterBlockPos realStart;
   protected final int startX;
   protected final int startY;
   protected final int startZ;
   protected final Goal goal;
   private final CalculationContext context;
   private final Long2ObjectOpenHashMap<PathNode> map;
   protected PathNode startNode;
   protected PathNode mostRecentConsidered;
   protected final PathNode[] bestSoFar;
   private volatile boolean isFinished;
   protected boolean cancelRequested;
   protected static final double[] COEFFICIENTS = new double[]{1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 10.0};
   protected static final double MIN_DIST_PATH = 5.0;
   protected static final double MIN_IMPROVEMENT = 0.01;

   AbstractNodeCostSearch(BetterBlockPos realStart, int startX, int startY, int startZ, Goal goal, CalculationContext context) {
      this.bestSoFar = new PathNode[COEFFICIENTS.length];
      this.realStart = realStart;
      this.startX = startX;
      this.startY = startY;
      this.startZ = startZ;
      this.goal = goal;
      this.context = context;
      int mapSize = Math.max(Baritone.settings().pathingMapDefaultSize.value, 8192);
      this.map = new Long2ObjectOpenHashMap(mapSize, Baritone.settings().pathingMapLoadFactor.value);
   }

   public void cancel() {
      this.cancelRequested = true;
   }

   @Override
   public synchronized PathCalculationResult calculate(long primaryTimeout, long failureTimeout) {
      if (this.isFinished) {
         throw new IllegalStateException("Path finder cannot be reused!");
      } else {
         this.cancelRequested = false;
         ScannedNodeTracker.getInstance().onCalculationStarted();

         PathCalculationResult var7;
         try {
            IPath path = this.calculate0(primaryTimeout, failureTimeout).map(IPath::postProcess).orElse(null);
            if (this.cancelRequested) {
               return new PathCalculationResult(PathCalculationResult.Type.CANCELLATION);
            }

            if (path == null) {
               return new PathCalculationResult(PathCalculationResult.Type.FAILURE);
            }

            int previousLength = path.length();
            path = path.cutoffAtLoadedChunks(this.context.bsi);
            if (path.length() < previousLength) {
               Helper.HELPER.logDebug("Cutting off path at edge of loaded chunks");
               Helper.HELPER.logDebug("Length decreased by " + (previousLength - path.length()));
            } else {
               Helper.HELPER.logDebug("Path ends within loaded chunks");
            }

            previousLength = path.length();
            path = path.staticCutoff(this.goal);
            if (path.length() < previousLength) {
               Helper.HELPER.logDebug("Static cutoff " + previousLength + " to " + path.length());
            }

            if (!this.goal.isInGoal(path.getDest())) {
               return new PathCalculationResult(PathCalculationResult.Type.SUCCESS_SEGMENT, path);
            }

            var7 = new PathCalculationResult(PathCalculationResult.Type.SUCCESS_TO_GOAL, path);
         } catch (Exception var11) {
            Helper.HELPER.logDirect("Pathing exception: " + var11);
            var11.printStackTrace();
            return new PathCalculationResult(PathCalculationResult.Type.EXCEPTION);
         } finally {
            this.isFinished = true;
            ScannedNodeTracker.getInstance().onCalculationFinished();
         }

         return var7;
      }
   }

   protected abstract Optional<IPath> calculate0(long var1, long var3);

   protected double getDistFromStartSq(PathNode n) {
      int xDiff = n.x - this.startX;
      int yDiff = n.y - this.startY;
      int zDiff = n.z - this.startZ;
      return (double)(xDiff * xDiff + yDiff * yDiff + zDiff * zDiff);
   }

   protected PathNode getNodeAtPosition(int x, int y, int z, long hashCode) {
      PathNode node = (PathNode)this.map.get(hashCode);
      if (node == null) {
         node = new PathNode(x, y, z, this.goal);
         this.map.put(hashCode, node);
      }

      return node;
   }

   @Override
   public Optional<IPath> pathToMostRecentNodeConsidered() {
      return Optional.ofNullable(this.mostRecentConsidered).map(node -> new Path(this.realStart, this.startNode, node, 0, this.goal, this.context));
   }

   @Override
   public Optional<IPath> bestPathSoFar() {
      return this.bestSoFar(false, 0);
   }

   protected Optional<IPath> bestSoFar(boolean logInfo, int numNodes) {
      if (this.startNode == null) {
         return Optional.empty();
      } else {
         double bestDist = 0.0;

         for (int i = 0; i < COEFFICIENTS.length; i++) {
            if (this.bestSoFar[i] != null) {
               double dist = this.getDistFromStartSq(this.bestSoFar[i]);
               if (dist > bestDist) {
                  bestDist = dist;
               }

               if (dist > 25.0) {
                  if (logInfo) {
                     if (COEFFICIENTS[i] >= 3.0) {
                        System.out.println("Warning: cost coefficient is greater than three! Probably means that");
                        System.out.println("the path I found is pretty terrible (like sneak-bridging for dozens of blocks)");
                        System.out.println("But I'm going to do it anyway, because yolo");
                     }

                     System.out.println("Path goes for " + Math.sqrt(dist) + " blocks");
                     this.logDebug("A* cost coefficient " + COEFFICIENTS[i]);
                  }

                  return Optional.of(new Path(this.realStart, this.startNode, this.bestSoFar[i], numNodes, this.goal, this.context));
               }
            }
         }

         if (logInfo) {
            this.logDebug(
               "Even with a cost coefficient of " + COEFFICIENTS[COEFFICIENTS.length - 1] + ", I couldn't get more than " + Math.sqrt(bestDist) + " blocks"
            );
            this.logDebug("No path found =(");
            this.logNotification("No path found =(", true);
         }

         return Optional.empty();
      }
   }

   @Override
   public final boolean isFinished() {
      return this.isFinished;
   }

   @Override
   public final Goal getGoal() {
      return this.goal;
   }

   public BetterBlockPos getStart() {
      return new BetterBlockPos(this.startX, this.startY, this.startZ);
   }

   protected int mapSize() {
      return this.map.size();
   }
}
