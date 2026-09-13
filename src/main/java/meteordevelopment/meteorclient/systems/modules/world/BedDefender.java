package meteordevelopment.meteorclient.systems.modules.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class BedDefender extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgBlocks = this.settings.createGroup("Blocks");
   private final SettingGroup sgRender = this.settings.createGroup("Render");

   private final Setting<Integer> maxLayers = this.sgGeneral
      .add(new IntSetting.Builder().name("max-layers").description("Layers of blocks to place around the bed.").defaultValue(1).min(1).max(3).sliderRange(1, 3).build());
   private final Setting<Boolean> preferSides = this.sgGeneral
      .add(new BoolSetting.Builder().name("prefer-sides").description("Place side blocks before top blocks.").defaultValue(true).build());
   private final Setting<Boolean> defendUnder = this.sgGeneral
      .add(new BoolSetting.Builder().name("defend-under").description("Also place blocks beneath the bed.").defaultValue(true).build());
   private final Setting<Boolean> topRiser = this.sgGeneral
      .add(new BoolSetting.Builder().name("top-riser").description("Place support risers to reach the top in 1-layer mode.").defaultValue(true).visible(() -> this.maxLayers.get() == 1).build());
   private final Setting<Boolean> sneakForBed = this.sgGeneral
      .add(new BoolSetting.Builder().name("sneak-for-bed").description("Automatically crouch to avoid opening the bed or chests.").defaultValue(true).build());
   private final Setting<Integer> delay = this.sgGeneral
      .add(new IntSetting.Builder().name("delay").description("Delay between block placements in ticks.").defaultValue(1).min(0).max(10).sliderRange(0, 5).build());
   private final Setting<Double> range = this.sgGeneral
      .add(new DoubleSetting.Builder().name("range").description("Maximum distance to look for beds.").defaultValue(5.0).min(2.0).max(6.0).sliderRange(2.0, 6.0).build());

   private final Setting<List<Block>> blocks = this.sgBlocks
      .add(new BlockListSetting.Builder().name("blocks").description("Defense materials to place.").defaultValue(Blocks.OBSIDIAN, Blocks.END_STONE, Blocks.ENDER_CHEST, Blocks.OAK_PLANKS).build());

   private final Setting<Boolean> render = this.sgRender
      .add(new BoolSetting.Builder().name("render").description("Renders planned defense blocks.").defaultValue(true).build());
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(new EnumSetting.Builder<ShapeMode>().name("shape-mode").description("How the shapes are rendered.").defaultValue(ShapeMode.Both).build());
   private final Setting<SettingColor> sideColor = this.sgRender
      .add(new ColorSetting.Builder().name("side-color").description("The side color.").defaultValue(new SettingColor(255, 128, 0, 45)).build());
   private final Setting<SettingColor> lineColor = this.sgRender
      .add(new ColorSetting.Builder().name("line-color").description("The line color.").defaultValue(new SettingColor(255, 128, 0, 200)).build());

   private final List<BlockPos> targets = new ArrayList<>();
   private BlockPos cachedBed = null;
   private int timer = 0;

   public BedDefender() {
      super(Categories.World, "bed-defender", "Surrounds nearby beds with defensive blocks.");
   }

   @Override
   public void onActivate() {
      this.targets.clear();
      this.cachedBed = null;
      this.timer = 0;
   }

   @Override
   public void onDeactivate() {
      this.targets.clear();
      this.cachedBed = null;
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player == null || this.mc.level == null) {
         return;
      }

      if (this.timer > 0) {
         this.timer--;
         return;
      }

      Vec3 eye = this.mc.player.getEyePosition();
      this.cachedBed = this.findNearestBed(eye, this.range.get());
      if (this.cachedBed == null) {
         this.targets.clear();
         return;
      }

      BlockState bedState = this.mc.level.getBlockState(this.cachedBed);
      if (!(bedState.getBlock() instanceof BedBlock)) {
         this.cachedBed = null;
         this.targets.clear();
         return;
      }

      Direction connected = BedBlock.getConnectedDirection(bedState);
      BlockPos partner = this.cachedBed.relative(connected);

      this.rebuildTargets(this.cachedBed, partner, connected, eye);
      if (this.targets.isEmpty()) {
         return;
      }

      FindItemResult itemResult = InvUtils.findInHotbar(itemStack -> {
         if (itemStack.getItem() instanceof BlockItem blockItem) {
            return this.blocks.get().contains(blockItem.getBlock());
         }
         return false;
      });

      if (!itemResult.found()) {
         return;
      }

      for (BlockPos target : this.targets) {
         if (!BlockUtils.canPlace(target, true)) {
            continue;
         }

         boolean sneaked = false;
         if (this.sneakForBed.get()) {
            Direction side = BlockUtils.getPlaceSide(target);
            if (side != null) {
               BlockPos support = target.relative(side);
               BlockState supportState = this.mc.level.getBlockState(support);
               if (supportState.getBlock() instanceof BedBlock || supportState.hasBlockEntity()) {
                  this.mc.getConnection().send(new ServerboundPlayerCommandPacket(this.mc.player, Action.PRESS_SHIFT_KEY));
                  sneaked = true;
               }
            }
         }

         boolean placed = BlockUtils.place(target, itemResult, true, 50, true, true, true);
         if (sneaked) {
            this.mc.getConnection().send(new ServerboundPlayerCommandPacket(this.mc.player, Action.RELEASE_SHIFT_KEY));
         }

         if (placed) {
            this.timer = this.delay.get();
            break;
         }
      }
   }

   private void rebuildTargets(BlockPos bed, BlockPos partner, Direction bedDir, Vec3 eye) {
      this.targets.clear();
      int layers = this.maxLayers.get();
      Direction outward = bedDir.getOpposite();
      Direction[] perps = bedDir.getAxis() == Axis.X
         ? new Direction[]{Direction.NORTH, Direction.SOUTH}
         : new Direction[]{Direction.WEST, Direction.EAST};

      Set<BlockPos> visited = new HashSet<>();
      List<ShellStep> steps = new ArrayList<>();

      this.collectShell(bed, layers, new Direction[]{outward, Direction.UP, perps[0], perps[1]}, visited, steps);
      this.collectShell(partner, layers, new Direction[]{bedDir, Direction.UP, perps[0], perps[1]}, visited, steps);

      if (this.defendUnder.get()) {
         BlockPos under1 = bed.below();
         BlockPos under2 = partner.below();
         if (visited.add(under1)) steps.add(new ShellStep(under1, 1, false, true));
         if (visited.add(under2)) steps.add(new ShellStep(under2, 1, false, true));
      }

      if (layers == 1 && this.topRiser.get()) {
         BlockPos top1 = bed.above();
         BlockPos top2 = partner.above();
         BlockPos riser1 = bed.relative(outward).above();
         BlockPos riser2 = partner.relative(bedDir).above();
         if (this.isAirOrReplaceable(top1) && visited.add(riser1)) {
            steps.add(new ShellStep(riser1, 2, false, false));
         }
         if (this.isAirOrReplaceable(top2) && visited.add(riser2)) {
            steps.add(new ShellStep(riser2, 2, false, false));
         }
      }

      double rangeSq = this.range.get() * this.range.get();
      int bedY = bed.getY();

      List<ShellStep> candidates = new ArrayList<>();
      for (ShellStep step : steps) {
         if (!this.isAirOrReplaceable(step.pos)) {
            continue;
         }
         double distSq = step.pos.distToCenterSqr(eye);
         if (distSq > rangeSq) {
            continue;
         }
         step.elevated = step.pos.getY() > bedY;
         step.distSq = distSq;
         candidates.add(step);
      }

      boolean sidesFirst = this.preferSides.get();
      candidates.sort((a, b) -> {
         if (a.layer != b.layer) {
            return Integer.compare(a.layer, b.layer);
         }
         if (a.under != b.under) {
            return a.under ? -1 : 1;
         }
         if (sidesFirst && a.elevated != b.elevated) {
            return a.elevated ? 1 : -1;
         }
         return Double.compare(a.distSq, b.distSq);
      });

      for (ShellStep step : candidates) {
         this.targets.add(step.pos);
      }
   }

   private void collectShell(BlockPos seed, int layers, Direction[] dirs, Set<BlockPos> visited, List<ShellStep> out) {
      ArrayDeque<ShellStep> queue = new ArrayDeque<>();
      queue.add(new ShellStep(seed, 0, false, false));
      visited.add(seed);

      while (!queue.isEmpty()) {
         ShellStep step = queue.poll();
         if (step.layer > 0) {
            out.add(step);
         }
         if (step.layer < layers) {
            for (Direction dir : dirs) {
               BlockPos next = step.pos.relative(dir);
               if (visited.add(next)) {
                  queue.add(new ShellStep(next, step.layer + 1, false, false));
               }
            }
         }
      }
   }

   private boolean isAirOrReplaceable(BlockPos pos) {
      if (this.mc.level == null) return false;
      BlockState state = this.mc.level.getBlockState(pos);
      return state.isAir() || state.canBeReplaced();
   }

   private BlockPos findNearestBed(Vec3 eye, double radius) {
      int minX = (int) Math.floor(eye.x - radius);
      int maxX = (int) Math.ceil(eye.x + radius);
      int minY = Math.max((int) Math.floor(eye.y - radius), this.mc.level.getMinBuildHeight());
      int maxY = Math.min((int) Math.ceil(eye.y + radius), this.mc.level.getMaxBuildHeight());
      int minZ = (int) Math.floor(eye.z - radius);
      int maxZ = (int) Math.ceil(eye.z + radius);

      BlockPos nearest = null;
      double nearestSq = radius * radius;

      for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
         if (this.mc.level.getBlockState(pos).getBlock() instanceof BedBlock) {
            double dSq = pos.distToCenterSqr(eye);
            if (dSq <= nearestSq) {
               nearest = pos.immutable();
               nearestSq = dSq;
            }
         }
      }

      return nearest;
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (!this.render.get() || this.targets.isEmpty()) {
         return;
      }

      for (BlockPos target : this.targets) {
         event.renderer.box(
            target.getX(),
            target.getY(),
            target.getZ(),
            target.getX() + 1,
            target.getY() + 1,
            target.getZ() + 1,
            this.sideColor.get(),
            this.lineColor.get(),
            this.shapeMode.get(),
            0
         );
      }
   }

   @Override
   public String getInfoString() {
      if (!this.targets.isEmpty()) {
         return Integer.toString(this.targets.size());
      }
      return this.cachedBed != null ? "Ready" : null;
   }

   private static class ShellStep {
      final BlockPos pos;
      final int layer;
      boolean elevated;
      final boolean under;
      double distSq;

      ShellStep(BlockPos pos, int layer, boolean elevated, boolean under) {
         this.pos = pos.immutable();
         this.layer = layer;
         this.elevated = elevated;
         this.under = under;
      }
   }
}
