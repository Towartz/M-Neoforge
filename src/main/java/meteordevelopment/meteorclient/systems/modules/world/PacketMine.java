package meteordevelopment.meteorclient.systems.modules.world;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.BreakIndicators;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Pool;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PacketMine extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");

   public final Setting<PacketMine.Mode> mode = this.sgGeneral
      .add(
         new EnumSetting.Builder<PacketMine.Mode>()
            .name("mode")
            .description("How the packet mine sends break packets.")
            .defaultValue(PacketMine.Mode.Normal)
            .build()
      );

   private final Setting<Integer> maxBlocks = this.sgGeneral
      .add(new IntSetting.Builder().name("max-blocks").description("Maximum number of concurrent blocks to mine.").defaultValue(Integer.valueOf(1)).min(1).max(2).sliderMax(2).build());

   private final Setting<Boolean> autoRebreak = this.sgGeneral
      .add(new BoolSetting.Builder().name("auto-rebreak").description("Automatically re-mines if a block is placed back at the broken position.").defaultValue(Boolean.valueOf(true)).build());

   private final Setting<Integer> delay = this.sgGeneral
      .add(new IntSetting.Builder().name("delay").description("Delay between mining blocks in ticks.").defaultValue(Integer.valueOf(1)).min(0).build());

   private final Setting<Boolean> rotate = this.sgGeneral
      .add(
         new BoolSetting.Builder().name("rotate").description("Sends rotation packets to the server when mining.").defaultValue(Boolean.valueOf(true)).build()
      );

   private final Setting<Boolean> autoSwitch = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("auto-switch")
            .description("Automatically switches to the best tool when the block is ready to be broken.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );

   private final Setting<Boolean> silentSwitch = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("silent-switch")
            .description("Switches to the tool only for the break packet, then immediately restores your previous weapon/item.")
            .defaultValue(Boolean.valueOf(true))
            .visible(this.autoSwitch::get)
            .build()
      );

   private final Setting<Boolean> pauseOnCombat = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("pause-on-combat")
            .description("Pauses tool switching when targeting an entity or in active combat.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );

   private final Setting<Boolean> notOnUse = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("not-on-use")
            .description("Won't auto switch if you're using an item (shield, food, bow).")
            .defaultValue(Boolean.valueOf(true))
            .visible(this.autoSwitch::get)
            .build()
      );

   private final Setting<Boolean> render = this.sgRender
      .add(new BoolSetting.Builder().name("render").description("Whether or not to render the block being mined.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode"))
                  .description("How the shapes are rendered."))
               .defaultValue(ShapeMode.Both))
            .build()
      );

   private final Setting<PacketMine.RenderMode> renderMode = this.sgRender
      .add(
         new EnumSetting.Builder<PacketMine.RenderMode>()
            .name("render-mode")
            .description("How the mining progress is animated.")
            .defaultValue(PacketMine.RenderMode.Grow)
            .build()
      );

   private final Setting<PacketMine.ColorMode> colorMode = this.sgRender
      .add(
         new EnumSetting.Builder<PacketMine.ColorMode>()
            .name("color-mode")
            .description("Whether to use flat colors or interpolate smoothly from start to ready.")
            .defaultValue(PacketMine.ColorMode.Gradient)
            .build()
      );

   private final Setting<SettingColor> readySideColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("ready-side-color")
            .description("The color of the sides of the blocks that can be broken.")
            .defaultValue(new SettingColor(0, 204, 0, 10))
            .build()
      );
   private final Setting<SettingColor> readyLineColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("ready-line-color")
            .description("The color of the lines of the blocks that can be broken.")
            .defaultValue(new SettingColor(0, 204, 0, 255))
            .build()
      );
   private final Setting<SettingColor> sideColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("side-color")
            .description("The color of the sides of the blocks being rendered.")
            .defaultValue(new SettingColor(204, 0, 0, 10))
            .build()
      );
   private final Setting<SettingColor> lineColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("line-color")
            .description("The color of the lines of the blocks being rendered.")
            .defaultValue(new SettingColor(204, 0, 0, 255))
            .build()
      );
   private final Pool<PacketMine.MyBlock> blockPool = new Pool<>(() -> new PacketMine.MyBlock());
   public final List<PacketMine.MyBlock> blocks = new ArrayList<>();
   private int combatTimer;
   private BlockPos lastBrokenPos;
   private Direction lastBrokenDirection;

   public PacketMine() {
      super(Categories.World, "packet-mine", "Sends packets to mine blocks without the mining animation.");
   }

   @Override
   public void onActivate() {
      this.combatTimer = 0;
      this.lastBrokenPos = null;
      this.lastBrokenDirection = null;
   }

   @Override
   public void onDeactivate() {
      for (PacketMine.MyBlock block : this.blocks) {
         if (block.mining) {
            this.mc.getConnection().send(new ServerboundPlayerActionPacket(Action.ABORT_DESTROY_BLOCK, block.blockPos, block.direction));
         }
         this.blockPool.free(block);
      }

      this.blocks.clear();
      this.combatTimer = 0;
      this.lastBrokenPos = null;
      this.lastBrokenDirection = null;
   }

   @EventHandler
   private void onAttackEntity(AttackEntityEvent event) {
      this.combatTimer = 20;
   }

   public boolean isCombatActive() {
      if (!this.pauseOnCombat.get()) {
         return false;
      }
      if (this.combatTimer > 0) {
         return true;
      }
      if (this.mc.hitResult instanceof EntityHitResult eHit && eHit.getEntity() != null) {
         return true;
      }
      return false;
   }

   @EventHandler
   private void onStartBreakingBlock(StartBreakingBlockEvent event) {
      if (BlockUtils.canBreak(event.blockPos)) {
         event.cancel();
         if (!this.isMiningBlock(event.blockPos)) {
            while (this.blocks.size() >= this.maxBlocks.get() && !this.blocks.isEmpty()) {
               PacketMine.MyBlock old = this.blocks.remove(this.blocks.size() - 1);
               if (old.mining) {
                  this.mc.getConnection().send(new ServerboundPlayerActionPacket(Action.ABORT_DESTROY_BLOCK, old.blockPos, old.direction));
               }
               this.blockPool.free(old);
            }
            this.blocks.add(this.blockPool.get().set(event));
         }
      }
   }

   public boolean isMiningBlock(BlockPos pos) {
      for (PacketMine.MyBlock block : this.blocks) {
         if (block.blockPos.equals(pos)) {
            return true;
         }
      }

      return false;
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.combatTimer > 0) {
         this.combatTimer--;
      }

      this.blocks.removeIf(PacketMine.MyBlock::shouldRemove);

      if (this.autoRebreak.get() && this.lastBrokenPos != null) {
         if (this.blocks.size() < this.maxBlocks.get() && !this.isMiningBlock(this.lastBrokenPos)) {
            BlockState state = this.mc.level.getBlockState(this.lastBrokenPos);
            if (BlockUtils.canBreak(this.lastBrokenPos, state) && !state.isAir()) {
               Direction dir = this.lastBrokenDirection != null ? this.lastBrokenDirection : BlockUtils.getDirection(this.lastBrokenPos);
               this.blocks.add(this.blockPool.get().set(this.lastBrokenPos, dir));
               this.lastBrokenPos = null;
            }
         }
      }

      int limit = Math.min(this.blocks.size(), this.maxBlocks.get());
      for (int i = 0; i < limit; i++) {
         this.blocks.get(i).mine();
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (this.render.get()) {
         for (PacketMine.MyBlock block : this.blocks) {
            if (!Modules.get().get(BreakIndicators.class).isActive() || !Modules.get().get(BreakIndicators.class).packetMine.get() || !block.mining) {
               block.render(event);
            }
         }
      }
   }

   public class MyBlock {
      public BlockPos blockPos;
      public BlockState blockState;
      public Block block;
      public Direction direction;
      public int timer;
      public boolean mining;
      public double progress;
      public boolean completed;
      public int readyTicks;

      public PacketMine.MyBlock set(StartBreakingBlockEvent event) {
         return this.set(event.blockPos, event.direction);
      }

      public PacketMine.MyBlock set(BlockPos pos, Direction dir) {
         this.blockPos = pos;
         this.direction = dir != null ? dir : Direction.UP;
         this.blockState = PacketMine.this.mc.level.getBlockState(this.blockPos);
         this.block = this.blockState.getBlock();
         this.timer = PacketMine.this.delay.get();
         this.mining = false;
         this.progress = 0.0;
         this.completed = false;
         this.readyTicks = 0;
         return this;
      }

      public boolean shouldRemove() {
         boolean blockBroken = PacketMine.this.mc.level.getBlockState(this.blockPos).getBlock() != this.block;
         if (blockBroken) {
            if (PacketMine.this.autoRebreak.get()) {
               PacketMine.this.lastBrokenPos = this.blockPos;
               PacketMine.this.lastBrokenDirection = this.direction;
            }
            return true;
         }

         boolean outOfRange = Utils.distance(
               PacketMine.this.mc.player.getX() - 0.5,
               PacketMine.this.mc.player.getY() + (double)PacketMine.this.mc.player.getEyeHeight(PacketMine.this.mc.player.getPose()),
               PacketMine.this.mc.player.getZ() - 0.5,
               (double)(this.blockPos.getX() + this.direction.getStepX()),
               (double)(this.blockPos.getY() + this.direction.getStepY()),
               (double)(this.blockPos.getZ() + this.direction.getStepZ())
            )
            > PacketMine.this.mc.player.blockInteractionRange();
         if (outOfRange) {
            PacketMine.this.mc.getConnection().send(new ServerboundPlayerActionPacket(Action.ABORT_DESTROY_BLOCK, this.blockPos, this.direction));
            PacketMine.this.mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
            return true;
         }

         if (this.readyTicks > 80) {
            PacketMine.this.mc.getConnection().send(new ServerboundPlayerActionPacket(Action.ABORT_DESTROY_BLOCK, this.blockPos, this.direction));
            return true;
         }

         return false;
      }

      public boolean isReady() {
         return this.progress >= 1.0;
      }

      public void mine() {
         if (!this.mining) {
            if (PacketMine.this.rotate.get()) {
               Rotations.rotate(Rotations.getYaw(this.blockPos), Rotations.getPitch(this.blockPos), 50, this::sendStartMinePackets);
            } else {
               this.sendStartMinePackets();
            }
            return;
         }

         if (this.completed) {
            this.readyTicks++;
            if (this.readyTicks > 25 && this.readyTicks % 15 == 0) {
               if (PacketMine.this.rotate.get()) {
                  Rotations.rotate(Rotations.getYaw(this.blockPos), Rotations.getPitch(this.blockPos), 50, () -> {
                     PacketMine.this.mc.getConnection().send(new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, this.blockPos, this.direction));
                     PacketMine.this.mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
                  });
               } else {
                  PacketMine.this.mc.getConnection().send(new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, this.blockPos, this.direction));
                  PacketMine.this.mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
               }
            }
            return;
         }

         double bestScore = -1.0;
         int bestSlot = -1;

         for (int i = 0; i < 9; i++) {
            double score = (double)PacketMine.this.mc.player.getInventory().getItem(i).getDestroySpeed(this.blockState);
            if (score > bestScore) {
               bestScore = score;
               bestSlot = i;
            }
         }

         this.progress += BlockUtils.getBreakDelta(bestSlot != -1 ? bestSlot : PacketMine.this.mc.player.getInventory().selected, this.blockState);

         if (this.progress >= 1.0) {
            if (PacketMine.this.isCombatActive()) {
               return;
            }

            if (PacketMine.this.rotate.get()) {
               Rotations.rotate(Rotations.getYaw(this.blockPos), Rotations.getPitch(this.blockPos), 50, this::sendStopMinePackets);
            } else {
               this.sendStopMinePackets();
            }
         }
      }

      private void sendStartMinePackets() {
         if (this.timer <= 0) {
            if (!this.mining) {
               Direction dir = BlockUtils.getDirection(this.blockPos);
               if (dir != null) {
                  this.direction = dir;
               }

               PacketMine.this.mc.getConnection().send(new ServerboundPlayerActionPacket(Action.START_DESTROY_BLOCK, this.blockPos, this.direction));
               PacketMine.this.mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
               this.mining = true;

               if (PacketMine.this.mode.get() == PacketMine.Mode.Instant) {
                  PacketMine.this.mc.getConnection().send(new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, this.blockPos, this.direction));
                  PacketMine.this.mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
                  this.completed = true;
               }
            }
         } else {
            this.timer--;
         }
      }

      private void sendStopMinePackets() {
         if (PacketMine.this.autoSwitch.get()) {
            if (PacketMine.this.notOnUse.get() && PacketMine.this.mc.player.isUsingItem()) {
               return;
            }

            FindItemResult tool = InvUtils.findFastestTool(this.blockState);
            if (tool.found() && PacketMine.this.mc.player.getInventory().selected != tool.slot()) {
               if (PacketMine.this.silentSwitch.get()) {
                  PacketMine.this.mc.getConnection().send(new ServerboundSetCarriedItemPacket(tool.slot()));
                  PacketMine.this.mc.getConnection().send(new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, this.blockPos, this.direction));
                  PacketMine.this.mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
                  PacketMine.this.mc.getConnection().send(new ServerboundSetCarriedItemPacket(PacketMine.this.mc.player.getInventory().selected));
                  this.completed = true;
                  return;
               } else {
                  InvUtils.swap(tool.slot(), false);
                  PacketMine.this.mc.getConnection().send(new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, this.blockPos, this.direction));
                  PacketMine.this.mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
                  this.completed = true;
                  return;
               }
            }
         }

         PacketMine.this.mc.getConnection().send(new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, this.blockPos, this.direction));
         PacketMine.this.mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
         this.completed = true;
      }

      public void render(Render3DEvent event) {
         VoxelShape shape = PacketMine.this.mc.level.getBlockState(this.blockPos).getShape(PacketMine.this.mc.level, this.blockPos);
         double x1 = (double)this.blockPos.getX();
         double y1 = (double)this.blockPos.getY();
         double z1 = (double)this.blockPos.getZ();
         double x2 = (double)(this.blockPos.getX() + 1);
         double y2 = (double)(this.blockPos.getY() + 1);
         double z2 = (double)(this.blockPos.getZ() + 1);
         if (!shape.isEmpty()) {
            x1 = (double)this.blockPos.getX() + shape.min(Axis.X);
            y1 = (double)this.blockPos.getY() + shape.min(Axis.Y);
            z1 = (double)this.blockPos.getZ() + shape.min(Axis.Z);
            x2 = (double)this.blockPos.getX() + shape.max(Axis.X);
            y2 = (double)this.blockPos.getY() + shape.max(Axis.Y);
            z2 = (double)this.blockPos.getZ() + shape.max(Axis.Z);
         }

         double factor = Math.min(Math.max(this.progress, 0.0), 1.0);

         if (PacketMine.this.renderMode.get() == PacketMine.RenderMode.Grow) {
            double cx = (x1 + x2) / 2.0;
            double cy = (y1 + y2) / 2.0;
            double cz = (z1 + z2) / 2.0;
            double hx = (x2 - x1) / 2.0 * factor;
            double hy = (y2 - y1) / 2.0 * factor;
            double hz = (z2 - z1) / 2.0 * factor;
            x1 = cx - hx;
            x2 = cx + hx;
            y1 = cy - hy;
            y2 = cy + hy;
            z1 = cz - hz;
            z2 = cz + hz;
         } else if (PacketMine.this.renderMode.get() == PacketMine.RenderMode.Shrink) {
            double shrinkFactor = 1.0 - factor;
            double cx = (x1 + x2) / 2.0;
            double cy = (y1 + y2) / 2.0;
            double cz = (z1 + z2) / 2.0;
            double hx = (x2 - x1) / 2.0 * shrinkFactor;
            double hy = (y2 - y1) / 2.0 * shrinkFactor;
            double hz = (z2 - z1) / 2.0 * shrinkFactor;
            x1 = cx - hx;
            x2 = cx + hx;
            y1 = cy - hy;
            y2 = cy + hy;
            z1 = cz - hz;
            z2 = cz + hz;
         }

         Color sideC;
         Color lineC;

         if (this.isReady()) {
            sideC = PacketMine.this.readySideColor.get();
            lineC = PacketMine.this.readyLineColor.get();
         } else if (PacketMine.this.colorMode.get() == PacketMine.ColorMode.Gradient) {
            sideC = lerpColor(PacketMine.this.sideColor.get(), PacketMine.this.readySideColor.get(), factor);
            lineC = lerpColor(PacketMine.this.lineColor.get(), PacketMine.this.readyLineColor.get(), factor);
         } else {
            sideC = PacketMine.this.sideColor.get();
            lineC = PacketMine.this.lineColor.get();
         }

         event.renderer.box(x1, y1, z1, x2, y2, z2, sideC, lineC, PacketMine.this.shapeMode.get(), 0);
      }
   }

   private static Color lerpColor(Color c1, Color c2, double delta) {
      float d = (float) Math.min(Math.max(delta, 0.0), 1.0);
      int r = (int) (c1.r + (c2.r - c1.r) * d);
      int g = (int) (c1.g + (c2.g - c1.g) * d);
      int b = (int) (c1.b + (c2.b - c1.b) * d);
      int a = (int) (c1.a + (c2.a - c1.a) * d);
      return new Color(r, g, b, a);
   }

   public static enum Mode {
      Normal,
      Instant;
   }

   public static enum RenderMode {
      Static,
      Grow,
      Shrink;
   }

   public static enum ColorMode {
      Flat,
      Gradient;
   }
}
