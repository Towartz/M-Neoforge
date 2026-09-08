package baritone.process.elytra;

import baritone.Baritone;
import baritone.api.Settings;
import baritone.api.behavior.look.IAimProcessor;
import baritone.api.behavior.look.ITickableAimProcessor;
import baritone.api.event.events.BlockChangeEvent;
import baritone.api.event.events.ChunkEvent;
import baritone.api.event.events.PacketEvent;
import baritone.api.event.events.RenderEvent;
import baritone.api.event.events.TickEvent;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.Helper;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.Pair;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.SettingsUtil;
import baritone.api.utils.input.Input;
import baritone.pathing.movement.MovementHelper;
import baritone.process.ElytraProcess;
import baritone.utils.BaritoneMath;
import baritone.utils.BlockStateInterface;
import baritone.utils.IRenderer;
import baritone.utils.PathRenderer;
import baritone.utils.accessor.IFireworkRocketEntity;
import com.mojang.blaze3d.vertex.BufferBuilder;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatIterator;
import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public final class ElytraBehavior implements Helper {
   private final Baritone baritone;
   private final IPlayerContext ctx;
   private final List<Pair<Vec3, Vec3>> clearLines;
   private final List<Pair<Vec3, Vec3>> blockedLines;
   private List<Vec3> simulationLine;
   private BlockPos aimPos;
   private List<BetterBlockPos> visiblePath;
   public NetherPathfinderContext npfContext;
   public IElytraPathFinder pathFinder;
   public final ElytraBehavior.PathManager pathManager;
   private final ElytraProcess process;
   private int remainingFireworkTicks;
   private int remainingSetBackTicks;
   public boolean landingMode;
   private int minimumBoostTicks;
   private boolean deployedFireworkLastTick;
   private final int[] nextTickBoostCounter;
   private BlockStateInterface bsi;
   public final BetterBlockPos destination;
   private final boolean appendDestination;
   private final ExecutorService solverExecutor;
   private Future<ElytraBehavior.Solution> solver;
   private ElytraBehavior.Solution pendingSolution;
   private boolean solveNextTick;
   private long timeLastCacheCull = 0L;
   private int invTickCountdown = 0;
   private final Queue<Runnable> invTransactionQueue = new LinkedList<>();

   public ElytraBehavior(Baritone baritone, ElytraProcess process, NetherPathfinderContext npf, BlockPos destination, boolean appendDestination) {
      this.baritone = baritone;
      this.ctx = baritone.getPlayerContext();
      this.clearLines = new CopyOnWriteArrayList<>();
      this.blockedLines = new CopyOnWriteArrayList<>();
      this.pathManager = new ElytraBehavior.PathManager();
      this.process = process;
      this.destination = new BetterBlockPos(destination);
      this.appendDestination = appendDestination;
      this.solverExecutor = Executors.newSingleThreadExecutor();
      this.nextTickBoostCounter = new int[2];
      this.npfContext = npf;
      if (this.ctx.world().dimension() == Level.NETHER) {
         this.pathFinder = (IElytraPathFinder)(Baritone.settings().elytraAllowAboveRoof.value && Baritone.settings().elytraAllowAboveBuildLimit.value
            ? new BuildLimitPathFinder(this.ctx, this.npfContext)
            : this.npfContext);
      } else {
         this.pathFinder = (IElytraPathFinder)(Baritone.settings().elytraAllowAboveBuildLimit.value
            ? new BuildLimitPathFinder(this.ctx, this.npfContext)
            : this.npfContext);
      }
   }

   public void onRenderPass(RenderEvent event) {
      Settings settings = Baritone.settings();
      if (this.visiblePath != null) {
         PathRenderer.drawPath(event.getModelViewStack(), this.visiblePath, 0, Color.RED, false, 0, 0, 0.0);
      }

      if (this.aimPos != null) {
         PathRenderer.drawGoal(event.getModelViewStack(), this.ctx, new GoalBlock(this.aimPos), event.getPartialTicks(), Color.GREEN);
      }

      if (!this.clearLines.isEmpty() && settings.elytraRenderRaytraces.value) {
         BufferBuilder bufferBuilder = IRenderer.startLines(Color.GREEN, settings.pathRenderLineWidthPixels.value, settings.renderPathIgnoreDepth.value);

         for (Pair<Vec3, Vec3> line : this.clearLines) {
            IRenderer.emitLine(bufferBuilder, event.getModelViewStack(), line.first(), line.second());
         }

         IRenderer.endLines(bufferBuilder, settings.renderPathIgnoreDepth.value);
      }

      if (!this.blockedLines.isEmpty() && Baritone.settings().elytraRenderRaytraces.value) {
         BufferBuilder bufferBuilder = IRenderer.startLines(Color.BLUE, settings.pathRenderLineWidthPixels.value, settings.renderPathIgnoreDepth.value);

         for (Pair<Vec3, Vec3> line : this.blockedLines) {
            IRenderer.emitLine(bufferBuilder, event.getModelViewStack(), line.first(), line.second());
         }

         IRenderer.endLines(bufferBuilder, settings.renderPathIgnoreDepth.value);
      }

      if (this.simulationLine != null && Baritone.settings().elytraRenderSimulation.value) {
         BufferBuilder bufferBuilder = IRenderer.startLines(new Color(3591388), settings.pathRenderLineWidthPixels.value, settings.renderPathIgnoreDepth.value);
         Vec3 offset = this.ctx.player().getPosition(event.getPartialTicks());

         for (int i = 0; i < this.simulationLine.size() - 1; i++) {
            Vec3 src = this.simulationLine.get(i).add(offset);
            Vec3 dst = this.simulationLine.get(i + 1).add(offset);
            IRenderer.emitLine(bufferBuilder, event.getModelViewStack(), src, dst);
         }

         IRenderer.endLines(bufferBuilder, settings.renderPathIgnoreDepth.value);
      }
   }

   public void onChunkEvent(ChunkEvent event) {
      if (event.isPostPopulate() && this.npfContext != null) {
         LevelChunk chunk = this.ctx.world().getChunk(event.getX(), event.getZ());
         this.npfContext.queueForPacking(chunk);
      }
   }

   public void onBlockChange(BlockChangeEvent event) {
      this.npfContext.queueBlockUpdate(event);
   }

   public void onReceivePacket(PacketEvent event) {
      if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
         this.ctx.minecraft().execute(() -> this.remainingSetBackTicks = Baritone.settings().elytraFireworkSetbackUseDelay.value);
      }
   }

   public void pathTo() {
      if (!Baritone.settings().elytraAutoJump.value || this.ctx.player().isFallFlying()) {
         this.pathManager.pathToDestination();
      }
   }

   public void destroy() {
      if (this.solver != null) {
         this.solver.cancel(true);
      }

      this.solverExecutor.shutdown();

      try {
         while (!this.solverExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)) {
         }
      } catch (InterruptedException var2) {
         var2.printStackTrace();
      }
   }

   public void onTick() {
      if (this.npfContext.tryAcquireReadLock()) {
         try {
            this.onTick0();
         } finally {
            this.npfContext.releaseReadLock();
         }
      }

      long now = System.currentTimeMillis();
      if ((now - this.timeLastCacheCull) / 1000L > Baritone.settings().elytraTimeBetweenCacheCullSecs.value) {
         this.npfContext
            .queueCacheCulling(this.ctx.player().chunkPosition().x, this.ctx.player().chunkPosition().z, Baritone.settings().elytraCacheCullDistance.value);
         this.timeLastCacheCull = now;
      }
   }

   private void onTick0() {
      this.pendingSolution = null;
      label64:
      if (this.solver == null) {
         this.tickInventoryTransactions();
         if (this.remainingFireworkTicks > 0) {
            this.remainingFireworkTicks--;
         }

         if (this.remainingSetBackTicks > 0) {
            this.remainingSetBackTicks--;
         }

         if (!this.getAttachedFirework().isPresent()) {
            this.minimumBoostTicks = 0;
         }

         this.clearLines.clear();
         this.blockedLines.clear();
         this.visiblePath = null;
         this.simulationLine = null;
         this.aimPos = null;
         List<BetterBlockPos> path = this.pathManager.getPath();
         if (!path.isEmpty()) {
            if (this.destination == null) {
               this.pathManager.clear();
            } else {
               this.bsi = new BlockStateInterface(this.ctx);
               this.pathManager.tick();
               int playerNear = this.pathManager.getNear();
               this.visiblePath = path.subList(Math.max(playerNear - 30, 0), Math.min(playerNear + 100, path.size()));
            }
         }
      } else {
         if (this.solver.isDone()) {
            try {
               this.pendingSolution = this.solver.get();
            } catch (Exception var5) {
            } finally {
               this.solver = null;
            }
         } else {
            this.solver.cancel(true);
            this.solver = null;
         }
         break label64;
      }
   }

   public void tick() {
      if (!this.pathManager.getPath().isEmpty()) {
         this.trySwapElytra();
         if (this.ctx.player().horizontalCollision) {
            this.logVerbose("hbonk");
         }

         if (this.ctx.player().verticalCollision) {
            this.logVerbose("vbonk");
         }

         ElytraBehavior.SolverContext solverContext = new ElytraBehavior.SolverContext(false);
         this.solveNextTick = true;
         ElytraBehavior.Solution solution;
         if (this.pendingSolution != null && this.pendingSolution.context.equals(solverContext)) {
            solution = this.pendingSolution;
         } else {
            solution = this.solveAngles(solverContext);
         }

         if (this.deployedFireworkLastTick) {
            this.nextTickBoostCounter[solverContext.boost.isBoosted() ? 1 : 0]++;
            this.deployedFireworkLastTick = false;
         }

         boolean inLava = this.ctx.player().isInLava();
         if (inLava) {
            this.baritone.getInputOverrideHandler().setInputForceState(Input.JUMP, true);
         }

         if (solution == null) {
            this.logVerbose("no solution");
         } else {
            this.baritone.getLookBehavior().updateTarget(solution.rotation, false);
            if (!solution.solvedPitch) {
               this.logVerbose("no pitch solution, probably gonna crash in a few ticks LOL!!!");
            } else {
               this.aimPos = new BetterBlockPos(solution.goingTo.x, solution.goingTo.y, solution.goingTo.z);
               this.tickUseFireworks(solution.context.start, solution.goingTo, solution.context.boost.isBoosted(), solution.forceUseFirework || inLava);
            }
         }
      }
   }

   public void onPostTick(TickEvent event) {
      if (event.getType() == TickEvent.Type.IN && this.solveNextTick) {
         this.pathManager.updatePlayerNear();
         ElytraBehavior.SolverContext context = new ElytraBehavior.SolverContext(true);
         this.solver = this.solverExecutor.submit(() -> {
            this.npfContext.acquireReadLock();

            ElytraBehavior.Solution var2x;
            try {
               var2x = this.solveAngles(context);
            } finally {
               this.npfContext.releaseReadLock();
            }

            return var2x;
         });
         this.solveNextTick = false;
      }
   }

   private ElytraBehavior.Solution solveAngles(ElytraBehavior.SolverContext context) {
      NetherPath path = context.path;
      int playerNear = this.landingMode ? path.size() - 1 : context.playerNear;
      Vec3 start = context.start;
      ElytraBehavior.Solution solution = null;

      for (int relaxation = 0; relaxation < 3; relaxation++) {
         int[] heights = context.boost.isBoosted() ? new int[]{20, 10, 5, 0} : new int[]{0};
         int lookahead = relaxation == 0 ? 2 : 3;
         int minStep = playerNear;

         for (int i = Math.min(playerNear + 20, path.size() - 1); i >= minStep; i--) {
            if (Thread.interrupted()) {
               return null;
            }

            List<Pair<Vec3, Integer>> candidates = new ArrayList<>();

            for (int dy : heights) {
               if (relaxation != 0 && i != minStep) {
                  if (relaxation == 1) {
                     double[] interps = new double[]{1.0, 0.75, 0.5, 0.25};

                     for (double interp : interps) {
                        Vec3 dest = interp == 1.0 ? path.getVec(i) : path.getVec(i).scale(interp).add(path.getVec(i - 1).scale(1.0 - interp));
                        candidates.add(new Pair<>(dest, dy));
                     }
                  } else {
                     Vec3 delta = path.getVec(i).subtract(path.getVec(i - 1));
                     int steps = BaritoneMath.fastFloor(delta.length());
                     Vec3 step = delta.normalize();
                     Vec3 stepped = path.getVec(i);

                     for (int interp = 0; interp < steps; interp++) {
                        candidates.add(new Pair<>(stepped, dy));
                        stepped = stepped.subtract(step);
                     }
                  }
               } else {
                  candidates.add(new Pair<>(path.getVec(i), dy));
               }
            }

            for (Pair<Vec3, Integer> candidate : candidates) {
               Integer augment = candidate.second();
               Vec3 dest = candidate.first().add(0.0, (double)augment.intValue(), 0.0);
               if (this.landingMode) {
                  dest = dest.add(0.5, 0.5, 0.5);
               }

               if (augment == 0
                  || i + lookahead < path.size()
                     && (
                        start.distanceTo(dest) < 40.0
                           ? this.clearView(dest, path.getVec(i + lookahead).add(0.0, (double)augment.intValue(), 0.0), false)
                              && this.clearView(dest, path.getVec(i + lookahead), false)
                           : this.clearView(dest, path.getVec(i), false)
                     )) {
                  double minAvoidance = Baritone.settings().elytraMinimumAvoidance.value;
                  Double growth = relaxation == 2 ? null : relaxation == 0 ? 2.0 * minAvoidance : minAvoidance;
                  if (this.isHitboxClear(context, dest, growth)) {
                     float yaw = RotationUtils.calcRotationFromVec3d(start, dest, this.ctx.playerRotations()).getYaw();
                     Pair<Float, Boolean> pitch = this.solvePitch(context, dest, relaxation);
                     if (pitch != null) {
                        return new ElytraBehavior.Solution(context, new Rotation(yaw, pitch.first()), dest, true, pitch.second());
                     }

                     solution = new ElytraBehavior.Solution(context, new Rotation(yaw, this.ctx.playerRotations().getPitch()), null, false, false);
                  }
               }
            }
         }
      }

      return solution;
   }

   private void tickUseFireworks(Vec3 start, Vec3 goingTo, boolean isBoosted, boolean forceUseFirework) {
      if (this.remainingSetBackTicks > 0) {
         this.logDebug("waiting for elytraFireworkSetbackUseDelay: " + this.remainingSetBackTicks);
      } else if (!this.landingMode) {
         boolean useOnDescend = !Baritone.settings().elytraConserveFireworks.value || this.ctx.player().position().y < goingTo.y + 5.0;
         double currentSpeed = new Vec3(
               this.ctx.player().getDeltaMovement().x,
               this.ctx.player().position().y < goingTo.y ? Math.max(0.0, this.ctx.player().getDeltaMovement().y) : this.ctx.player().getDeltaMovement().y,
               this.ctx.player().getDeltaMovement().z
            )
            .lengthSqr();
         double elytraFireworkSpeed = Baritone.settings().elytraFireworkSpeed.value;
         if (this.remainingFireworkTicks <= 0
            && (
               forceUseFirework
                  || !isBoosted
                     && useOnDescend
                     && (
                        this.ctx.player().position().y < goingTo.y - 5.0
                           || start.distanceTo(new Vec3(goingTo.x + 0.5, this.ctx.player().position().y, goingTo.z + 0.5)) > 5.0
                     )
                     && currentSpeed < elytraFireworkSpeed * elytraFireworkSpeed
            )) {
            if (!this.baritone.getInventoryBehavior().throwaway(true, ElytraBehavior::isBoostingFireworks)
               && !this.baritone.getInventoryBehavior().throwaway(true, ElytraBehavior::isFireworks)) {
               this.logDirect("no fireworks");
               return;
            }

            this.logVerbose("attempting to use firework" + (forceUseFirework ? " (forced)" : ""));
            this.ctx.playerController().processRightClick(this.ctx.player(), this.ctx.world(), InteractionHand.MAIN_HAND);
            this.minimumBoostTicks = 10 * (1 + getFireworkBoost(this.ctx.player().getItemInHand(InteractionHand.MAIN_HAND)).orElse(0));
            this.remainingFireworkTicks = 10;
            this.deployedFireworkLastTick = true;
         }
      }
   }

   public static boolean isFireworks(ItemStack itemStack) {
      if (itemStack.getItem() != Items.FIREWORK_ROCKET) {
         return false;
      } else {
         Fireworks fw = (Fireworks)itemStack.get(DataComponents.FIREWORKS);
         return fw != null && fw.explosions().isEmpty();
      }
   }

   private static boolean isBoostingFireworks(ItemStack itemStack) {
      return getFireworkBoost(itemStack).isPresent();
   }

   private static OptionalInt getFireworkBoost(ItemStack itemStack) {
      Fireworks fw = (Fireworks)itemStack.get(DataComponents.FIREWORKS);
      return fw != null && fw.explosions().isEmpty() ? OptionalInt.of(fw.flightDuration()) : OptionalInt.empty();
   }

   private Optional<FireworkRocketEntity> getAttachedFirework() {
      return this.ctx
         .entitiesStream()
         .filter(x -> x instanceof FireworkRocketEntity)
         .filter(x -> Objects.equals(((IFireworkRocketEntity)x).getBoostedEntity(), this.ctx.player()))
         .map(x -> (FireworkRocketEntity)x)
         .findFirst();
   }

   private boolean isHitboxClear(ElytraBehavior.SolverContext context, Vec3 dest, Double growAmount) {
      Vec3 start = context.start;
      boolean ignoreLava = context.ignoreLava;
      if (!this.clearView(start, dest, ignoreLava)) {
         return false;
      } else if (growAmount == null) {
         return true;
      } else {
         AABB bb = context.boundingBox.inflate(growAmount);
         double ox = dest.x - start.x;
         double oy = dest.y - start.y;
         double oz = dest.z - start.z;
         double[] src = new double[]{
            bb.minX,
            bb.minY,
            bb.minZ,
            bb.minX,
            bb.minY,
            bb.maxZ,
            bb.minX,
            bb.maxY,
            bb.minZ,
            bb.minX,
            bb.maxY,
            bb.maxZ,
            bb.maxX,
            bb.minY,
            bb.minZ,
            bb.maxX,
            bb.minY,
            bb.maxZ,
            bb.maxX,
            bb.maxY,
            bb.minZ,
            bb.maxX,
            bb.maxY,
            bb.maxZ
         };
         double[] dst = new double[]{
            bb.minX + ox,
            bb.minY + oy,
            bb.minZ + oz,
            bb.minX + ox,
            bb.minY + oy,
            bb.maxZ + oz,
            bb.minX + ox,
            bb.maxY + oy,
            bb.minZ + oz,
            bb.minX + ox,
            bb.maxY + oy,
            bb.maxZ + oz,
            bb.maxX + ox,
            bb.minY + oy,
            bb.minZ + oz,
            bb.maxX + ox,
            bb.minY + oy,
            bb.maxZ + oz,
            bb.maxX + ox,
            bb.maxY + oy,
            bb.minZ + oz,
            bb.maxX + ox,
            bb.maxY + oy,
            bb.maxZ + oz
         };
         if (Baritone.settings().elytraRenderHitboxRaytraces.value) {
            boolean clear = true;

            for (int i = 0; i < 8; i++) {
               Vec3 s = new Vec3(src[i * 3], src[i * 3 + 1], src[i * 3 + 2]);
               Vec3 d = new Vec3(dst[i * 3], dst[i * 3 + 1], dst[i * 3 + 2]);
               if (!this.clearView(s, d, false)) {
                  clear = false;
               }
            }

            return clear;
         } else {
            return this.raytrace(8, src, dst, 0);
         }
      }
   }

   public boolean clearView(Vec3 start, Vec3 dest, boolean ignoreLava) {
      boolean clear;
      if (!ignoreLava) {
         clear = start.equals(dest) || this.raytrace(start, dest);
      } else {
         clear = this.ctx.world().clip(new ClipContext(start, dest, Block.COLLIDER, Fluid.NONE, this.ctx.player())).getType() == Type.MISS;
      }

      if (Baritone.settings().elytraRenderRaytraces.value) {
         (clear ? this.clearLines : this.blockedLines).add(new Pair<>(start, dest));
      }

      return clear;
   }

   private static FloatArrayList pitchesToSolveFor(float goodPitch, boolean desperate) {
      float minPitch = desperate ? -90.0F : Math.max(goodPitch - (float)Baritone.settings().elytraPitchRange.value.intValue(), -89.0F);
      float maxPitch = desperate ? 90.0F : Math.min(goodPitch + (float)Baritone.settings().elytraPitchRange.value.intValue(), 89.0F);
      FloatArrayList pitchValues = new FloatArrayList(BaritoneMath.fastCeil((double)(maxPitch - minPitch)) + 1);

      for (float pitch = goodPitch; pitch <= maxPitch; pitch++) {
         pitchValues.add(pitch);
      }

      for (float pitch = goodPitch - 1.0F; pitch >= minPitch; pitch--) {
         pitchValues.add(pitch);
      }

      return pitchValues;
   }

   private Pair<Float, Boolean> solvePitch(ElytraBehavior.SolverContext context, Vec3 goal, int relaxation) {
      boolean desperate = relaxation == 2;
      float goodPitch = RotationUtils.calcRotationFromVec3d(context.start, goal, this.ctx.playerRotations()).getPitch();
      FloatArrayList pitches = pitchesToSolveFor(goodPitch, desperate);
      ElytraBehavior.IntTriFunction<ElytraBehavior.PitchResult> solve = (ticksx, ticksBoosted, ticksBoostDelay) -> this.solvePitch(
            context, goal, relaxation, pitches.iterator(), ticksx, ticksBoosted, ticksBoostDelay
         );
      List<ElytraBehavior.IntTriple> tests = new ArrayList<>();
      if (context.boost.isBoosted()) {
         int guaranteed = context.boost.getGuaranteedBoostTicks();
         if (guaranteed == 0) {
            int lookahead = Math.max(4, 10 - context.boost.getMaximumBoostTicks());
            tests.add(new ElytraBehavior.IntTriple(lookahead, 1, 0));
         } else if (guaranteed <= 5) {
            tests.add(new ElytraBehavior.IntTriple(guaranteed + 5, guaranteed, 0));
         } else {
            tests.add(new ElytraBehavior.IntTriple(guaranteed + 1, guaranteed, 0));
         }
      }

      int ticks = desperate
         ? 3
         : (context.boost.isBoosted() ? Math.max(5, context.boost.getGuaranteedBoostTicks()) : Baritone.settings().elytraSimulationTicks.value);
      tests.add(new ElytraBehavior.IntTriple(ticks, context.boost.isBoosted() ? ticks : 0, 0));
      Optional<ElytraBehavior.PitchResult> result = tests.stream().map(i -> solve.apply(i.first, i.second, i.third)).filter(Objects::nonNull).findFirst();
      if (result.isPresent()) {
         return new Pair<>(result.get().pitch, false);
      } else {
         if (desperate) {
            List<ElytraBehavior.IntTriple> testsBoost = new ArrayList<>();
            testsBoost.add(new ElytraBehavior.IntTriple(ticks, 10, 3));
            testsBoost.add(new ElytraBehavior.IntTriple(ticks, 10, 2));
            testsBoost.add(new ElytraBehavior.IntTriple(ticks, 10, 1));
            Optional<ElytraBehavior.PitchResult> resultBoost = testsBoost.stream()
               .map(i -> solve.apply(i.first, i.second, i.third))
               .filter(Objects::nonNull)
               .findFirst();
            if (resultBoost.isPresent()) {
               return new Pair<>(resultBoost.get().pitch, true);
            }
         }

         return null;
      }
   }

   private ElytraBehavior.PitchResult solvePitch(
      ElytraBehavior.SolverContext context, Vec3 goal, int relaxation, FloatIterator pitches, int ticks, int ticksBoosted, int ticksBoostDelay
   ) {
      Vec3 goalDelta = goal.subtract(context.start);
      Vec3 goalDirection = goalDelta.normalize();
      Deque<ElytraBehavior.PitchResult> bestResults = new ArrayDeque<>();

      while (pitches.hasNext()) {
         float pitch = pitches.nextFloat();
         List<Vec3> displacement = this.simulate(context, goalDelta, pitch, ticks, ticksBoosted, ticksBoostDelay);
         if (displacement != null) {
            Vec3 last = displacement.get(displacement.size() - 1);
            double goodness = goalDirection.dot(last.normalize());
            if (this.landingMode) {
               goodness = -goalDelta.subtract(last).length();
            }

            ElytraBehavior.PitchResult bestSoFar = bestResults.peek();
            if (bestSoFar == null || goodness > bestSoFar.dot) {
               bestResults.push(new ElytraBehavior.PitchResult(pitch, goodness, displacement));
            }
         }
      }

      Iterator var17 = bestResults.iterator();

      ElytraBehavior.PitchResult result;
      label40:
      while (true) {
         if (!var17.hasNext()) {
            return null;
         }

         result = (ElytraBehavior.PitchResult)var17.next();
         if (relaxation < 2) {
            int i = result.steps.size() - 1;

            while (true) {
               if (i < 1) {
                  break label40;
               }

               if (!this.clearView(context.start.add(result.steps.get(i)), goal, context.ignoreLava)) {
                  break;
               }

               i--;
            }
         } else {
            if (!this.clearView(context.start.add(result.steps.get(result.steps.size() - 1)), goal, context.ignoreLava)) {
               continue;
            }
            break;
         }
      }

      this.simulationLine = result.steps;
      return result;
   }

   private List<Vec3> simulate(ElytraBehavior.SolverContext context, Vec3 goalDelta, float pitch, int ticks, int ticksBoosted, int ticksBoostDelay) {
      ITickableAimProcessor aimProcessor = context.aimProcessor.fork();
      Vec3 delta = goalDelta;
      Vec3 motion = context.motion;
      AABB hitbox = context.boundingBox;
      List<Vec3> displacement = new ArrayList<>(ticks + 1);
      displacement.add(Vec3.ZERO);
      int remainingTicksBoosted = ticksBoosted;

      for (int i = 0; i < ticks; i++) {
         double cx = hitbox.minX + (hitbox.maxX - hitbox.minX) * 0.5;
         double cz = hitbox.minZ + (hitbox.maxZ - hitbox.minZ) * 0.5;
         if (delta.lengthSqr() < 1.0) {
            break;
         }

         Rotation rotation = aimProcessor.nextRotation(RotationUtils.calcRotationFromVec3d(Vec3.ZERO, delta, this.ctx.playerRotations()).withPitch(pitch));
         Vec3 lookDirection = RotationUtils.calcLookDirectionFromRotation(rotation);
         motion = step(motion, lookDirection, rotation.getPitch());
         delta = delta.subtract(motion);
         AABB inMotion = hitbox.expandTowards(motion.x, motion.y, motion.z).inflate(0.01);
         int xmin = BaritoneMath.fastFloor(inMotion.minX);
         int xmax = BaritoneMath.fastCeil(inMotion.maxX);
         int ymin = BaritoneMath.fastFloor(inMotion.minY);
         int ymax = BaritoneMath.fastCeil(inMotion.maxY);
         int zmin = BaritoneMath.fastFloor(inMotion.minZ);
         int zmax = BaritoneMath.fastCeil(inMotion.maxZ);

         for (int x = xmin; x < xmax; x++) {
            for (int y = ymin; y < ymax; y++) {
               for (int z = zmin; z < zmax; z++) {
                  if (!this.passable(x, y, z, context.ignoreLava)) {
                     return null;
                  }
               }
            }
         }

         hitbox = hitbox.move(motion);
         displacement.add(displacement.get(displacement.size() - 1).add(motion));
         if (i >= ticksBoostDelay && remainingTicksBoosted-- > 0) {
            motion = motion.add(
               lookDirection.x * 0.1 + (lookDirection.x * 1.5 - motion.x) * 0.5,
               lookDirection.y * 0.1 + (lookDirection.y * 1.5 - motion.y) * 0.5,
               lookDirection.z * 0.1 + (lookDirection.z * 1.5 - motion.z) * 0.5
            );
         }
      }

      return displacement;
   }

   private static Vec3 step(Vec3 motion, Vec3 lookDirection, float pitch) {
      double motionX = motion.x;
      double motionY = motion.y;
      double motionZ = motion.z;
      float pitchRadians = pitch * (float) (Math.PI / 180.0);
      double pitchBase2 = Math.sqrt(lookDirection.x * lookDirection.x + lookDirection.z * lookDirection.z);
      double flatMotion = Math.sqrt(motionX * motionX + motionZ * motionZ);
      double thisIsAlwaysOne = lookDirection.length();
      float pitchBase3 = Mth.cos(pitchRadians);
      pitchBase3 = (float)((double)pitchBase3 * (double)pitchBase3 * Math.min(1.0, thisIsAlwaysOne / 0.4));
      motionY += -0.08 + (double)pitchBase3 * 0.06;
      if (motionY < 0.0 && pitchBase2 > 0.0) {
         double speedModifier = motionY * -0.1 * (double)pitchBase3;
         motionY += speedModifier;
         motionX += lookDirection.x * speedModifier / pitchBase2;
         motionZ += lookDirection.z * speedModifier / pitchBase2;
      }

      if (pitchRadians < 0.0F) {
         double anotherSpeedModifier = flatMotion * (double)(-Mth.sin(pitchRadians)) * 0.04;
         motionY += anotherSpeedModifier * 3.2;
         motionX -= lookDirection.x * anotherSpeedModifier / pitchBase2;
         motionZ -= lookDirection.z * anotherSpeedModifier / pitchBase2;
      }

      if (pitchBase2 > 0.0) {
         motionX += (lookDirection.x / pitchBase2 * flatMotion - motionX) * 0.1;
         motionZ += (lookDirection.z / pitchBase2 * flatMotion - motionZ) * 0.1;
      }

      motionX *= 0.99F;
      motionY *= 0.98F;
      motionZ *= 0.99F;
      return new Vec3(motionX, motionY, motionZ);
   }

   private boolean passable(int x, int y, int z, boolean ignoreLava) {
      if (!ignoreLava) {
         return this.passable(x, y, z);
      } else {
         BlockState state = this.bsi.get0(x, y, z);
         return state.getBlock() instanceof AirBlock || MovementHelper.isLava(state);
      }
   }

   private void tickInventoryTransactions() {
      if (this.invTickCountdown <= 0) {
         Runnable r = this.invTransactionQueue.poll();
         if (r != null) {
            r.run();
            this.invTickCountdown = Baritone.settings().ticksBetweenInventoryMoves.value;
         }
      }

      if (this.invTickCountdown > 0) {
         this.invTickCountdown--;
      }
   }

   private void queueWindowClick(int windowId, int slotId, int button, ClickType type) {
      this.invTransactionQueue.add(() -> this.ctx.playerController().windowClick(windowId, slotId, button, type, this.ctx.player()));
   }

   private int findGoodElytra() {
      NonNullList<ItemStack> invy = this.ctx.player().getInventory().items;

      for (int i = 0; i < invy.size(); i++) {
         ItemStack slot = (ItemStack)invy.get(i);
         if (slot.getItem() == Items.ELYTRA && slot.getMaxDamage() - slot.getDamageValue() > Baritone.settings().elytraMinimumDurability.value) {
            return i;
         }
      }

      return -1;
   }

   private void trySwapElytra() {
      if (Baritone.settings().elytraAutoSwap.value && this.invTransactionQueue.isEmpty()) {
         ItemStack chest = this.ctx.player().getItemBySlot(EquipmentSlot.CHEST);
         if (chest.getItem() == Items.ELYTRA && chest.getMaxDamage() - chest.getDamageValue() <= Baritone.settings().elytraMinimumDurability.value) {
            int goodElytraSlot = this.findGoodElytra();
            if (goodElytraSlot != -1) {
               int CHEST_SLOT = 6;
               int slotId = goodElytraSlot < 9 ? goodElytraSlot + 36 : goodElytraSlot;
               this.queueWindowClick(this.ctx.player().inventoryMenu.containerId, slotId, 0, ClickType.PICKUP);
               this.queueWindowClick(this.ctx.player().inventoryMenu.containerId, 6, 0, ClickType.PICKUP);
               this.queueWindowClick(this.ctx.player().inventoryMenu.containerId, slotId, 0, ClickType.PICKUP);
            }
         }
      }
   }

   void logVerbose(String message) {
      if (Baritone.settings().elytraChatSpam.value) {
         this.logDebug(message);
      }
   }

   private BetterBlockPos fixDestination(BetterBlockPos dst) {
      if (this.ctx.world().dimension() == Level.NETHER) {
         if (this.ctx.player().getY() >= 128.0 && dst.y < 128) {
            return new BetterBlockPos(dst.x, 128, dst.z);
         }

         if (this.ctx.player().getY() < 128.0 && dst.y >= 128) {
            return new BetterBlockPos(dst.x, 64, dst.z);
         }
      }

      return dst;
   }

   private BetterBlockPos destinationFixed() {
      return this.fixDestination(this.destination);
   }

   public boolean raytrace(double startX, double startY, double startZ, double endX, double endY, double endZ) {
      int maxHeight = this.npfContext.getMaxHeight() + this.ctx.world().getMinBuildHeight();
      int minHeight = this.ctx.world().getMinBuildHeight();
      boolean isOOB = startY >= (double)maxHeight || endY >= (double)maxHeight || startY < (double)minHeight || endY < (double)minHeight;
      if (isOOB) {
         Vec3 start = new Vec3(startX, startY, startZ);
         Vec3 end = new Vec3(endX, endY, endZ);
         return this.ctx.world().clip(new ClipContext(start, end, Block.COLLIDER, Fluid.NONE, this.ctx.player())).getType() == Type.MISS;
      } else {
         return this.npfContext.raytrace(startX, startY, startZ, endX, endY, endZ);
      }
   }

   public boolean raytrace(Vec3 start, Vec3 end) {
      int maxHeight = this.npfContext.getMaxHeight() + this.ctx.world().getMinBuildHeight();
      int minHeight = this.ctx.world().getMinBuildHeight();
      boolean isOOB = start.y >= (double)maxHeight || end.y >= (double)maxHeight || start.y < (double)minHeight || end.y < (double)minHeight;
      return isOOB
         ? this.ctx.world().clip(new ClipContext(start, end, Block.COLLIDER, Fluid.NONE, this.ctx.player())).getType() == Type.MISS
         : this.npfContext.raytrace(start.x, start.y, start.z, end.x, end.y, end.z);
   }

   public boolean raytrace(int count, double[] src, double[] dst, int visibility) {
      if (src.length == count * 3 && src.length == dst.length) {
         int maxHeight = this.npfContext.getMaxHeight() + this.ctx.world().getMinBuildHeight();
         boolean isOOB = false;

         for (int i = 1; i < src.length; i += 3) {
            if (src[i] >= (double)maxHeight
               || src[i] < (double)this.ctx.world().getMinBuildHeight()
               || dst[i] >= (double)maxHeight
               || dst[i] < (double)this.ctx.world().getMinBuildHeight()) {
               isOOB = true;
               break;
            }
         }

         if (isOOB) {
            for (int ix = 0; ix < count; ix++) {
               Vec3 start = new Vec3(src[ix * 3], src[ix * 3 + 1], src[ix * 3 + 2]);
               Vec3 end = new Vec3(dst[ix * 3], dst[ix * 3 + 1], dst[ix * 3 + 2]);
               if (this.ctx.world().clip(new ClipContext(start, end, Block.COLLIDER, Fluid.NONE, this.ctx.player())).getType() != Type.MISS) {
                  return false;
               }
            }

            return true;
         } else {
            return this.npfContext.raytrace(count, src, dst, visibility);
         }
      } else {
         throw new IllegalArgumentException("Expected source and dst to have length of " + count * 3);
      }
   }

   public boolean passable(int x, int y, int z) {
      return y < this.ctx.world().getMaxBuildHeight() && y >= this.ctx.world().getMinBuildHeight() ? this.npfContext.passable(x, y, z) : true;
   }

   private static final class FireworkBoost {
      private final Integer fireworkTicksExisted;
      private final int minimumBoostTicks;
      private final int maximumBoostTicks;

      public FireworkBoost(Integer fireworkTicksExisted, int minimumBoostTicks) {
         this.fireworkTicksExisted = fireworkTicksExisted;
         this.minimumBoostTicks = minimumBoostTicks;
         this.maximumBoostTicks = minimumBoostTicks + 11;
      }

      public boolean isBoosted() {
         return this.fireworkTicksExisted != null;
      }

      public int getGuaranteedBoostTicks() {
         return this.isBoosted() ? Math.max(0, this.minimumBoostTicks - this.fireworkTicksExisted) : 0;
      }

      public int getMaximumBoostTicks() {
         return this.isBoosted() ? Math.max(0, this.maximumBoostTicks - this.fireworkTicksExisted) : 0;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else if (o != null && o.getClass() == ElytraBehavior.FireworkBoost.class) {
            ElytraBehavior.FireworkBoost other = (ElytraBehavior.FireworkBoost)o;
            return !this.isBoosted() && !other.isBoosted()
               ? true
               : Objects.equals(this.fireworkTicksExisted, other.fireworkTicksExisted)
                  && this.minimumBoostTicks == other.minimumBoostTicks
                  && this.maximumBoostTicks == other.maximumBoostTicks;
         } else {
            return false;
         }
      }
   }

   @FunctionalInterface
   private interface IntTriFunction<T> {
      T apply(int var1, int var2, int var3);
   }

   private static final class IntTriple {
      public final int first;
      public final int second;
      public final int third;

      public IntTriple(int first, int second, int third) {
         this.first = first;
         this.second = second;
         this.third = third;
      }
   }

   public final class PathManager {
      public NetherPath path;
      private boolean completePath;
      private boolean recalculating;
      private int maxPlayerNear;
      private int ticksNearUnchanged;
      private int playerNear;

      public PathManager() {
         this.clear();
      }

      public void tick() {
         this.updatePlayerNear();
         int prevMaxNear = this.maxPlayerNear;
         this.maxPlayerNear = Math.max(this.maxPlayerNear, this.playerNear);
         if (this.maxPlayerNear == prevMaxNear && ElytraBehavior.this.ctx.player().isFallFlying()) {
            this.ticksNearUnchanged++;
         } else {
            this.ticksNearUnchanged = 0;
         }

         int minY = ElytraBehavior.this.ctx.world().dimensionType().minY();
         int y = ElytraBehavior.this.ctx.playerFeet().y;
         ElytraBehavior.this.npfContext.acquireReadLock();

         try {
            this.pathfindAroundObstacles();
         } finally {
            ElytraBehavior.this.npfContext.releaseReadLock();
         }

         this.attemptNextSegment();
      }

      public CompletableFuture<Void> pathToDestination() {
         return this.pathToDestination(ElytraBehavior.this.ctx.playerFeet());
      }

      public CompletableFuture<Void> pathToDestination(BlockPos from) {
         long start = System.nanoTime();
         return this.path0(from, ElytraBehavior.this.destinationFixed(), UnaryOperator.identity())
            .thenRun(
               () -> {
                  double distance = this.path.get(0).distanceTo(this.path.get(this.path.size() - 1));
                  if (this.completePath) {
                     ElytraBehavior.this.logVerbose(
                        String.format("Computed path (%.1f blocks in %.4f seconds)", distance, (double)(System.nanoTime() - start) / 1.0E9)
                     );
                  } else {
                     ElytraBehavior.this.logVerbose(
                        String.format("Computed segment (Next %.1f blocks in %.4f seconds)", distance, (double)(System.nanoTime() - start) / 1.0E9)
                     );
                  }
               }
            )
            .whenComplete((result, ex) -> {
               this.recalculating = false;
               if (ex != null) {
                  Throwable cause = ex.getCause();
                  if (cause instanceof PathCalculationException) {
                     ElytraBehavior.this.logDirect("Failed to compute path to destination");
                  } else {
                     ElytraBehavior.this.logUnhandledException(cause);
                  }
               }
            });
      }

      public CompletableFuture<Void> pathRecalcSegment(OptionalInt upToIncl) {
         if (this.recalculating) {
            throw new IllegalStateException("already recalculating");
         } else {
            this.recalculating = true;
            List<BetterBlockPos> after = upToIncl.isPresent() ? this.path.subList(upToIncl.getAsInt() + 1, this.path.size()) : Collections.emptyList();
            boolean complete = this.completePath;
            return this.path0(
                  ElytraBehavior.this.ctx.playerFeet(),
                  upToIncl.isPresent() ? ElytraBehavior.this.fixDestination(this.path.get(upToIncl.getAsInt())) : ElytraBehavior.this.destinationFixed(),
                  segment -> segment.append(after.stream(), complete || segment.isFinished() && !upToIncl.isPresent())
               )
               .whenComplete((result, ex) -> {
                  this.recalculating = false;
                  if (ex != null) {
                     Throwable cause = ex.getCause();
                     if (cause instanceof PathCalculationException) {
                        ElytraBehavior.this.logDirect("Failed to recompute segment");
                     } else {
                        ElytraBehavior.this.logUnhandledException(cause);
                     }
                  }
               });
         }
      }

      public void pathNextSegment(int afterIncl) {
         if (!this.recalculating) {
            this.recalculating = true;
            List<BetterBlockPos> before = this.path.subList(0, afterIncl + 1);
            long start = System.nanoTime();
            BetterBlockPos pathStart = this.path.get(afterIncl);
            this.path0(pathStart, ElytraBehavior.this.destinationFixed(), segment -> segment.prepend(before.stream()))
               .thenRun(
                  () -> {
                     int recompute = this.path.size() - before.size() - 1;
                     double distance = recompute > 0 ? this.path.get(0).distanceTo(this.path.get(recompute)) : 0.0;
                     if (this.completePath) {
                        ElytraBehavior.this.logVerbose(
                           String.format("Computed path (%.1f blocks in %.4f seconds)", distance, (double)(System.nanoTime() - start) / 1.0E9)
                        );
                     } else {
                        ElytraBehavior.this.logVerbose(
                           String.format("Computed segment (Next %.1f blocks in %.4f seconds)", distance, (double)(System.nanoTime() - start) / 1.0E9)
                        );
                     }
                  }
               )
               .whenComplete(
                  (result, ex) -> {
                     this.recalculating = false;
                     if (ex != null) {
                        Throwable cause = ex.getCause();
                        if (cause instanceof PathCalculationException) {
                           ElytraBehavior.this.logDirect("Failed to compute next segment");
                           if (ElytraBehavior.this.ctx.player().distanceToSqr(pathStart.getCenter()) < 256.0) {
                              ElytraBehavior.this.logVerbose(
                                 "Player is near the segment start, therefore repeating this calculation is pointless. Marking as complete"
                              );
                              this.completePath = true;
                           }
                        } else {
                           ElytraBehavior.this.logUnhandledException(cause);
                        }
                     }
                  }
               );
         }
      }

      public void clear() {
         this.path = NetherPath.emptyPath();
         this.completePath = true;
         this.recalculating = false;
         this.playerNear = 0;
         this.ticksNearUnchanged = 0;
         this.maxPlayerNear = 0;
      }

      private void setPath(UnpackedSegment segment) {
         List<BetterBlockPos> path = segment.collect();
         if (ElytraBehavior.this.appendDestination) {
            BlockPos dest = ElytraBehavior.this.destinationFixed();
            BlockPos last = !path.isEmpty() ? path.get(path.size() - 1) : null;
            if (last != null && ElytraBehavior.this.clearView(Vec3.atLowerCornerOf(dest), Vec3.atLowerCornerOf(last), false)) {
               path.add(new BetterBlockPos(dest));
            } else {
               ElytraBehavior.this.logDirect("unable to land at " + dest);
               ElytraBehavior.this.process.landingSpotIsBad(new BetterBlockPos(dest));
            }
         }

         this.path = new NetherPath(path);
         this.completePath = segment.isFinished();
         this.playerNear = 0;
         this.ticksNearUnchanged = 0;
         this.maxPlayerNear = 0;
      }

      public NetherPath getPath() {
         return this.path;
      }

      public int getNear() {
         return this.playerNear;
      }

      private CompletableFuture<Void> path0(BlockPos src, BlockPos dst, UnaryOperator<UnpackedSegment> operator) {
         return ElytraBehavior.this.pathFinder
            .pathFindAsync(src, dst)
            .thenApply(operator)
            .thenAcceptAsync(this::setPath, ElytraBehavior.this.ctx.minecraft()::execute);
      }

      private void pathfindAroundObstacles() {
         if (!this.recalculating) {
            int rangeStartIncl = this.playerNear;
            int rangeEndExcl = this.playerNear;

            while (rangeEndExcl < this.path.size() && ElytraBehavior.this.npfContext.hasChunk(new ChunkPos(this.path.get(rangeEndExcl)))) {
               rangeEndExcl++;
            }

            if (rangeStartIncl < rangeEndExcl) {
               BetterBlockPos rangeStart = this.path.get(rangeStartIncl);
               if (ElytraBehavior.this.passable(rangeStart.x, rangeStart.y, rangeStart.z, false)) {
                  if (ElytraBehavior.this.process.state != ElytraProcess.State.LANDING && this.ticksNearUnchanged > 100) {
                     this.pathRecalcSegment(OptionalInt.of(rangeEndExcl - 1))
                        .thenRun(() -> ElytraBehavior.this.logVerbose("Recalculating segment, no progress in last 100 ticks"));
                     this.ticksNearUnchanged = 0;
                  } else {
                     boolean canSeeAny = false;

                     for (int i = rangeStartIncl; i < rangeEndExcl - 1; i++) {
                        if (ElytraBehavior.this.clearView(ElytraBehavior.this.ctx.playerFeetAsVec(), this.path.getVec(i), false)
                           || ElytraBehavior.this.clearView(ElytraBehavior.this.ctx.playerHead(), this.path.getVec(i), false)) {
                           canSeeAny = true;
                        }

                        if (!ElytraBehavior.this.clearView(this.path.getVec(i), this.path.getVec(i + 1), false)) {
                           BetterBlockPos dest = ElytraBehavior.this.destinationFixed();
                           OptionalInt rejoinMainPathAt;
                           if (this.path.get(rangeEndExcl - 1).distanceSq(dest) < ElytraBehavior.this.ctx.playerFeet().distanceSq(dest)) {
                              rejoinMainPathAt = OptionalInt.of(rangeEndExcl - 1);
                           } else {
                              rejoinMainPathAt = OptionalInt.empty();
                           }

                           BetterBlockPos blockage = this.path.get(i);
                           double distance = ElytraBehavior.this.ctx.playerFeet().distanceTo(this.path.get(rejoinMainPathAt.orElse(this.path.size() - 1)));
                           long start = System.nanoTime();
                           this.pathRecalcSegment(rejoinMainPathAt)
                              .thenRun(
                                 () -> ElytraBehavior.this.logVerbose(
                                       String.format(
                                          "Recalculated segment around path blockage near %s %s %s (next %.1f blocks in %.4f seconds)",
                                          SettingsUtil.maybeCensor(blockage.x),
                                          SettingsUtil.maybeCensor(blockage.y),
                                          SettingsUtil.maybeCensor(blockage.z),
                                          distance,
                                          (double)(System.nanoTime() - start) / 1.0E9
                                       )
                                    )
                              );
                           return;
                        }
                     }

                     if (!canSeeAny && rangeStartIncl < rangeEndExcl - 2 && ElytraBehavior.this.process.state != ElytraProcess.State.GET_TO_JUMP) {
                        this.pathRecalcSegment(OptionalInt.of(rangeEndExcl - 1))
                           .thenRun(() -> ElytraBehavior.this.logVerbose("Recalculated segment since no path points were visible"));
                     }
                  }
               }
            }
         }
      }

      private void attemptNextSegment() {
         if (!this.recalculating) {
            int last = this.path.size() - 1;
            BetterBlockPos lastPos = this.path.get(this.path.size() - 1);
            if (!this.completePath && ElytraBehavior.this.ctx.world().getChunkSource().hasChunk(lastPos.x >> 4, lastPos.z >> 4)) {
               this.pathNextSegment(last);
            }
         }
      }

      public void updatePlayerNear() {
         if (!this.path.isEmpty()) {
            int index = this.playerNear;
            BetterBlockPos pos = ElytraBehavior.this.ctx.playerFeet();

            for (int i = index; i >= Math.max(index - 1000, 0); i -= 10) {
               if (this.path.get(i).distanceSq(pos) < this.path.get(index).distanceSq(pos)) {
                  index = i;
               }
            }

            for (int ix = index; ix < Math.min(index + 1000, this.path.size()); ix += 10) {
               if (this.path.get(ix).distanceSq(pos) < this.path.get(index).distanceSq(pos)) {
                  index = ix;
               }
            }

            for (int ixx = index; ixx >= Math.max(index - 50, 0); ixx--) {
               if (this.path.get(ixx).distanceSq(pos) < this.path.get(index).distanceSq(pos)) {
                  index = ixx;
               }
            }

            for (int ixxx = index; ixxx < Math.min(index + 50, this.path.size()); ixxx++) {
               if (this.path.get(ixxx).distanceSq(pos) < this.path.get(index).distanceSq(pos)) {
                  index = ixxx;
               }
            }

            this.playerNear = index;
         }
      }

      public boolean isComplete() {
         return this.completePath;
      }
   }

   private static final class PitchResult {
      public final float pitch;
      public final double dot;
      public final List<Vec3> steps;

      public PitchResult(float pitch, double dot, List<Vec3> steps) {
         this.pitch = pitch;
         this.dot = dot;
         this.steps = steps;
      }
   }

   private static final class Solution {
      public final ElytraBehavior.SolverContext context;
      public final Rotation rotation;
      public final Vec3 goingTo;
      public final boolean solvedPitch;
      public final boolean forceUseFirework;

      public Solution(ElytraBehavior.SolverContext context, Rotation rotation, Vec3 goingTo, boolean solvedPitch, boolean forceUseFirework) {
         this.context = context;
         this.rotation = rotation;
         this.goingTo = goingTo;
         this.solvedPitch = solvedPitch;
         this.forceUseFirework = forceUseFirework;
      }
   }

   private final class SolverContext {
      public final NetherPath path = ElytraBehavior.this.pathManager.getPath();
      public final int playerNear = ElytraBehavior.this.pathManager.getNear();
      public final Vec3 start = ElytraBehavior.this.ctx.playerFeetAsVec();
      public final Vec3 motion = ElytraBehavior.this.ctx.playerMotion();
      public final AABB boundingBox = ElytraBehavior.this.ctx.player().getBoundingBox();
      public final boolean ignoreLava = ElytraBehavior.this.ctx.player().isInLava();
      public final ElytraBehavior.FireworkBoost boost;
      public final IAimProcessor aimProcessor;

      public SolverContext(boolean async) {
         Integer fireworkTicksExisted;
         if (async && ElytraBehavior.this.deployedFireworkLastTick) {
            int[] counter = ElytraBehavior.this.nextTickBoostCounter;
            fireworkTicksExisted = counter[1] > counter[0] ? 0 : null;
         } else {
            fireworkTicksExisted = ElytraBehavior.this.getAttachedFirework().map(e -> e.tickCount).orElse(null);
         }

         this.boost = new ElytraBehavior.FireworkBoost(fireworkTicksExisted, ElytraBehavior.this.minimumBoostTicks);
         ITickableAimProcessor aim = ElytraBehavior.this.baritone.getLookBehavior().getAimProcessor().fork();
         if (async) {
            aim.advance(1);
         }

         this.aimProcessor = aim;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else if (o != null && o.getClass() == ElytraBehavior.SolverContext.class) {
            ElytraBehavior.SolverContext other = (ElytraBehavior.SolverContext)o;
            return this.path == other.path
               && this.playerNear == other.playerNear
               && Objects.equals(this.start, other.start)
               && Objects.equals(this.motion, other.motion)
               && Objects.equals(this.boundingBox, other.boundingBox)
               && this.ignoreLava == other.ignoreLava
               && Objects.equals(this.boost, other.boost);
         } else {
            return false;
         }
      }
   }
}
