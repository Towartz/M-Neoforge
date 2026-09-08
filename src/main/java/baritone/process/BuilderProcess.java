package baritone.process;

import baritone.Baritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.process.IBuilderProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.schematic.FillSchematic;
import baritone.api.schematic.ISchematic;
import baritone.api.schematic.IStaticSchematic;
import baritone.api.schematic.MaskSchematic;
import baritone.api.schematic.MirroredSchematic;
import baritone.api.schematic.RotatedSchematic;
import baritone.api.schematic.SubstituteSchematic;
import baritone.api.schematic.format.ISchematicFormat;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.RayTraceUtils;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.SettingsUtil;
import baritone.api.utils.input.Input;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.Movement;
import baritone.pathing.movement.MovementHelper;
import baritone.utils.BaritoneProcessHelper;
import baritone.utils.BlockStateInterface;
import baritone.utils.PathingCommandContext;
import baritone.utils.schematic.MapArtSchematic;
import baritone.utils.schematic.SchematicSystem;
import baritone.utils.schematic.SelectionSchematic;
import baritone.utils.schematic.litematica.LitematicaHelper;
import baritone.utils.schematic.schematica.SchematicaHelper;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Tuple;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class BuilderProcess extends BaritoneProcessHelper implements IBuilderProcess {
   private static final Set<Property<?>> ORIENTATION_PROPS = ImmutableSet.of(
      RotatedPillarBlock.AXIS,
      HorizontalDirectionalBlock.FACING,
      StairBlock.FACING,
      StairBlock.HALF,
      StairBlock.SHAPE,
      PipeBlock.NORTH,
      new Property[]{PipeBlock.EAST, PipeBlock.SOUTH, PipeBlock.WEST, PipeBlock.UP, TrapDoorBlock.OPEN, TrapDoorBlock.HALF}
   );
   private HashSet<BetterBlockPos> incorrectPositions;
   private LongOpenHashSet observedCompleted;
   private String name;
   private ISchematic realSchematic;
   private ISchematic schematic;
   private Vec3i origin;
   private int ticks;
   private boolean paused;
   private int layer;
   private int numRepeats;
   private List<BlockState> approxPlaceable;
   public int stopAtHeight = 0;

   public BuilderProcess(Baritone baritone) {
      super(baritone);
   }

   @Override
   public void build(String name, ISchematic schematic, Vec3i origin) {
      this.name = name;
      this.schematic = schematic;
      this.realSchematic = null;
      boolean buildingSelectionSchematic = schematic instanceof SelectionSchematic;
      if (!Baritone.settings().buildSubstitutes.value.isEmpty()) {
         this.schematic = new SubstituteSchematic(this.schematic, Baritone.settings().buildSubstitutes.value);
      }

      if (Baritone.settings().buildSchematicMirror.value != Mirror.NONE) {
         this.schematic = new MirroredSchematic(this.schematic, Baritone.settings().buildSchematicMirror.value);
      }

      if (Baritone.settings().buildSchematicRotation.value != net.minecraft.world.level.block.Rotation.NONE) {
         this.schematic = new RotatedSchematic(this.schematic, Baritone.settings().buildSchematicRotation.value);
      }

      this.schematic = new MaskSchematic(this.schematic) {
         @Override
         public boolean partOfMask(int x, int y, int z, BlockState current) {
            return !Baritone.settings().buildSkipBlocks.value.contains(this.desiredState(x, y, z, current, Collections.emptyList()).getBlock());
         }
      };
      int x = origin.getX();
      int y = origin.getY();
      int z = origin.getZ();
      if (Baritone.settings().schematicOrientationX.value) {
         x += schematic.widthX();
      }

      if (Baritone.settings().schematicOrientationY.value) {
         y += schematic.heightY();
      }

      if (Baritone.settings().schematicOrientationZ.value) {
         z += schematic.lengthZ();
      }

      this.origin = new Vec3i(x, y, z);
      this.paused = false;
      this.layer = Baritone.settings().startAtLayer.value;
      this.stopAtHeight = schematic.heightY();
      if (Baritone.settings().buildOnlySelection.value && buildingSelectionSchematic) {
         if (this.baritone.getSelectionManager().getSelections().length == 0) {
            this.logDirect("Poor little kitten forgot to set a selection while BuildOnlySelection is true");
            this.stopAtHeight = 0;
         } else if (Baritone.settings().buildInLayers.value) {
            OptionalInt minim = Stream.of(this.baritone.getSelectionManager().getSelections()).mapToInt(sel -> sel.min().y).min();
            OptionalInt maxim = Stream.of(this.baritone.getSelectionManager().getSelections()).mapToInt(sel -> sel.max().y).max();
            if (minim.isPresent() && maxim.isPresent()) {
               int startAtHeight = Baritone.settings().layerOrder.value ? y + schematic.heightY() - maxim.getAsInt() : minim.getAsInt() - y;
               this.stopAtHeight = (Baritone.settings().layerOrder.value ? y + schematic.heightY() - minim.getAsInt() : maxim.getAsInt() - y) + 1;
               this.layer = Math.max(this.layer, startAtHeight / Baritone.settings().layerHeight.value);
               this.logDebug(String.format("Schematic starts at y=%s with height %s", y, schematic.heightY()));
               this.logDebug(String.format("Selection starts at y=%s and ends at y=%s", minim.getAsInt(), maxim.getAsInt()));
               this.logDebug(String.format("Considering relevant height %s - %s", startAtHeight, this.stopAtHeight));
            }
         }
      }

      this.numRepeats = 0;
      this.observedCompleted = new LongOpenHashSet();
      this.incorrectPositions = null;
   }

   @Override
   public void resume() {
      this.paused = false;
   }

   @Override
   public void pause() {
      this.paused = true;
   }

   @Override
   public boolean isPaused() {
      return this.paused;
   }

   @Override
   public boolean build(String name, File schematic, Vec3i origin) {
      Optional<ISchematicFormat> format = SchematicSystem.INSTANCE.getByFile(schematic);
      if (!format.isPresent()) {
         return false;
      } else {
         IStaticSchematic parsed;
         try {
            parsed = format.get().parse(new FileInputStream(schematic));
         } catch (Exception var7) {
            var7.printStackTrace();
            return false;
         }

         ISchematic schem = this.applyMapArtAndSelection(origin, parsed);
         this.build(name, schem, origin);
         return true;
      }
   }

   private ISchematic applyMapArtAndSelection(Vec3i origin, IStaticSchematic parsed) {
      ISchematic schematic = parsed;
      if (Baritone.settings().mapArtMode.value) {
         schematic = new MapArtSchematic(parsed);
      }

      if (Baritone.settings().buildOnlySelection.value) {
         schematic = new SelectionSchematic(schematic, origin, this.baritone.getSelectionManager().getSelections());
      }

      return schematic;
   }

   @Override
   public void buildOpenSchematic() {
      if (SchematicaHelper.isSchematicaPresent()) {
         Optional<Tuple<IStaticSchematic, BlockPos>> schematic = SchematicaHelper.getOpenSchematic();
         if (schematic.isPresent()) {
            IStaticSchematic raw = (IStaticSchematic)schematic.get().getA();
            BlockPos origin = (BlockPos)schematic.get().getB();
            ISchematic schem = this.applyMapArtAndSelection(origin, raw);
            this.build(raw.toString(), schem, origin);
         } else {
            this.logDirect("No schematic currently open");
         }
      } else {
         this.logDirect("Schematica is not present");
      }
   }

   @Override
   public void buildOpenLitematic(int i) {
      if (LitematicaHelper.isLitematicaPresent()) {
         if (LitematicaHelper.hasLoadedSchematic(i)) {
            Tuple<IStaticSchematic, Vec3i> schematic = LitematicaHelper.getSchematic(i);
            Vec3i correctedOrigin = (Vec3i)schematic.getB();
            ISchematic schematic2 = this.applyMapArtAndSelection(correctedOrigin, (IStaticSchematic)schematic.getA());
            this.build(((IStaticSchematic)schematic.getA()).toString(), schematic2, correctedOrigin);
         } else {
            this.logDirect(String.format("List of placements has no entry %s", i + 1));
         }
      } else {
         this.logDirect("Litematica is not present");
      }
   }

   @Override
   public void clearArea(BlockPos corner1, BlockPos corner2) {
      BlockPos origin = new BlockPos(
         Math.min(corner1.getX(), corner2.getX()), Math.min(corner1.getY(), corner2.getY()), Math.min(corner1.getZ(), corner2.getZ())
      );
      int widthX = Math.abs(corner1.getX() - corner2.getX()) + 1;
      int heightY = Math.abs(corner1.getY() - corner2.getY()) + 1;
      int lengthZ = Math.abs(corner1.getZ() - corner2.getZ()) + 1;
      this.build("clear area", new FillSchematic(widthX, heightY, lengthZ, Blocks.AIR.defaultBlockState()), origin);
   }

   @Override
   public List<BlockState> getApproxPlaceable() {
      return new ArrayList<>(this.approxPlaceable);
   }

   @Override
   public boolean isActive() {
      return this.schematic != null;
   }

   public BlockState placeAt(int x, int y, int z, BlockState current) {
      if (!this.isActive()) {
         return null;
      } else if (!this.schematic.inSchematic(x - this.origin.getX(), y - this.origin.getY(), z - this.origin.getZ(), current)) {
         return null;
      } else {
         BlockState state = this.schematic.desiredState(x - this.origin.getX(), y - this.origin.getY(), z - this.origin.getZ(), current, this.approxPlaceable);
         return state.getBlock() instanceof AirBlock ? null : state;
      }
   }

   private Optional<Tuple<BetterBlockPos, Rotation>> toBreakNearPlayer(BuilderProcess.BuilderCalculationContext bcc) {
      BetterBlockPos center = this.ctx.playerFeet();
      BetterBlockPos pathStart = this.baritone.getPathingBehavior().pathStart();

      for (int dx = -5; dx <= 5; dx++) {
         for (int dy = Baritone.settings().breakFromAbove.value ? -1 : 0; dy <= 5; dy++) {
            for (int dz = -5; dz <= 5; dz++) {
               int x = center.x + dx;
               int y = center.y + dy;
               int z = center.z + dz;
               if (dy != -1 || x != pathStart.x || z != pathStart.z) {
                  BlockState desired = bcc.getSchematic(x, y, z, bcc.bsi.get0(x, y, z));
                  if (desired != null) {
                     BlockState curr = bcc.bsi.get0(x, y, z);
                     if (!(curr.getBlock() instanceof AirBlock)
                        && curr.getBlock() != Blocks.WATER
                        && curr.getBlock() != Blocks.LAVA
                        && !valid(curr, desired, false)) {
                        BetterBlockPos pos = new BetterBlockPos(x, y, z);
                        Optional<Rotation> rot = RotationUtils.reachable(this.ctx, pos, this.ctx.playerController().getBlockReachDistance());
                        if (rot.isPresent()) {
                           return Optional.of(new Tuple(pos, rot.get()));
                        }
                     }
                  }
               }
            }
         }
      }

      return Optional.empty();
   }

   private Optional<BuilderProcess.Placement> searchForPlacables(BuilderProcess.BuilderCalculationContext bcc, List<BlockState> desirableOnHotbar) {
      BetterBlockPos center = this.ctx.playerFeet();

      for (int dx = -5; dx <= 5; dx++) {
         for (int dy = -5; dy <= 1; dy++) {
            for (int dz = -5; dz <= 5; dz++) {
               int x = center.x + dx;
               int y = center.y + dy;
               int z = center.z + dz;
               BlockState desired = bcc.getSchematic(x, y, z, bcc.bsi.get0(x, y, z));
               if (desired != null) {
                  BlockState curr = bcc.bsi.get0(x, y, z);
                  if (MovementHelper.isReplaceable(x, y, z, curr, bcc.bsi)
                     && !valid(curr, desired, false)
                     && (dy != 1 || !(bcc.bsi.get0(x, y + 1, z).getBlock() instanceof AirBlock))) {
                     desirableOnHotbar.add(desired);
                     Optional<BuilderProcess.Placement> opt = this.possibleToPlace(desired, x, y, z, bcc.bsi);
                     if (opt.isPresent()) {
                        return opt;
                     }
                  }
               }
            }
         }
      }

      return Optional.empty();
   }

   public boolean placementPlausible(BlockPos pos, BlockState state) {
      VoxelShape voxelshape = state.getCollisionShape(this.ctx.world(), pos);
      return voxelshape.isEmpty() || this.ctx.world().isUnobstructed(null, voxelshape.move((double)pos.getX(), (double)pos.getY(), (double)pos.getZ()));
   }

   private Optional<BuilderProcess.Placement> possibleToPlace(BlockState toPlace, int x, int y, int z, BlockStateInterface bsi) {
      for (Direction against : Direction.values()) {
         BetterBlockPos placeAgainstPos = new BetterBlockPos(x, y, z).relative(against);
         BlockState placeAgainstState = bsi.get0(placeAgainstPos);
         if (!MovementHelper.isReplaceable(placeAgainstPos.x, placeAgainstPos.y, placeAgainstPos.z, placeAgainstState, bsi)
            && toPlace.canSurvive(this.ctx.world(), new BetterBlockPos(x, y, z))
            && this.placementPlausible(new BetterBlockPos(x, y, z), toPlace)) {
            VoxelShape shape = placeAgainstState.getShape(this.ctx.world(), placeAgainstPos);
            if (!shape.isEmpty()) {
               AABB aabb = shape.bounds();

               for (Vec3 placementMultiplier : aabbSideMultipliers(against)) {
                  double placeX = (double)placeAgainstPos.x + aabb.minX * placementMultiplier.x + aabb.maxX * (1.0 - placementMultiplier.x);
                  double placeY = (double)placeAgainstPos.y + aabb.minY * placementMultiplier.y + aabb.maxY * (1.0 - placementMultiplier.y);
                  double placeZ = (double)placeAgainstPos.z + aabb.minZ * placementMultiplier.z + aabb.maxZ * (1.0 - placementMultiplier.z);
                  Rotation rot = RotationUtils.calcRotationFromVec3d(
                     RayTraceUtils.inferSneakingEyePosition(this.ctx.player()), new Vec3(placeX, placeY, placeZ), this.ctx.playerRotations()
                  );
                  Rotation actualRot = this.baritone.getLookBehavior().getAimProcessor().peekRotation(rot);
                  HitResult result = RayTraceUtils.rayTraceTowards(this.ctx.player(), actualRot, this.ctx.playerController().getBlockReachDistance(), true);
                  if (result != null
                     && result.getType() == Type.BLOCK
                     && ((BlockHitResult)result).getBlockPos().equals(placeAgainstPos)
                     && ((BlockHitResult)result).getDirection() == against.getOpposite()) {
                     OptionalInt hotbar = this.hasAnyItemThatWouldPlace(toPlace, result, actualRot);
                     if (hotbar.isPresent()) {
                        return Optional.of(new BuilderProcess.Placement(hotbar.getAsInt(), placeAgainstPos, against.getOpposite(), rot));
                     }
                  }
               }
            }
         }
      }

      return Optional.empty();
   }

   private OptionalInt hasAnyItemThatWouldPlace(BlockState desired, HitResult result, Rotation rot) {
      for (int i = 0; i < 9; i++) {
         ItemStack stack = (ItemStack)this.ctx.player().getInventory().items.get(i);
         if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
            float originalYaw = this.ctx.player().getYRot();
            float originalPitch = this.ctx.player().getXRot();
            this.ctx.player().setYRot(rot.getYaw());
            this.ctx.player().setXRot(rot.getPitch());
            BlockPlaceContext meme = new BlockPlaceContext(
               new UseOnContext(this.ctx.world(), this.ctx.player(), InteractionHand.MAIN_HAND, stack, (BlockHitResult)result) {
               }
            );
            BlockState wouldBePlaced = ((BlockItem)stack.getItem()).getBlock().getStateForPlacement(meme);
            this.ctx.player().setYRot(originalYaw);
            this.ctx.player().setXRot(originalPitch);
            if (wouldBePlaced != null && meme.canPlace() && valid(wouldBePlaced, desired, true)) {
               return OptionalInt.of(i);
            }
         }
      }

      return OptionalInt.empty();
   }

   private static Vec3[] aabbSideMultipliers(Direction side) {
      switch (side) {
         case UP:
            return new Vec3[]{new Vec3(0.5, 1.0, 0.5), new Vec3(0.1, 1.0, 0.5), new Vec3(0.9, 1.0, 0.5), new Vec3(0.5, 1.0, 0.1), new Vec3(0.5, 1.0, 0.9)};
         case DOWN:
            return new Vec3[]{new Vec3(0.5, 0.0, 0.5), new Vec3(0.1, 0.0, 0.5), new Vec3(0.9, 0.0, 0.5), new Vec3(0.5, 0.0, 0.1), new Vec3(0.5, 0.0, 0.9)};
         case NORTH:
         case SOUTH:
         case EAST:
         case WEST:
            double x = side.getStepX() == 0 ? 0.5 : (double)(1 + side.getStepX()) / 2.0;
            double z = side.getStepZ() == 0 ? 0.5 : (double)(1 + side.getStepZ()) / 2.0;
            return new Vec3[]{new Vec3(x, 0.25, z), new Vec3(x, 0.75, z)};
         default:
            throw new IllegalStateException("Unexpected side " + side);
      }
   }

   @Override
   public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
      return this.onTick(calcFailed, isSafeToCancel, 0);
   }

   private PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel, int recursions) {
      if (recursions > 100) {
         return new PathingCommand(null, PathingCommandType.SET_GOAL_AND_PATH);
      } else {
         this.approxPlaceable = this.approxPlaceable(36);
         if (this.baritone.getInputOverrideHandler().isInputForcedDown(Input.CLICK_LEFT)) {
            this.ticks = 5;
         } else {
            this.ticks--;
         }

         this.baritone.getInputOverrideHandler().clearAllKeys();
         if (this.paused) {
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
         } else {
            if (Baritone.settings().buildInLayers.value) {
               if (this.realSchematic == null) {
                  this.realSchematic = this.schematic;
               }

               final ISchematic realSchematic = this.realSchematic;
               final int minYInclusive;
               final int maxYInclusive;
               if (Baritone.settings().layerOrder.value) {
                  maxYInclusive = realSchematic.heightY() - 1;
                  minYInclusive = realSchematic.heightY() - this.layer * Baritone.settings().layerHeight.value;
               } else {
                  maxYInclusive = this.layer * Baritone.settings().layerHeight.value - 1;
                  minYInclusive = 0;
               }

               this.schematic = new ISchematic() {
                  @Override
                  public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
                     return realSchematic.desiredState(x, y, z, current, BuilderProcess.this.approxPlaceable);
                  }

                  @Override
                  public boolean inSchematic(int x, int y, int z, BlockState currentState) {
                     return ISchematic.super.inSchematic(x, y, z, currentState)
                        && y >= minYInclusive
                        && y <= maxYInclusive
                        && realSchematic.inSchematic(x, y, z, currentState);
                  }

                  @Override
                  public void reset() {
                     realSchematic.reset();
                  }

                  @Override
                  public int widthX() {
                     return realSchematic.widthX();
                  }

                  @Override
                  public int heightY() {
                     return realSchematic.heightY();
                  }

                  @Override
                  public int lengthZ() {
                     return realSchematic.lengthZ();
                  }
               };
            }

            BuilderProcess.BuilderCalculationContext bcc = new BuilderProcess.BuilderCalculationContext();
            if (!this.recalc(bcc)) {
               if (Baritone.settings().buildInLayers.value && this.layer * Baritone.settings().layerHeight.value < this.stopAtHeight) {
                  this.logDirect("Starting layer " + this.layer);
                  this.layer++;
                  return this.onTick(calcFailed, isSafeToCancel, recursions + 1);
               } else {
                  Vec3i repeat = Baritone.settings().buildRepeat.value;
                  int max = Baritone.settings().buildRepeatCount.value;
                  this.numRepeats++;
                  if (repeat.equals(new Vec3i(0, 0, 0)) || max != -1 && this.numRepeats >= max) {
                     this.logDirect("Done building");
                     if (Baritone.settings().notificationOnBuildFinished.value) {
                        this.logNotification("Done building", false);
                     }

                     this.onLostControl();
                     return null;
                  } else {
                     this.layer = 0;
                     this.origin = new BlockPos(this.origin).offset(repeat);
                     if (!Baritone.settings().buildRepeatSneaky.value) {
                        this.schematic.reset();
                     }

                     this.logDirect("Repeating build in vector " + repeat + ", new origin is " + this.origin);
                     return this.onTick(calcFailed, isSafeToCancel, recursions + 1);
                  }
               }
            } else {
               if (Baritone.settings().distanceTrim.value) {
                  this.trim();
               }

               Optional<Tuple<BetterBlockPos, Rotation>> toBreak = this.toBreakNearPlayer(bcc);
               if (toBreak.isPresent() && isSafeToCancel && this.ctx.player().onGround()) {
                  Rotation rot = (Rotation)toBreak.get().getB();
                  BetterBlockPos pos = (BetterBlockPos)toBreak.get().getA();
                  this.baritone.getLookBehavior().updateTarget(rot, true);
                  MovementHelper.switchToBestToolFor(this.ctx, bcc.get(pos));
                  if (this.ctx.player().isCrouching()) {
                     this.baritone.getInputOverrideHandler().setInputForceState(Input.SNEAK, true);
                  }

                  if (this.ctx.isLookingAt(pos) || this.ctx.playerRotations().isReallyCloseTo(rot)) {
                     this.baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                  }

                  return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
               } else {
                  List<BlockState> desirableOnHotbar = new ArrayList<>();
                  Optional<BuilderProcess.Placement> toPlace = this.searchForPlacables(bcc, desirableOnHotbar);
                  if (toPlace.isPresent() && isSafeToCancel && this.ctx.player().onGround() && this.ticks <= 0) {
                     Rotation rotx = toPlace.get().rot;
                     this.baritone.getLookBehavior().updateTarget(rotx, true);
                     this.ctx.player().getInventory().selected = toPlace.get().hotbarSelection;
                     this.baritone.getInputOverrideHandler().setInputForceState(Input.SNEAK, true);
                     if (this.ctx.isLookingAt(toPlace.get().placeAgainst)
                           && ((BlockHitResult)this.ctx.objectMouseOver()).getDirection().equals(toPlace.get().side)
                        || this.ctx.playerRotations().isReallyCloseTo(rotx)) {
                        this.baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
                     }

                     return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
                  } else {
                     if (Baritone.settings().allowInventory.value) {
                        ArrayList<Integer> usefulSlots = new ArrayList<>();
                        List<BlockState> noValidHotbarOption = new ArrayList<>();

                        label155:
                        for (BlockState desired : desirableOnHotbar) {
                           for (int i = 0; i < 9; i++) {
                              if (valid(this.approxPlaceable.get(i), desired, true)) {
                                 usefulSlots.add(i);
                                 continue label155;
                              }
                           }

                           noValidHotbarOption.add(desired);
                        }

                        label141:
                        for (int ix = 9; ix < 36; ix++) {
                           for (BlockState desired : noValidHotbarOption) {
                              if (valid(this.approxPlaceable.get(ix), desired, true)) {
                                 if (!this.baritone.getInventoryBehavior().attemptToPutOnHotbar(ix, usefulSlots::contains)) {
                                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                                 }
                                 break label141;
                              }
                           }
                        }
                     }

                     Goal goal = this.assemble(bcc, this.approxPlaceable.subList(0, 9));
                     if (goal == null) {
                        goal = this.assemble(bcc, this.approxPlaceable, true);
                        if (goal == null) {
                           if (Baritone.settings().skipFailedLayers.value
                              && Baritone.settings().buildInLayers.value
                              && this.layer * Baritone.settings().layerHeight.value < this.realSchematic.heightY()) {
                              this.logDirect("Skipping layer that I cannot construct! Layer #" + this.layer);
                              this.layer++;
                              return this.onTick(calcFailed, isSafeToCancel, recursions + 1);
                           }

                           this.logDirect("Unable to do it. Pausing. resume to resume, cancel to cancel");
                           this.paused = true;
                           return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                        }
                     }

                     return new PathingCommandContext(goal, PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH, bcc);
                  }
               }
            }
         }
      }
   }

   private boolean recalc(BuilderProcess.BuilderCalculationContext bcc) {
      if (this.incorrectPositions == null) {
         this.incorrectPositions = new HashSet<>();
         this.fullRecalc(bcc);
         if (this.incorrectPositions.isEmpty()) {
            return false;
         }
      }

      this.recalcNearby(bcc);
      if (this.incorrectPositions.isEmpty()) {
         this.fullRecalc(bcc);
      }

      return !this.incorrectPositions.isEmpty();
   }

   private void trim() {
      HashSet<BetterBlockPos> copy = new HashSet<>(this.incorrectPositions);
      copy.removeIf(pos -> pos.distSqr(this.ctx.player().blockPosition()) > 200.0);
      if (!copy.isEmpty()) {
         this.incorrectPositions = copy;
      }
   }

   private void recalcNearby(BuilderProcess.BuilderCalculationContext bcc) {
      BetterBlockPos center = this.ctx.playerFeet();
      int radius = Baritone.settings().builderTickScanRadius.value;

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
               int x = center.x + dx;
               int y = center.y + dy;
               int z = center.z + dz;
               BlockState desired = bcc.getSchematic(x, y, z, bcc.bsi.get0(x, y, z));
               if (desired != null) {
                  BetterBlockPos pos = new BetterBlockPos(x, y, z);
                  if (valid(bcc.bsi.get0(x, y, z), desired, false)) {
                     this.incorrectPositions.remove(pos);
                     this.observedCompleted.add(BetterBlockPos.longHash(pos));
                  } else {
                     this.incorrectPositions.add(pos);
                     this.observedCompleted.remove(BetterBlockPos.longHash(pos));
                  }
               }
            }
         }
      }
   }

   private void fullRecalc(BuilderProcess.BuilderCalculationContext bcc) {
      this.incorrectPositions = new HashSet<>();

      for (int y = 0; y < this.schematic.heightY(); y++) {
         for (int z = 0; z < this.schematic.lengthZ(); z++) {
            for (int x = 0; x < this.schematic.widthX(); x++) {
               int blockX = x + this.origin.getX();
               int blockY = y + this.origin.getY();
               int blockZ = z + this.origin.getZ();
               BlockState current = bcc.bsi.get0(blockX, blockY, blockZ);
               if (this.schematic.inSchematic(x, y, z, current)) {
                  if (bcc.bsi.worldContainsLoadedChunk(blockX, blockZ)) {
                     if (valid(bcc.bsi.get0(blockX, blockY, blockZ), this.schematic.desiredState(x, y, z, current, this.approxPlaceable), false)) {
                        this.observedCompleted.add(BetterBlockPos.longHash(blockX, blockY, blockZ));
                     } else {
                        this.incorrectPositions.add(new BetterBlockPos(blockX, blockY, blockZ));
                        this.observedCompleted.remove(BetterBlockPos.longHash(blockX, blockY, blockZ));
                        if (this.incorrectPositions.size() > Baritone.settings().incorrectSize.value) {
                           return;
                        }
                     }
                  } else if (!this.observedCompleted.contains(BetterBlockPos.longHash(blockX, blockY, blockZ))) {
                     this.incorrectPositions.add(new BetterBlockPos(blockX, blockY, blockZ));
                     if (this.incorrectPositions.size() > Baritone.settings().incorrectSize.value) {
                        return;
                     }
                  }
               }
            }
         }
      }
   }

   private Goal assemble(BuilderProcess.BuilderCalculationContext bcc, List<BlockState> approxPlaceable) {
      return this.assemble(bcc, approxPlaceable, false);
   }

   private Goal assemble(BuilderProcess.BuilderCalculationContext bcc, List<BlockState> approxPlaceable, boolean logMissing) {
      List<BetterBlockPos> placeable = new ArrayList<>();
      List<BetterBlockPos> breakable = new ArrayList<>();
      List<BetterBlockPos> sourceLiquids = new ArrayList<>();
      List<BetterBlockPos> flowingLiquids = new ArrayList<>();
      Map<BlockState, Integer> missing = new HashMap<>();
      List<BetterBlockPos> outOfBounds = new ArrayList<>();
      this.incorrectPositions.forEach(pos -> {
         BlockState state = bcc.bsi.get0(pos);
         if (state.getBlock() instanceof AirBlock) {
            BlockState desired = bcc.getSchematic(pos.x, pos.y, pos.z, state);
            if (desired == null) {
               outOfBounds.add(pos);
            } else if (containsBlockState(approxPlaceable, desired)) {
               placeable.add(pos);
            } else {
               missing.put(desired, 1 + missing.getOrDefault(desired, 0));
            }
         } else if (state.getBlock() instanceof LiquidBlock) {
            if (!MovementHelper.possiblyFlowing(state)) {
               sourceLiquids.add(pos);
            } else {
               flowingLiquids.add(pos);
            }
         } else {
            breakable.add(pos);
         }
      });
      this.incorrectPositions.removeAll(outOfBounds);
      List<Goal> toBreak = new ArrayList<>();
      breakable.forEach(pos -> toBreak.add(this.breakGoal(pos, bcc)));
      List<Goal> toPlace = new ArrayList<>();
      placeable.forEach(pos -> {
         if (!placeable.contains(pos.below()) && !placeable.contains(pos.below(2))) {
            toPlace.add(this.placementGoal(pos, bcc));
         }
      });
      sourceLiquids.forEach(pos -> toPlace.add(new GoalBlock(pos.above())));
      if (!toPlace.isEmpty()) {
         return new BuilderProcess.JankyGoalComposite(new GoalComposite(toPlace.toArray(new Goal[0])), new GoalComposite(toBreak.toArray(new Goal[0])));
      } else if (toBreak.isEmpty()) {
         if (logMissing && !missing.isEmpty()) {
            this.logDirect("Missing materials for at least:");
            this.logDirect(missing.entrySet().stream().map(e -> String.format("%sx %s", e.getValue(), e.getKey())).collect(Collectors.joining("\n")));
         }

         if (logMissing && !flowingLiquids.isEmpty()) {
            this.logDirect("Unreplaceable liquids at at least:");
            this.logDirect(flowingLiquids.stream().map(p -> String.format("%s %s %s", p.x, p.y, p.z)).collect(Collectors.joining("\n")));
         }

         return null;
      } else {
         return new GoalComposite(toBreak.toArray(new Goal[0]));
      }
   }

   private Goal placementGoal(BlockPos pos, BuilderProcess.BuilderCalculationContext bcc) {
      if (!(this.ctx.world().getBlockState(pos).getBlock() instanceof AirBlock)) {
         return new BuilderProcess.GoalPlace(pos);
      } else {
         boolean allowSameLevel = !(this.ctx.world().getBlockState(pos.above()).getBlock() instanceof AirBlock);
         BlockState current = this.ctx.world().getBlockState(pos);

         for (Direction facing : Movement.HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP) {
            if (MovementHelper.canPlaceAgainst(this.ctx, pos.relative(facing))
               && this.placementPlausible(pos, bcc.getSchematic(pos.getX(), pos.getY(), pos.getZ(), current))) {
               return new BuilderProcess.GoalAdjacent(pos, pos.relative(facing), allowSameLevel);
            }
         }

         return new BuilderProcess.GoalPlace(pos);
      }
   }

   private Goal breakGoal(BlockPos pos, BuilderProcess.BuilderCalculationContext bcc) {
      return (Goal)(Baritone.settings().goalBreakFromAbove.value
            && bcc.bsi.get0(pos.above()).getBlock() instanceof AirBlock
            && bcc.bsi.get0(pos.above(2)).getBlock() instanceof AirBlock
         ? new BuilderProcess.JankyGoalComposite(new BuilderProcess.GoalBreak(pos), new GoalGetToBlock(pos.above()) {
            @Override
            public boolean isInGoal(int x, int y, int z) {
               return y <= this.y && (x != this.x || y != this.y || z != this.z) ? super.isInGoal(x, y, z) : false;
            }
         })
         : new BuilderProcess.GoalBreak(pos));
   }

   @Override
   public void onLostControl() {
      this.incorrectPositions = null;
      this.name = null;
      this.schematic = null;
      this.realSchematic = null;
      this.layer = Baritone.settings().startAtLayer.value;
      this.numRepeats = 0;
      this.paused = false;
      this.observedCompleted = null;
   }

   @Override
   public String displayName0() {
      return this.paused ? "Builder Paused" : "Building " + this.name;
   }

   @Override
   public Optional<Integer> getMinLayer() {
      return Baritone.settings().buildInLayers.value ? Optional.of(this.layer) : Optional.empty();
   }

   @Override
   public Optional<Integer> getMaxLayer() {
      return Baritone.settings().buildInLayers.value ? Optional.of(this.stopAtHeight) : Optional.empty();
   }

   private List<BlockState> approxPlaceable(int size) {
      List<BlockState> result = new ArrayList<>();

      for (int i = 0; i < size; i++) {
         ItemStack stack = (ItemStack)this.ctx.player().getInventory().items.get(i);
         if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
            BlockState itemState = ((BlockItem)stack.getItem())
               .getBlock()
               .getStateForPlacement(
                  new BlockPlaceContext(
                     new UseOnContext(
                        this.ctx.world(),
                        this.ctx.player(),
                        InteractionHand.MAIN_HAND,
                        stack,
                        new BlockHitResult(
                           new Vec3(this.ctx.player().position().x, this.ctx.player().position().y, this.ctx.player().position().z),
                           Direction.UP,
                           this.ctx.playerFeet(),
                           false
                        )
                     ) {
                     }
                  )
               );
            if (itemState != null) {
               result.add(itemState);
            } else {
               result.add(Blocks.AIR.defaultBlockState());
            }
         } else {
            result.add(Blocks.AIR.defaultBlockState());
         }
      }

      return result;
   }

   private static boolean sameBlockstate(BlockState first, BlockState second) {
      if (first.getBlock() != second.getBlock()) {
         return false;
      } else {
         boolean ignoreDirection = Baritone.settings().buildIgnoreDirection.value;
         List<String> ignoredProps = Baritone.settings().buildIgnoreProperties.value;
         if (!ignoreDirection && ignoredProps.isEmpty()) {
            return first.equals(second);
         } else {
            Map<Property<?>, Comparable<?>> map1 = first.getValues();
            Map<Property<?>, Comparable<?>> map2 = second.getValues();

            for (Property<?> prop : map1.keySet()) {
               if (map1.get(prop) != map2.get(prop) && (!ignoreDirection || !ORIENTATION_PROPS.contains(prop)) && !ignoredProps.contains(prop.getName())) {
                  return false;
               }
            }

            return true;
         }
      }
   }

   private static boolean containsBlockState(Collection<BlockState> states, BlockState state) {
      for (BlockState testee : states) {
         if (sameBlockstate(testee, state)) {
            return true;
         }
      }

      return false;
   }

   private static boolean valid(BlockState current, BlockState desired, boolean itemVerify) {
      if (desired == null) {
         return true;
      } else if (current.getBlock() instanceof LiquidBlock && Baritone.settings().okIfWater.value) {
         return true;
      } else if (current.getBlock() instanceof AirBlock && desired.getBlock() instanceof AirBlock) {
         return true;
      } else if (current.getBlock() instanceof AirBlock && Baritone.settings().okIfAir.value.contains(desired.getBlock())) {
         return true;
      } else if (desired.getBlock() instanceof AirBlock && Baritone.settings().buildIgnoreBlocks.value.contains(current.getBlock())) {
         return true;
      } else if (!(current.getBlock() instanceof AirBlock) && Baritone.settings().buildIgnoreExisting.value && !itemVerify) {
         return true;
      } else if (Baritone.settings().buildValidSubstitutes.value.getOrDefault(desired.getBlock(), Collections.emptyList()).contains(current.getBlock())
         && !itemVerify) {
         return true;
      } else {
         return current.equals(desired) ? true : sameBlockstate(current, desired);
      }
   }

   public class BuilderCalculationContext extends CalculationContext {
      private final List<BlockState> placeable = BuilderProcess.this.approxPlaceable(9);
      private final ISchematic schematic;
      private final int originX;
      private final int originY;
      private final int originZ;

      public BuilderCalculationContext() {
         super(BuilderProcess.this.baritone, true);
         this.schematic = BuilderProcess.this.schematic;
         this.originX = BuilderProcess.this.origin.getX();
         this.originY = BuilderProcess.this.origin.getY();
         this.originZ = BuilderProcess.this.origin.getZ();
         this.jumpPenalty += 10.0;
         this.backtrackCostFavoringCoefficient = 1.0;
      }

      private BlockState getSchematic(int x, int y, int z, BlockState current) {
         return this.schematic.inSchematic(x - this.originX, y - this.originY, z - this.originZ, current)
            ? this.schematic.desiredState(x - this.originX, y - this.originY, z - this.originZ, current, BuilderProcess.this.approxPlaceable)
            : null;
      }

      @Override
      public double costOfPlacingAt(int x, int y, int z, BlockState current) {
         if (!this.isPossiblyProtected(x, y, z) && this.worldBorder.canPlaceAt(x, z)) {
            BlockState sch = this.getSchematic(x, y, z, current);
            if (sch != null) {
               if (sch.getBlock() instanceof AirBlock) {
                  return this.placeBlockCost * Baritone.settings().placeIncorrectBlockPenaltyMultiplier.value;
               } else if (this.placeable.contains(sch)) {
                  return 0.0;
               } else {
                  return !this.hasThrowaway ? 1000000.0 : this.placeBlockCost * 1.5 * Baritone.settings().placeIncorrectBlockPenaltyMultiplier.value;
               }
            } else {
               return this.hasThrowaway ? this.placeBlockCost : 1000000.0;
            }
         } else {
            return 1000000.0;
         }
      }

      @Override
      public double breakCostMultiplierAt(int x, int y, int z, BlockState current) {
         if ((this.allowBreak || this.allowBreakAnyway.contains(current.getBlock())) && !this.isPossiblyProtected(x, y, z)) {
            BlockState sch = this.getSchematic(x, y, z, current);
            if (sch == null) {
               return 1.0;
            } else if (sch.getBlock() instanceof AirBlock) {
               return 1.0;
            } else {
               return BuilderProcess.valid(this.bsi.get0(x, y, z), sch, false) ? Baritone.settings().breakCorrectBlockPenaltyMultiplier.value : 1.0;
            }
         } else {
            return 1000000.0;
         }
      }
   }

   public static class GoalAdjacent extends GoalGetToBlock {
      private boolean allowSameLevel;
      private BlockPos no;

      public GoalAdjacent(BlockPos pos, BlockPos no, boolean allowSameLevel) {
         super(pos);
         this.no = no;
         this.allowSameLevel = allowSameLevel;
      }

      @Override
      public boolean isInGoal(int x, int y, int z) {
         if (x == this.x && y == this.y && z == this.z) {
            return false;
         } else if (x == this.no.getX() && y == this.no.getY() && z == this.no.getZ()) {
            return false;
         } else if (!this.allowSameLevel && y == this.y - 1) {
            return false;
         } else {
            return y < this.y - 1 ? false : super.isInGoal(x, y, z);
         }
      }

      @Override
      public double heuristic(int x, int y, int z) {
         return (double)(this.y * 100) + super.heuristic(x, y, z);
      }

      @Override
      public boolean equals(Object o) {
         if (!super.equals(o)) {
            return false;
         } else {
            BuilderProcess.GoalAdjacent goal = (BuilderProcess.GoalAdjacent)o;
            return this.allowSameLevel == goal.allowSameLevel && Objects.equals(this.no, goal.no);
         }
      }

      @Override
      public int hashCode() {
         int hash = 806368046;
         hash = hash * 1412661222 + super.hashCode();
         hash = hash * 1730799370 + (int)BetterBlockPos.longHash(this.no.getX(), this.no.getY(), this.no.getZ());
         return hash * 260592149 + (this.allowSameLevel ? -1314802005 : 1565710265);
      }

      @Override
      public String toString() {
         return String.format(
            "GoalAdjacent{x=%s,y=%s,z=%s}", SettingsUtil.maybeCensor(this.x), SettingsUtil.maybeCensor(this.y), SettingsUtil.maybeCensor(this.z)
         );
      }
   }

   public static class GoalBreak extends GoalGetToBlock {
      public GoalBreak(BlockPos pos) {
         super(pos);
      }

      @Override
      public boolean isInGoal(int x, int y, int z) {
         return y > this.y ? false : super.isInGoal(x, y, z);
      }

      @Override
      public String toString() {
         return String.format("GoalBreak{x=%s,y=%s,z=%s}", SettingsUtil.maybeCensor(this.x), SettingsUtil.maybeCensor(this.y), SettingsUtil.maybeCensor(this.z));
      }

      @Override
      public int hashCode() {
         return super.hashCode() * 1636324008;
      }
   }

   public static class GoalPlace extends GoalBlock {
      public GoalPlace(BlockPos placeAt) {
         super(placeAt.above());
      }

      @Override
      public double heuristic(int x, int y, int z) {
         return (double)(this.y * 100) + super.heuristic(x, y, z);
      }

      @Override
      public int hashCode() {
         return super.hashCode() * 1910811835;
      }

      @Override
      public String toString() {
         return String.format("GoalPlace{x=%s,y=%s,z=%s}", SettingsUtil.maybeCensor(this.x), SettingsUtil.maybeCensor(this.y), SettingsUtil.maybeCensor(this.z));
      }
   }

   public static class JankyGoalComposite implements Goal {
      private final Goal primary;
      private final Goal fallback;

      public JankyGoalComposite(Goal primary, Goal fallback) {
         this.primary = primary;
         this.fallback = fallback;
      }

      @Override
      public boolean isInGoal(int x, int y, int z) {
         return this.primary.isInGoal(x, y, z) || this.fallback.isInGoal(x, y, z);
      }

      @Override
      public double heuristic(int x, int y, int z) {
         return this.primary.heuristic(x, y, z);
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else if (o != null && this.getClass() == o.getClass()) {
            BuilderProcess.JankyGoalComposite goal = (BuilderProcess.JankyGoalComposite)o;
            return Objects.equals(this.primary, goal.primary) && Objects.equals(this.fallback, goal.fallback);
         } else {
            return false;
         }
      }

      @Override
      public int hashCode() {
         int hash = -1701079641;
         hash = hash * 1196141026 + this.primary.hashCode();
         return hash * -80327868 + this.fallback.hashCode();
      }

      @Override
      public String toString() {
         return "JankyComposite Primary: " + this.primary + " Fallback: " + this.fallback;
      }
   }

   public static class Placement {
      private final int hotbarSelection;
      private final BlockPos placeAgainst;
      private final Direction side;
      private final Rotation rot;

      public Placement(int hotbarSelection, BlockPos placeAgainst, Direction side, Rotation rot) {
         this.hotbarSelection = hotbarSelection;
         this.placeAgainst = placeAgainst;
         this.side = side;
         this.rot = rot;
      }
   }
}
