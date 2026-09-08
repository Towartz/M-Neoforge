package baritone.process;

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.event.events.BlockChangeEvent;
import baritone.api.event.events.ChunkEvent;
import baritone.api.event.events.PacketEvent;
import baritone.api.event.events.RenderEvent;
import baritone.api.event.events.TickEvent;
import baritone.api.event.events.WorldEvent;
import baritone.api.event.events.type.EventState;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.pathing.goals.GoalYLevel;
import baritone.api.pathing.movement.IMovement;
import baritone.api.pathing.path.IPathExecutor;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.IElytraProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.input.Input;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.movements.MovementFall;
import baritone.process.elytra.ElytraBehavior;
import baritone.process.elytra.NetherPathfinderContext;
import baritone.process.elytra.NullElytraProcess;
import baritone.utils.BaritoneProcessHelper;
import baritone.utils.PathingCommandContext;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;

public class ElytraProcess extends BaritoneProcessHelper implements IBaritoneProcess, IElytraProcess, AbstractGameEventListener {
   public ElytraProcess.State state;
   private boolean goingToLandingSpot;
   private BetterBlockPos landingSpot;
   private boolean reachedGoal;
   private Goal goal;
   private ElytraBehavior behavior;
   private NetherPathfinderContext npfContext;
   private boolean predictingTerrain;
   private boolean allowTight;
   private boolean allowAboveBuildLimit;
   private boolean allowAboveRoof;
   private final Semaphore npfSema = new Semaphore(1);
   private static final int SHORT_LANDING_COLUMN_HEIGHT = 15;
   private static final int LONG_LANDING_COLUMN_HEIGHT = 39;
   private static final long LANDING_SEARCH_BUDGET_NANOS = TimeUnit.MILLISECONDS.toNanos(25L);
   private int landingColumnHeight = 15;
   private Set<BetterBlockPos> badLandingSpots = new HashSet<>();
   private ElytraProcess.LandingSearchState landingSearchState;
   private static final String AUTO_JUMP_FAILURE_MSG = "Failed to compute a walking path to a spot to jump off from. Consider starting from a higher location, near an overhang. Or, you can disable elytraAutoJump and just manually begin gliding.";

   @Override
   public void onLostControl() {
      this.onLostControl(true);
   }

   public void onLostControl(boolean destroyNpf) {
      this.state = ElytraProcess.State.START_FLYING;
      this.goingToLandingSpot = false;
      this.landingSpot = null;
      this.landingSearchState = null;
      this.reachedGoal = false;
      this.goal = null;
      this.destroyBehaviorAsync();
      if (destroyNpf) {
         this.destroyNpfContextAsync();
      }
   }

   private ElytraProcess(Baritone baritone) {
      super(baritone);
      baritone.getGameEventHandler().registerEventListener(this);
   }

   public static IElytraProcess create(Baritone baritone) {
      return (IElytraProcess)(NetherPathfinderContext.isSupported() ? new ElytraProcess(baritone) : new NullElytraProcess(baritone));
   }

   @Override
   public boolean isActive() {
      return this.behavior != null;
   }

   @Override
   public void resetState() {
      BlockPos destination = this.currentDestination();
      this.onLostControl();
      if (destination != null) {
         this.pathTo(destination);
         this.repackChunks();
      }
   }

   @Override
   public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
      try {
         long seedSetting = Baritone.settings().elytraNetherSeed.value;
         if (seedSetting != this.behavior.npfContext.getSeed()) {
            this.logDirect("Nether seed changed, recalculating path");
            this.resetState();
         }

         if (this.predictingTerrain != Baritone.settings().elytraPredictTerrain.value && this.ctx.player().level().dimension() == Level.NETHER) {
            this.logDirect("elytraPredictTerrain setting changed, recalculating path from scratch");
            this.predictingTerrain = Baritone.settings().elytraPredictTerrain.value;
            this.resetState();
         }

         if (this.allowTight != Baritone.settings().elytraAllowTightSpaces.value) {
            this.logDirect("elytraAllowTightSpaces setting changed, recalculating path from scratch");
            this.allowTight = Baritone.settings().elytraAllowTightSpaces.value;
            this.resetState();
         }

         if (this.allowAboveBuildLimit != Baritone.settings().elytraAllowAboveBuildLimit.value) {
            this.logDirect("elytraAllowAboveBuildLimit setting changed, recalculating path from scratch");
            this.allowAboveBuildLimit = Baritone.settings().elytraAllowAboveBuildLimit.value;
            this.resetState();
         }

         if (this.allowAboveRoof != Baritone.settings().elytraAllowAboveRoof.value && this.ctx.player().level().dimension() == Level.NETHER) {
            this.logDirect("elytraAllowAboveRoof setting changed, recalculating path from scratch");
            this.allowAboveRoof = Baritone.settings().elytraAllowAboveRoof.value;
            this.resetState();
         }
      } catch (IllegalArgumentException var12) {
         this.logDirect(var12.getMessage(), ChatFormatting.RED);
         return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
      }

      this.behavior.onTick();
      if (calcFailed) {
         this.onLostControl();
         this.logDirect(
            "Failed to compute a walking path to a spot to jump off from. Consider starting from a higher location, near an overhang. Or, you can disable elytraAutoJump and just manually begin gliding."
         );
         return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
      } else {
         boolean safetyLanding = false;
         if (this.ctx.player().isFallFlying() && this.shouldLandForSafety()) {
            if (Baritone.settings().elytraAllowEmergencyLand.value) {
               this.logDirect("Emergency landing - almost out of elytra durability or fireworks");
               safetyLanding = true;
            } else {
               this.logDirect("almost out of elytra durability or fireworks, but I'm going to continue since elytraAllowEmergencyLand is false");
            }
         }

         if (this.ctx.player().isFallFlying() && this.state != ElytraProcess.State.LANDING && (this.behavior.pathManager.isComplete() || safetyLanding)) {
            BetterBlockPos last = this.behavior.pathManager.path.getLast();
            if (last != null
               && (this.ctx.player().position().distanceToSqr(last.getCenter()) < 2304.0 || safetyLanding)
               && (!this.goingToLandingSpot || safetyLanding && this.landingSpot == null)) {
               if (this.landingSearchState == null) {
                  this.logDirect("Path complete, searching for safe landing spot...");
               }

               BetterBlockPos landingSpot = this.findSafeLandingSpot(this.ctx.playerFeet());
               if (landingSpot != null) {
                  this.logDirect("Found potential landing spot.");
                  this.pathTo0(landingSpot, true);
                  this.landingSpot = landingSpot;
                  this.goingToLandingSpot = true;
               } else {
                  this.goingToLandingSpot = false;
               }
            }

            if (last != null && this.ctx.player().position().distanceToSqr(last.getCenter()) < 1.0) {
               if (Baritone.settings().notificationOnPathComplete.value && !this.reachedGoal) {
                  this.logNotification("Pathing complete", false);
               }

               if (Baritone.settings().disconnectOnArrival.value && !this.reachedGoal) {
                  this.onLostControl();
                  this.ctx.world().disconnect();
                  return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
               }

               this.reachedGoal = true;
               if (this.goingToLandingSpot && this.landingSpot != null) {
                  this.state = ElytraProcess.State.LANDING;
                  this.logDirect("Above the landing spot, landing...");
               }
            }
         }

         if (this.state == ElytraProcess.State.LANDING) {
            BetterBlockPos endPos = this.landingSpot != null ? this.landingSpot : this.behavior.pathManager.path.getLast();
            if (this.ctx.player().isFallFlying() && endPos != null) {
               Vec3 from = this.ctx.player().position();
               Vec3 to = new Vec3((double)endPos.x + 0.5, from.y, (double)endPos.z + 0.5);
               Rotation rotation = RotationUtils.calcRotationFromVec3d(from, to, this.ctx.playerRotations());
               this.baritone.getLookBehavior().updateTarget(new Rotation(rotation.getYaw(), 0.0F), false);
               if (this.ctx.player().position().y < (double)(endPos.y - this.landingColumnHeight)) {
                  this.logDirect("bad landing spot, trying again...");
                  this.landingSpotIsBad(endPos);
               }
            }
         }

         if (this.ctx.player().isFallFlying()) {
            this.behavior.landingMode = this.state == ElytraProcess.State.LANDING;
            this.goal = null;
            this.baritone.getInputOverrideHandler().clearAllKeys();
            if (this.behavior.npfContext.tryAcquireReadLock()) {
               try {
                  this.behavior.tick();
               } finally {
                  this.behavior.npfContext.releaseReadLock();
               }
            }

            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
         } else if (this.state == ElytraProcess.State.LANDING) {
            if (this.ctx.playerMotion().multiply(1.0, 0.0, 1.0).length() > 0.001) {
               this.logDirect("Landed, but still moving, waiting for velocity to die down... ");
               this.baritone.getInputOverrideHandler().setInputForceState(Input.SNEAK, true);
               return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            } else {
               this.logDirect("Done :)");
               this.baritone.getInputOverrideHandler().clearAllKeys();
               this.onLostControl();
               return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
         } else {
            if (this.state == ElytraProcess.State.FLYING || this.state == ElytraProcess.State.START_FLYING) {
               this.state = this.ctx.player().onGround() && Baritone.settings().elytraAutoJump.value
                  ? ElytraProcess.State.LOCATE_JUMP
                  : ElytraProcess.State.START_FLYING;
            }

            if (this.state == ElytraProcess.State.LOCATE_JUMP) {
               if (this.shouldLandForSafety()) {
                  this.logDirect("Not taking off, because elytra durability or fireworks are so low that I would immediately emergency land anyway.");
                  this.onLostControl();
                  return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
               } else {
                  if (this.goal == null) {
                     this.goal = new GoalYLevel(31);
                  }

                  IPathExecutor executor = this.baritone.getPathingBehavior().getCurrent();
                  if (executor != null && executor.getPath().getGoal() == this.goal) {
                     IMovement fall = executor.getPath().movements().stream().filter(movement -> movement instanceof MovementFall).findFirst().orElse(null);
                     if (fall == null) {
                        this.onLostControl();
                        this.logDirect(
                           "Failed to compute a walking path to a spot to jump off from. Consider starting from a higher location, near an overhang. Or, you can disable elytraAutoJump and just manually begin gliding."
                        );
                        return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
                     }

                     BetterBlockPos from = new BetterBlockPos(
                        (fall.getSrc().x + fall.getDest().x) / 2, (fall.getSrc().y + fall.getDest().y) / 2, (fall.getSrc().z + fall.getDest().z) / 2
                     );
                     this.behavior.pathManager.pathToDestination(from).whenComplete((result, ex) -> {
                        if (ex == null) {
                           this.state = ElytraProcess.State.GET_TO_JUMP;
                        } else {
                           this.onLostControl();
                        }
                     });
                     this.state = ElytraProcess.State.PAUSE;
                  }

                  return new PathingCommandContext(this.goal, PathingCommandType.SET_GOAL_AND_PAUSE, new ElytraProcess.WalkOffCalculationContext(this.baritone));
               }
            } else if (this.state == ElytraProcess.State.PAUSE) {
               return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            } else {
               if (this.state == ElytraProcess.State.GET_TO_JUMP) {
                  IPathExecutor executor = this.baritone.getPathingBehavior().getCurrent();
                  boolean canStartFlying = this.ctx.player().fallDistance > 1.0F
                     && !isSafeToCancel
                     && executor != null
                     && executor.getPath().movements().get(executor.getPosition()) instanceof MovementFall;
                  if (!canStartFlying) {
                     return new PathingCommand(null, PathingCommandType.SET_GOAL_AND_PATH);
                  }

                  this.state = ElytraProcess.State.START_FLYING;
               }

               if (this.state == ElytraProcess.State.START_FLYING) {
                  if (!isSafeToCancel) {
                     this.baritone.getPathingBehavior().secretInternalSegmentCancel();
                  }

                  this.baritone.getInputOverrideHandler().clearAllKeys();
                  if (this.ctx.player().fallDistance > 1.0F) {
                     this.baritone.getInputOverrideHandler().setInputForceState(Input.JUMP, true);
                  }
               }

               return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
            }
         }
      }
   }

   public void landingSpotIsBad(BetterBlockPos endPos) {
      this.badLandingSpots.add(endPos);
      this.goingToLandingSpot = false;
      this.landingSpot = null;
      this.landingSearchState = null;
      this.state = ElytraProcess.State.FLYING;
   }

   private void destroyBehaviorAsync() {
      ElytraBehavior behavior = this.behavior;
      if (behavior != null) {
         this.behavior = null;
         Baritone.getExecutor().execute(() -> behavior.destroy());
      }
   }

   @Override
   public double priority() {
      return 0.0;
   }

   @Override
   public String displayName0() {
      return "Elytra - " + this.state.description;
   }

   @Override
   public void repackChunks() {
      if (this.npfContext != null) {
         ChunkSource chunkProvider = this.ctx.world().getChunkSource();
         BetterBlockPos playerPos = this.ctx.playerFeet();
         int playerChunkX = playerPos.getX() >> 4;
         int playerChunkZ = playerPos.getZ() >> 4;
         int minX = playerChunkX - 40;
         int minZ = playerChunkZ - 40;
         int maxX = playerChunkX + 40;
         int maxZ = playerChunkZ + 40;

         for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
               LevelChunk chunk = chunkProvider.getChunk(x, z, false);
               if (chunk != null && !chunk.isEmpty()) {
                  this.npfContext.queueForPacking(chunk);
               }
            }
         }
      }
   }

   @Override
   public BlockPos currentDestination() {
      return this.behavior != null ? this.behavior.destination : null;
   }

   @Override
   public List<BetterBlockPos> getPath() {
      return (List<BetterBlockPos>)(this.behavior != null ? this.behavior.pathManager.getPath() : Collections.emptyList());
   }

   @Override
   public void pathTo(BlockPos destination) {
      if (!this.isSupportedPos(destination)) {
         throw new IllegalArgumentException("The goal must be within bounds to use elytra flight.");
      } else if (this.ctx.player() != null && !this.isSupportedPos(this.ctx.playerFeet())) {
         throw new IllegalArgumentException("The player must be within bounds to use elytra flight.");
      } else {
         this.pathTo0(destination, false);
      }
   }

   private void pathTo0(BlockPos destination, boolean appendDestination) {
      if (this.ctx.player() != null) {
         this.onLostControl(false);
         this.predictingTerrain = this.ctx.player().level().dimension() == Level.NETHER && Baritone.settings().elytraPredictTerrain.value;
         this.allowTight = Baritone.settings().elytraAllowTightSpaces.value;
         this.allowAboveBuildLimit = Baritone.settings().elytraAllowAboveBuildLimit.value;
         this.allowAboveRoof = Baritone.settings().elytraAllowAboveRoof.value;
         this.behavior = new ElytraBehavior(this.baritone, this, this.getNpfContext(), destination, appendDestination);
         if (this.ctx.world() != null) {
            this.repackChunks();
         }

         this.behavior.pathTo();
      }
   }

   @Override
   public void pathTo(Goal iGoal) {
      int x;
      int y;
      int z;
      if (iGoal instanceof GoalXZ goal) {
         x = goal.getX();
         y = 64;
         z = goal.getZ();
      } else {
         if (!(iGoal instanceof GoalBlock goal)) {
            throw new IllegalArgumentException("The goal must be a GoalXZ or GoalBlock");
         }

         x = goal.x;
         y = goal.y;
         z = goal.z;
      }

      this.pathTo(new BlockPos(x, y, z));
   }

   private boolean isSupportedPos(BlockPos pos) {
      boolean isNether = this.ctx.world().dimension() == Level.NETHER;
      int minY = this.ctx.world().dimensionType().minY();
      int maxY = isNether && !Baritone.settings().elytraAllowAboveRoof.value ? 127 : Math.min(minY + 384, this.ctx.world().dimensionType().height() + minY);
      boolean aboveRoof = Baritone.settings().elytraAllowAboveRoof.value;
      boolean aboveBuild = Baritone.settings().elytraAllowAboveBuildLimit.value;
      boolean enforceMaxY = isNether ? !aboveRoof || !aboveBuild : !aboveBuild;
      return pos.getY() < minY ? false : !enforceMaxY || pos.getY() < maxY;
   }

   private boolean shouldLandForSafety() {
      ItemStack chest = this.ctx.player().getItemBySlot(EquipmentSlot.CHEST);
      if (chest.getItem() == Items.ELYTRA && chest.getMaxDamage() - chest.getDamageValue() >= Baritone.settings().elytraMinimumDurability.value) {
         NonNullList<ItemStack> inv = this.ctx.player().getInventory().items;
         int qty = 0;

         for (int i = 0; i < 36; i++) {
            if (ElytraBehavior.isFireworks((ItemStack)inv.get(i))) {
               qty += ((ItemStack)inv.get(i)).getCount();
            }
         }

         return qty <= Baritone.settings().elytraMinFireworksBeforeLanding.value;
      } else {
         return true;
      }
   }

   @Override
   public boolean isLoaded() {
      return true;
   }

   @Override
   public boolean isSafeToCancel() {
      return !this.isActive() || this.state != ElytraProcess.State.FLYING && this.state != ElytraProcess.State.START_FLYING;
   }

   @Override
   public void onRenderPass(RenderEvent event) {
      if (this.behavior != null) {
         this.behavior.onRenderPass(event);
      }
   }

   @Override
   public void onWorldEvent(WorldEvent event) {
      if (event.getWorld() != null && event.getState() == EventState.POST) {
         this.destroyBehaviorAsync();
      }
   }

   @Override
   public void onChunkEvent(ChunkEvent event) {
      if (this.behavior != null) {
         this.behavior.onChunkEvent(event);
      }
   }

   @Override
   public void onBlockChange(BlockChangeEvent event) {
      if (this.behavior != null) {
         this.behavior.onBlockChange(event);
      }
   }

   @Override
   public void onReceivePacket(PacketEvent event) {
      if (this.behavior != null) {
         this.behavior.onReceivePacket(event);
      }
   }

   @Override
   public void onPostTick(TickEvent event) {
      IBaritoneProcess procThisTick = this.baritone.getPathingControlManager().mostRecentInControl().orElse(null);
      if (this.behavior != null && procThisTick == this) {
         this.behavior.onPostTick(event);
      }
   }

   private static boolean isInBounds(Level dim, BlockPos pos) {
      DimensionType dimType = dim.dimensionType();
      int minY = dimType.minY();
      int maxY = dim.dimension() == Level.NETHER && !Baritone.settings().elytraAllowAboveRoof.value ? 127 : Math.min(minY + 384, dimType.height() + minY);
      return pos.getY() >= minY && pos.getY() < maxY;
   }

   private boolean isSafeBlock(Block block) {
      return block == Blocks.NETHERRACK
         || block == Blocks.GRAVEL
         || block == Blocks.SOUL_SAND
         || block == Blocks.SOUL_SOIL
         || block == Blocks.NETHER_BRICKS && Baritone.settings().elytraAllowLandOnNetherFortress.value
         || block == Blocks.STONE
         || block == Blocks.DEEPSLATE
         || block == Blocks.GRASS_BLOCK
         || block == Blocks.SAND
         || block == Blocks.RED_SAND
         || block == Blocks.TERRACOTTA
         || block == Blocks.SNOW
         || block == Blocks.ICE
         || block == Blocks.MYCELIUM
         || block == Blocks.PODZOL
         || block == Blocks.DARK_OAK_LEAVES
         || block == Blocks.JUNGLE_LEAVES
         || block == Blocks.END_STONE
         || block == Blocks.BEDROCK
         || block == Blocks.OBSIDIAN
         || block == Blocks.COBBLESTONE;
   }

   private boolean isSafeBlock(BlockPos pos) {
      return this.isSafeBlock(this.ctx.world().getBlockState(pos).getBlock());
   }

   private boolean isAtEdge(BlockPos pos) {
      return !this.isSafeBlock(pos.north())
         || !this.isSafeBlock(pos.south())
         || !this.isSafeBlock(pos.east())
         || !this.isSafeBlock(pos.west())
         || !this.isSafeBlock(pos.north().west())
         || !this.isSafeBlock(pos.north().east())
         || !this.isSafeBlock(pos.south().west())
         || !this.isSafeBlock(pos.south().east());
   }

   private boolean isColumnAir(BlockPos landingSpot, int minHeight) {
      MutableBlockPos mut = new MutableBlockPos(landingSpot.getX(), landingSpot.getY(), landingSpot.getZ());
      int maxY = mut.getY() + minHeight;

      for (int y = mut.getY() + 1; y <= maxY; y++) {
         mut.set(mut.getX(), y, mut.getZ());
         if (!(this.ctx.world().getBlockState(mut).getBlock() instanceof AirBlock)) {
            return false;
         }
      }

      return true;
   }

   private boolean hasAirBubble(BlockPos pos) {
      int radius = 4;
      MutableBlockPos mut = new MutableBlockPos();

      for (int x = -4; x <= 4; x++) {
         for (int y = -4; y <= 4; y++) {
            for (int z = -4; z <= 4; z++) {
               mut.set(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
               if (!(this.ctx.world().getBlockState(mut).getBlock() instanceof AirBlock)) {
                  return false;
               }
            }
         }
      }

      return true;
   }

   private BetterBlockPos checkLandingSpot(BlockPos pos, LongOpenHashSet checkedSpots) {
      MutableBlockPos mut = new MutableBlockPos(pos.getX(), pos.getY(), pos.getZ());

      while (mut.getY() >= this.ctx.world().dimensionType().minY()) {
         if (checkedSpots.contains(mut.asLong())) {
            return null;
         }

         checkedSpots.add(mut.asLong());
         Block block = this.ctx.world().getBlockState(mut).getBlock();
         if (this.isSafeBlock(block)) {
            if (!this.isAtEdge(mut)) {
               return new BetterBlockPos(mut);
            }

            return null;
         }

         if (block != Blocks.AIR) {
            return null;
         }

         mut.set(mut.getX(), mut.getY() - 1, mut.getZ());
      }

      return null;
   }

   private BetterBlockPos findSafeLandingSpot(BetterBlockPos start) {
      boolean useHeightmap = this.ctx.player().getY() > (double)this.ctx.world().getHeight(Types.MOTION_BLOCKING, start.getX(), start.getZ());
      if (this.landingSearchState != null && this.landingSearchState.isCompatible(start, useHeightmap)) {
         this.landingSearchState.updateStartPosition(start);
      } else {
         this.landingSearchState = new ElytraProcess.LandingSearchState(start, this.behavior.destination, useHeightmap);
      }

      BetterBlockPos landingSpot = this.landingSearchState.advance();
      if (landingSpot != null || this.landingSearchState.exhausted) {
         this.landingSearchState = null;
      }

      return landingSpot;
   }

   private boolean isChunkLoaded(BetterBlockPos pos) {
      return this.ctx.world().getChunkSource().hasChunk(pos.x >> 4, pos.z >> 4);
   }

   private NetherPathfinderContext getNpfContext() {
      if (this.npfContext == null) {
         this.npfSema.acquireUninterruptibly();
         this.npfContext = new NetherPathfinderContext(
            Baritone.settings().elytraNetherSeed.value,
            Baritone.settings().elytraUseCache.value ? this.baritone.getWorldProvider().getCurrentWorld().directory.resolve("cache") : null,
            this.ctx.world()
         );
      }

      return this.npfContext;
   }

   private void destroyNpfContextAsync() {
      NetherPathfinderContext npf = this.npfContext;
      if (npf != null) {
         this.npfContext = null;
         Baritone.getExecutor().execute(() -> {
            npf.destroy();
            this.npfSema.release();
         });
      }
   }

   private final class LandingSearchState {
      private final BetterBlockPos origin;
      private final boolean useHeightmap;
      private final Queue<BetterBlockPos> queue;
      private final Set<BetterBlockPos> visited = new HashSet<>();
      private final LongOpenHashSet checkedPositions = new LongOpenHashSet();
      private boolean exhausted;

      private LandingSearchState(BetterBlockPos origin, BetterBlockPos dest, boolean useHeightmap) {
         this.origin = origin;
         this.useHeightmap = useHeightmap;
         BetterBlockPos target = ElytraProcess.this.isChunkLoaded(dest) ? dest : origin;
         this.queue = new PriorityQueue<>(
            Comparator.<BetterBlockPos>comparingInt(pos -> (pos.x - target.x) * (pos.x - target.x) + (pos.z - target.z) * (pos.z - target.z))
               .thenComparingInt(pos -> -pos.y)
         );
         this.queue.add(target);
      }

      private boolean isCompatible(BetterBlockPos start, boolean useHeightmap) {
         return this.useHeightmap == useHeightmap && this.origin.distanceSq(start) <= 256.0;
      }

      private void updateStartPosition(BetterBlockPos start) {
         if (this.visited.add(start)) {
            this.queue.add(start);
         }
      }

      private BetterBlockPos advance() {
         long deadline = System.nanoTime() + ElytraProcess.LANDING_SEARCH_BUDGET_NANOS;

         while (!this.queue.isEmpty()) {
            if (System.nanoTime() >= deadline) {
               return null;
            }

            BetterBlockPos qPos = this.queue.poll();
            if (ElytraProcess.this.isChunkLoaded(qPos)) {
               BetterBlockPos landing = this.useHeightmap ? this.advanceHeightmap(qPos) : this.advanceUnderground(qPos);
               if (landing != null) {
                  return landing;
               }
            }
         }

         this.exhausted = true;
         return null;
      }

      private BetterBlockPos advanceUnderground(BetterBlockPos pos) {
         if (ElytraProcess.isInBounds(ElytraProcess.this.ctx.world(), pos) && ElytraProcess.this.ctx.world().getBlockState(pos).getBlock() == Blocks.AIR) {
            BetterBlockPos actualLandingSpot = ElytraProcess.this.checkLandingSpot(pos, this.checkedPositions);
            if (actualLandingSpot != null) {
               ElytraProcess.this.landingColumnHeight = 15;
               if (ElytraProcess.this.isColumnAir(actualLandingSpot, ElytraProcess.this.landingColumnHeight)
                  && ElytraProcess.this.hasAirBubble(actualLandingSpot.above(ElytraProcess.this.landingColumnHeight))
                  && !ElytraProcess.this.badLandingSpots.contains(actualLandingSpot.above(ElytraProcess.this.landingColumnHeight))) {
                  return actualLandingSpot.above(ElytraProcess.this.landingColumnHeight);
               }
            }

            if (this.visited.add(pos.north())) {
               this.queue.add(pos.north());
            }

            if (this.visited.add(pos.east())) {
               this.queue.add(pos.east());
            }

            if (this.visited.add(pos.south())) {
               this.queue.add(pos.south());
            }

            if (this.visited.add(pos.west())) {
               this.queue.add(pos.west());
            }

            if (this.visited.add(pos.above())) {
               this.queue.add(pos.above());
            }

            if (this.visited.add(pos.below())) {
               this.queue.add(pos.below());
            }
         }

         return null;
      }

      private BetterBlockPos advanceHeightmap(BetterBlockPos qPos) {
         int height = ElytraProcess.this.ctx.world().getHeight(Types.MOTION_BLOCKING, qPos.getX(), qPos.getZ());
         BetterBlockPos pos = new BetterBlockPos(qPos.getX(), height + 1, qPos.getZ());
         if (ElytraProcess.isInBounds(ElytraProcess.this.ctx.world(), pos) && ElytraProcess.this.ctx.world().getBlockState(pos).getBlock() == Blocks.AIR) {
            BetterBlockPos actualLandingSpot = ElytraProcess.this.checkLandingSpot(pos, this.checkedPositions);
            if (actualLandingSpot != null) {
               ElytraProcess.this.landingColumnHeight = ElytraProcess.this.ctx.playerFeet().y - actualLandingSpot.y < 39 ? 15 : 39;
               if (ElytraProcess.this.hasAirBubble(actualLandingSpot.above(ElytraProcess.this.landingColumnHeight))
                  && !ElytraProcess.this.badLandingSpots.contains(actualLandingSpot.above(ElytraProcess.this.landingColumnHeight))) {
                  return actualLandingSpot.above(ElytraProcess.this.landingColumnHeight);
               }
            }

            if (this.visited.add(pos.north())) {
               this.queue.add(pos.north());
            }

            if (this.visited.add(pos.east())) {
               this.queue.add(pos.east());
            }

            if (this.visited.add(pos.south())) {
               this.queue.add(pos.south());
            }

            if (this.visited.add(pos.west())) {
               this.queue.add(pos.west());
            }
         }

         return null;
      }
   }

   public static enum State {
      LOCATE_JUMP("Finding spot to jump off"),
      PAUSE("Waiting for elytra path"),
      GET_TO_JUMP("Walking to takeoff"),
      START_FLYING("Begin flying"),
      FLYING("Flying"),
      LANDING("Landing");

      public final String description;

      private State(String desc) {
         this.description = desc;
      }
   }

   public static final class WalkOffCalculationContext extends CalculationContext {
      public WalkOffCalculationContext(IBaritone baritone) {
         super(baritone, true);
         this.allowFallIntoLava = true;
         this.minFallHeight = 8;
         this.maxFallHeightNoWater = 10000;
      }

      @Override
      public double costOfPlacingAt(int x, int y, int z, BlockState current) {
         return 1000000.0;
      }

      @Override
      public double breakCostMultiplierAt(int x, int y, int z, BlockState current) {
         return 1000000.0;
      }

      @Override
      public double placeBucketCost() {
         return 1000000.0;
      }
   }
}
