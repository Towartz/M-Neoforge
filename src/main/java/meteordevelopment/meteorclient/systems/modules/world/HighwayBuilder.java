package meteordevelopment.meteorclient.systems.modules.world;

import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.meteorclient.systems.modules.player.AutoGap;
import meteordevelopment.meteorclient.systems.modules.player.AutoTool;
import meteordevelopment.meteorclient.systems.modules.player.InstantRebreak;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.HorizontalDirection;
import meteordevelopment.meteorclient.utils.misc.MBlockPos;
import meteordevelopment.meteorclient.utils.player.CustomPlayerInput;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.meteorclient.utils.world.Dir;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class HighwayBuilder extends Module {
   private static final BlockPos ZERO = new BlockPos(0, 0, 0);
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgDigging = this.settings.createGroup("Digging");
   private final SettingGroup sgPaving = this.settings.createGroup("Paving");
   private final SettingGroup sgInventory = this.settings.createGroup("Inventory");
   private final SettingGroup sgRenderDigging = this.settings.createGroup("Render Digging");
   private final SettingGroup sgRenderPaving = this.settings.createGroup("Render Paving");
   private final Setting<Integer> width = this.sgGeneral
      .add(new IntSetting.Builder().name("width").description("Width of the highway.").defaultValue(Integer.valueOf(4)).range(1, 5).sliderRange(1, 5).build());
   private final Setting<Integer> height = this.sgGeneral
      .add(new IntSetting.Builder().name("height").description("Height of the highway.").defaultValue(Integer.valueOf(3)).range(2, 5).sliderRange(2, 5).build());
   private final Setting<HighwayBuilder.Floor> floor = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("floor"))
                  .description("What floor placement mode to use."))
               .defaultValue(HighwayBuilder.Floor.Replace))
            .build()
      );
   private final Setting<Boolean> railings = this.sgGeneral
      .add(new BoolSetting.Builder().name("railings").description("Builds railings next to the highway.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<Boolean> mineAboveRailings = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("mine-above-railings")
            .description("Mines blocks above railings.")
            .visible(this.railings::get)
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   private final Setting<HighwayBuilder.Rotation> rotation = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("rotation")).description("Mode of rotation."))
               .defaultValue(HighwayBuilder.Rotation.Both))
            .build()
      );
   private final Setting<Boolean> disconnectOnToggle = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("disconnect-on-toggle")
            .description("Automatically disconnects when the module is turned off, for example for not having enough blocks.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   private final Setting<Boolean> pauseOnLag = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("pause-on-lag")
            .description("Pauses the current process while the server stops responding.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   private final Setting<Boolean> dontBreakTools = this.sgDigging
      .add(new BoolSetting.Builder().name("dont-break-tools").description("Don't break tools.").defaultValue(Boolean.valueOf(false)).build());
   private final Setting<Integer> savePickaxes = this.sgDigging
      .add(
         new IntSetting.Builder()
            .name("save-pickaxes")
            .description("How many pickaxes to ensure are saved.")
            .defaultValue(Integer.valueOf(0))
            .range(0, 36)
            .sliderRange(0, 36)
            .visible(() -> !this.dontBreakTools.get())
            .build()
      );
   private final Setting<Integer> breakDelay = this.sgDigging
      .add(new IntSetting.Builder().name("break-delay").description("The delay between breaking blocks.").defaultValue(Integer.valueOf(0)).min(0).build());
   private final Setting<Integer> blocksPerTick = this.sgDigging
      .add(
         new IntSetting.Builder()
            .name("blocks-per-tick")
            .description("The maximum amount of blocks that can be mined in a tick. Only applies to blocks instantly breakable.")
            .defaultValue(Integer.valueOf(1))
            .range(1, 100)
            .sliderRange(1, 25)
            .build()
      );
   private final Setting<List<Block>> blocksToPlace = this.sgPaving
      .add(
         new BlockListSetting.Builder()
            .name("blocks-to-place")
            .description("Blocks it is allowed to place.")
            .defaultValue(Blocks.OBSIDIAN)
            .filter(block -> Block.isShapeFullBlock(block.defaultBlockState().getCollisionShape(EmptyBlockGetter.INSTANCE, ZERO)))
            .build()
      );
   private final Setting<Integer> placeDelay = this.sgPaving
      .add(new IntSetting.Builder().name("place-delay").description("The delay between placing blocks.").defaultValue(Integer.valueOf(0)).min(0).build());
   private final Setting<Integer> placementsPerTick = this.sgPaving
      .add(
         new IntSetting.Builder()
            .name("placements-per-tick")
            .description("The maximum amount of blocks that can be placed in a tick.")
            .defaultValue(Integer.valueOf(1))
            .min(1)
            .build()
      );
   private final Setting<List<Item>> trashItems = this.sgInventory
      .add(
         new ItemListSetting.Builder()
            .name("trash-items")
            .description("Items that are considered trash and can be thrown out.")
            .defaultValue(
               Items.NETHERRACK,
               Items.QUARTZ,
               Items.GOLD_NUGGET,
               Items.GOLDEN_SWORD,
               Items.GLOWSTONE_DUST,
               Items.GLOWSTONE,
               Items.BLACKSTONE,
               Items.BASALT,
               Items.GHAST_TEAR,
               Items.SOUL_SAND,
               Items.SOUL_SOIL,
               Items.ROTTEN_FLESH
            )
            .build()
      );
   private final Setting<Boolean> mineEnderChests = this.sgInventory
      .add(new BoolSetting.Builder().name("mine-ender-chests").description("Mines ender chests for obsidian.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<Integer> saveEchests = this.sgInventory
      .add(
         new IntSetting.Builder()
            .name("save-ender-chests")
            .description("How many ender chests to ensure are saved.")
            .defaultValue(Integer.valueOf(1))
            .range(0, 64)
            .sliderRange(0, 64)
            .visible(this.mineEnderChests::get)
            .build()
      );
   private final Setting<Boolean> rebreakEchests = this.sgInventory
      .add(
         new BoolSetting.Builder()
            .name("instantly-rebreak-echests")
            .description("Whether or not to use the instant rebreak exploit to break echests.")
            .defaultValue(Boolean.valueOf(false))
            .visible(this.mineEnderChests::get)
            .build()
      );
   private final Setting<Integer> rebreakTimer = this.sgInventory
      .add(
         new IntSetting.Builder()
            .name("rebreak-delay")
            .description("Delay between rebreak attempts.")
            .defaultValue(Integer.valueOf(0))
            .sliderMax(20)
            .visible(() -> this.mineEnderChests.get() && this.rebreakEchests.get())
            .build()
      );
   private final Setting<Boolean> renderMine = this.sgRenderDigging
      .add(new BoolSetting.Builder().name("render-blocks-to-mine").description("Render blocks to be mined.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<ShapeMode> renderMineShape = this.sgRenderDigging
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("blocks-to-mine-shape-mode"))
                  .description("How the blocks to be mined are rendered."))
               .defaultValue(ShapeMode.Both))
            .build()
      );
   private final Setting<SettingColor> renderMineSideColor = this.sgRenderDigging
      .add(
         new ColorSetting.Builder()
            .name("blocks-to-mine-side-color")
            .description("Color of blocks to be mined.")
            .defaultValue(new SettingColor(225, 25, 25, 25))
            .build()
      );
   private final Setting<SettingColor> renderMineLineColor = this.sgRenderDigging
      .add(
         new ColorSetting.Builder()
            .name("blocks-to-mine-line-color")
            .description("Color of blocks to be mined.")
            .defaultValue(new SettingColor(225, 25, 25))
            .build()
      );
   private final Setting<Boolean> renderPlace = this.sgRenderPaving
      .add(new BoolSetting.Builder().name("render-blocks-to-place").description("Render blocks to be placed.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<ShapeMode> renderPlaceShape = this.sgRenderPaving
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("blocks-to-place-shape-mode"))
                  .description("How the blocks to be placed are rendered."))
               .defaultValue(ShapeMode.Both))
            .build()
      );
   private final Setting<SettingColor> renderPlaceSideColor = this.sgRenderPaving
      .add(
         new ColorSetting.Builder()
            .name("blocks-to-place-side-color")
            .description("Color of blocks to be placed.")
            .defaultValue(new SettingColor(25, 25, 225, 25))
            .build()
      );
   private final Setting<SettingColor> renderPlaceLineColor = this.sgRenderPaving
      .add(
         new ColorSetting.Builder()
            .name("blocks-to-place-line-color")
            .description("Color of blocks to be placed.")
            .defaultValue(new SettingColor(25, 25, 225))
            .build()
      );
   private HorizontalDirection dir;
   private HorizontalDirection leftDir;
   private HorizontalDirection rightDir;
   private Input prevInput;
   private CustomPlayerInput input;
   private HighwayBuilder.State state;
   private HighwayBuilder.State lastState;
   private HighwayBuilder.IBlockPosProvider blockPosProvider;
   public Vec3 start;
   public int blocksBroken;
   public int blocksPlaced;
   private final MBlockPos lastBreakingPos = new MBlockPos();
   private boolean displayInfo;
   private int placeTimer;
   private int breakTimer;
   private int count;
   private final MBlockPos posRender2 = new MBlockPos();
   private final MBlockPos posRender3 = new MBlockPos();

   public HighwayBuilder() {
      super(Categories.World, "highway-builder", "Automatically builds highways.");
   }

   @Override
   public void onActivate() {
      this.dir = HorizontalDirection.get(this.mc.player.getYRot());
      this.leftDir = this.dir.rotateLeftSkipOne();
      this.rightDir = this.leftDir.opposite();
      this.prevInput = this.mc.player.input;
      this.mc.player.input = this.input = new CustomPlayerInput();
      this.state = HighwayBuilder.State.Forward;
      this.setState(HighwayBuilder.State.Center);
      this.blockPosProvider = (HighwayBuilder.IBlockPosProvider)(this.dir.diagonal
         ? new HighwayBuilder.DiagonalBlockPosProvider()
         : new HighwayBuilder.StraightBlockPosProvider());
      this.start = this.mc.player.position();
      this.blocksBroken = this.blocksPlaced = 0;
      this.lastBreakingPos.set(0, 0, 0);
      this.displayInfo = true;
      this.placeTimer = 0;
      this.breakTimer = 0;
      this.count = 0;
      if (this.blocksPerTick.get() > 1 && this.rotation.get().mine) {
         this.warning("With rotations enabled, you can break at most 1 block per tick.", new Object[0]);
      }

      if (this.placementsPerTick.get() > 1 && this.rotation.get().place) {
         this.warning("With rotations enabled, you can place at most 1 block per tick.", new Object[0]);
      }

      if (Modules.get().get(InstantRebreak.class).isActive()) {
         this.warning(
            "It's recommended to disable the Instant Rebreak module and instead use the 'instantly-rebreak-echests' setting to avoid errors.", new Object[0]
         );
      }
   }

   @Override
   public void onDeactivate() {
      this.mc.player.input = this.prevInput;
      this.mc.player.setYRot(this.dir.yaw);
      if (this.displayInfo) {
         this.info("Distance: (highlight)%.0f", new Object[]{PlayerUtils.distanceTo(this.start)});
         this.info("Blocks broken: (highlight)%d", new Object[]{this.blocksBroken});
         this.info("Blocks placed: (highlight)%d", new Object[]{this.blocksPlaced});
      }
   }

   @Override
   public void error(String message, Object... args) {
      super.error(message, args);
      this.toggle();
      if (this.disconnectOnToggle.get()) {
         this.disconnect(message, args);
      }
   }

   private void errorEarly(String message, Object... args) {
      super.error(message, args);
      this.displayInfo = false;
      this.toggle();
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.width.get() < 3 && this.dir.diagonal) {
         this.errorEarly("Diagonal highways with width less than 3 are not supported.");
      } else if (!Modules.get().get(AutoEat.class).eating) {
         if (!Modules.get().get(AutoGap.class).isEating()) {
            if (!Modules.get().get(KillAura.class).attacking) {
               if (!this.pauseOnLag.get() || !(TickRate.INSTANCE.getTimeSinceLastTick() >= 2.0F)) {
                  this.count = 0;
                  this.state.tick(this);
                  if (this.breakTimer > 0) {
                     this.breakTimer--;
                  }

                  if (this.placeTimer > 0) {
                     this.placeTimer--;
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.renderMine.get()) {
         this.render(event, this.blockPosProvider.getFront(), mBlockPos -> this.canMine(mBlockPos, true), true);
         if (this.floor.get() == HighwayBuilder.Floor.Replace) {
            this.render(event, this.blockPosProvider.getFloor(), mBlockPos -> this.canMine(mBlockPos, false), true);
         }

         if (this.railings.get()) {
            this.render(event, this.blockPosProvider.getRailings(true), mBlockPos -> this.canMine(mBlockPos, false), true);
         }

         if (this.state == HighwayBuilder.State.MineEChestBlockade) {
            this.render(event, this.blockPosProvider.getEChestBlockade(true), mBlockPos -> this.canMine(mBlockPos, true), true);
         }
      }

      if (this.renderPlace.get()) {
         this.render(event, this.blockPosProvider.getLiquids(), mBlockPos -> this.canPlace(mBlockPos, true), false);
         if (this.railings.get()) {
            this.render(event, this.blockPosProvider.getRailings(false), mBlockPos -> this.canPlace(mBlockPos, false), false);
         }

         this.render(event, this.blockPosProvider.getFloor(), mBlockPos -> this.canPlace(mBlockPos, false), false);
         if (this.state == HighwayBuilder.State.PlaceEChestBlockade) {
            this.render(event, this.blockPosProvider.getEChestBlockade(false), mBlockPos -> this.canPlace(mBlockPos, false), false);
         }
      }
   }

   private void render(Render3DEvent event, HighwayBuilder.MBPIterator it, Predicate<MBlockPos> predicate, boolean mine) {
      Color sideColor = mine ? this.renderMineSideColor.get() : this.renderPlaceSideColor.get();
      Color lineColor = mine ? this.renderMineLineColor.get() : this.renderPlaceLineColor.get();
      ShapeMode shapeMode = mine ? this.renderMineShape.get() : this.renderPlaceShape.get();

      for (MBlockPos pos : it) {
         this.posRender2.set(pos);
         if (predicate.test(this.posRender2)) {
            int excludeDir = 0;

            for (Direction side : Direction.values()) {
               this.posRender3.set(this.posRender2).add(side.getStepX(), side.getStepY(), side.getStepZ());
               it.save();

               for (MBlockPos p : it) {
                  if (p.equals(this.posRender3) && predicate.test(p)) {
                     excludeDir |= Dir.get(side);
                  }
               }

               it.restore();
            }

            event.renderer.box(this.posRender2.getBlockPos(), sideColor, lineColor, shapeMode, excludeDir);
         }
      }
   }

   private void setState(HighwayBuilder.State state) {
      this.lastState = this.state;
      this.state = state;
      this.input.stop();
      state.start(this);
   }

   private int getWidthLeft() {
      return switch (this.width.get()) {
         case 2, 3 -> 1;
         case 4, 5 -> 2;
         default -> 0;
      };
   }

   private int getWidthRight() {
      return switch (this.width.get()) {
         case 3, 4 -> 1;
         case 5 -> 2;
         default -> 0;
      };
   }

   private boolean canMine(MBlockPos pos, boolean ignoreBlocksToPlace) {
      BlockState state = pos.getState();
      return BlockUtils.canBreak(pos.getBlockPos(), state) && (ignoreBlocksToPlace || !this.blocksToPlace.get().contains(state.getBlock()));
   }

   private boolean canPlace(MBlockPos pos, boolean liquids) {
      return liquids ? !pos.getState().getFluidState().isEmpty() : BlockUtils.canPlace(pos.getBlockPos());
   }

   private void disconnect(String message, Object... args) {
      MutableComponent text = Component.literal(
            String.format("%s[%s%s%s] %s", ChatFormatting.GRAY, ChatFormatting.BLUE, this.title, ChatFormatting.GRAY, ChatFormatting.RED)
               + String.format(message, args)
         )
         .append("\n");
      text.append(this.getStatsText());
      this.mc.getConnection().getConnection().disconnect(text);
   }

   public MutableComponent getStatsText() {
      MutableComponent text = Component.literal(
         String.format("%sDistance: %s%.0f\n", ChatFormatting.GRAY, ChatFormatting.WHITE, this.mc.player == null ? 0.0 : PlayerUtils.distanceTo(this.start))
      );
      text.append(String.format("%sBlocks broken: %s%d\n", ChatFormatting.GRAY, ChatFormatting.WHITE, this.blocksBroken));
      text.append(String.format("%sBlocks placed: %s%d", ChatFormatting.GRAY, ChatFormatting.WHITE, this.blocksPlaced));
      return text;
   }

   private class DiagonalBlockPosProvider implements HighwayBuilder.IBlockPosProvider {
      private final MBlockPos pos = new MBlockPos();
      private final MBlockPos pos2 = new MBlockPos();

      @Override
      public HighwayBuilder.MBPIterator getFront() {
         this.pos
            .set(HighwayBuilder.this.mc.player)
            .offset(HighwayBuilder.this.dir.rotateLeft())
            .offset(HighwayBuilder.this.leftDir, HighwayBuilder.this.getWidthLeft() - 1);
         return new HighwayBuilder.MBPIterator() {
            private int i;
            private int w;
            private int y;
            private int pi;
            private int pw;
            private int py;

            @Override
            public boolean hasNext() {
               return this.i < 2 && this.w < HighwayBuilder.this.width.get() && this.y < HighwayBuilder.this.height.get();
            }

            public MBlockPos next() {
               DiagonalBlockPosProvider.this.pos2.set(DiagonalBlockPosProvider.this.pos).offset(HighwayBuilder.this.rightDir, this.w).add(0, this.y++, 0);
               if (this.y >= HighwayBuilder.this.height.get()) {
                  this.y = 0;
                  this.w++;
                  if (this.w >= (this.i == 0 ? HighwayBuilder.this.width.get() - 1 : HighwayBuilder.this.width.get())) {
                     this.w = 0;
                     this.i++;
                     DiagonalBlockPosProvider.this.pos
                        .set(HighwayBuilder.this.mc.player)
                        .offset(HighwayBuilder.this.dir)
                        .offset(HighwayBuilder.this.leftDir, HighwayBuilder.this.getWidthLeft());
                  }
               }

               return DiagonalBlockPosProvider.this.pos2;
            }

            private void initPos() {
               if (this.i == 0) {
                  DiagonalBlockPosProvider.this.pos
                     .set(HighwayBuilder.this.mc.player)
                     .offset(HighwayBuilder.this.dir.rotateLeft())
                     .offset(HighwayBuilder.this.leftDir, HighwayBuilder.this.getWidthLeft() - 1);
               } else {
                  DiagonalBlockPosProvider.this.pos
                     .set(HighwayBuilder.this.mc.player)
                     .offset(HighwayBuilder.this.dir)
                     .offset(HighwayBuilder.this.leftDir, HighwayBuilder.this.getWidthLeft());
               }
            }

            @Override
            public void save() {
               this.pi = this.i;
               this.pw = this.w;
               this.py = this.y;
               this.i = this.w = this.y = 0;
               this.initPos();
            }

            @Override
            public void restore() {
               this.i = this.pi;
               this.w = this.pw;
               this.y = this.py;
               this.initPos();
            }
         };
      }

      @Override
      public HighwayBuilder.MBPIterator getFloor() {
         this.pos
            .set(HighwayBuilder.this.mc.player)
            .add(0, -1, 0)
            .offset(HighwayBuilder.this.dir.rotateLeft())
            .offset(HighwayBuilder.this.leftDir, HighwayBuilder.this.getWidthLeft() - 1);
         return new HighwayBuilder.MBPIterator() {
            private int i;
            private int w;
            private int pi;
            private int pw;

            @Override
            public boolean hasNext() {
               return this.i < 2 && this.w < HighwayBuilder.this.width.get();
            }

            public MBlockPos next() {
               DiagonalBlockPosProvider.this.pos2.set(DiagonalBlockPosProvider.this.pos).offset(HighwayBuilder.this.rightDir, this.w++);
               if (this.w >= (this.i == 0 ? HighwayBuilder.this.width.get() - 1 : HighwayBuilder.this.width.get())) {
                  this.w = 0;
                  this.i++;
                  DiagonalBlockPosProvider.this.pos
                     .set(HighwayBuilder.this.mc.player)
                     .add(0, -1, 0)
                     .offset(HighwayBuilder.this.dir)
                     .offset(HighwayBuilder.this.leftDir, HighwayBuilder.this.getWidthLeft());
               }

               return DiagonalBlockPosProvider.this.pos2;
            }

            private void initPos() {
               if (this.i == 0) {
                  DiagonalBlockPosProvider.this.pos
                     .set(HighwayBuilder.this.mc.player)
                     .add(0, -1, 0)
                     .offset(HighwayBuilder.this.dir.rotateLeft())
                     .offset(HighwayBuilder.this.leftDir, HighwayBuilder.this.getWidthLeft() - 1);
               } else {
                  DiagonalBlockPosProvider.this.pos
                     .set(HighwayBuilder.this.mc.player)
                     .add(0, -1, 0)
                     .offset(HighwayBuilder.this.dir)
                     .offset(HighwayBuilder.this.leftDir, HighwayBuilder.this.getWidthLeft());
               }
            }

            @Override
            public void save() {
               this.pi = this.i;
               this.pw = this.w;
               this.i = this.w = 0;
               this.initPos();
            }

            @Override
            public void restore() {
               this.i = this.pi;
               this.w = this.pw;
               this.initPos();
            }
         };
      }

      @Override
      public HighwayBuilder.MBPIterator getRailings(boolean mine) {
         final boolean mineAll = mine && HighwayBuilder.this.mineAboveRailings.get();
         this.pos
            .set(HighwayBuilder.this.mc.player)
            .offset(HighwayBuilder.this.dir.rotateLeft())
            .offset(HighwayBuilder.this.leftDir, HighwayBuilder.this.getWidthLeft());
         return new HighwayBuilder.MBPIterator() {
            private int i;
            private int y;
            private int pi;
            private int py;

            @Override
            public boolean hasNext() {
               return this.i < 2 && this.y < (mineAll ? HighwayBuilder.this.height.get() : 1);
            }

            public MBlockPos next() {
               DiagonalBlockPosProvider.this.pos2.set(DiagonalBlockPosProvider.this.pos).add(0, this.y++, 0);
               if (this.y >= (mineAll ? HighwayBuilder.this.height.get() : 1)) {
                  this.y = 0;
                  this.i++;
                  DiagonalBlockPosProvider.this.pos
                     .set(HighwayBuilder.this.mc.player)
                     .offset(HighwayBuilder.this.dir.rotateRight())
                     .offset(HighwayBuilder.this.rightDir, HighwayBuilder.this.getWidthRight());
               }

               return DiagonalBlockPosProvider.this.pos2;
            }

            private void initPos() {
               if (this.i == 0) {
                  DiagonalBlockPosProvider.this.pos
                     .set(HighwayBuilder.this.mc.player)
                     .offset(HighwayBuilder.this.dir.rotateLeft())
                     .offset(HighwayBuilder.this.leftDir, HighwayBuilder.this.getWidthLeft());
               } else {
                  DiagonalBlockPosProvider.this.pos
                     .set(HighwayBuilder.this.mc.player)
                     .offset(HighwayBuilder.this.dir.rotateRight())
                     .offset(HighwayBuilder.this.rightDir, HighwayBuilder.this.getWidthRight());
               }
            }

            @Override
            public void save() {
               this.pi = this.i;
               this.py = this.y;
               this.i = this.y = 0;
               this.initPos();
            }

            @Override
            public void restore() {
               this.i = this.pi;
               this.y = this.py;
               this.initPos();
            }
         };
      }

      @Override
      public HighwayBuilder.MBPIterator getLiquids() {
         final boolean m = HighwayBuilder.this.railings.get() && HighwayBuilder.this.mineAboveRailings.get();
         this.pos
            .set(HighwayBuilder.this.mc.player)
            .offset(HighwayBuilder.this.dir)
            .offset(HighwayBuilder.this.dir.rotateLeft())
            .offset(HighwayBuilder.this.leftDir, HighwayBuilder.this.getWidthLeft());
         return new HighwayBuilder.MBPIterator() {
            private int i;
            private int w;
            private int y;
            private int pi;
            private int pw;
            private int py;

            private int getWidth() {
               return HighwayBuilder.this.width.get() + (this.i == 0 ? 1 : 0) + (m && this.i == 1 ? 2 : 0);
            }

            @Override
            public boolean hasNext() {
               return m && this.i == 1 && this.y == HighwayBuilder.this.height.get() && this.w == this.getWidth() - 1
                  ? false
                  : this.i < 2 && this.w < this.getWidth() && this.y < HighwayBuilder.this.height.get() + 1;
            }

            private void updateW() {
               this.w++;
               if (this.w >= this.getWidth()) {
                  this.w = 0;
                  this.i++;
                  DiagonalBlockPosProvider.this.pos
                     .set(HighwayBuilder.this.mc.player)
                     .offset(HighwayBuilder.this.dir, 2)
                     .offset(HighwayBuilder.this.leftDir, HighwayBuilder.this.getWidthLeft() + (m ? 1 : 0));
               }
            }

            public MBlockPos next() {
               if (this.i == (m ? 1 : 0) && this.y == HighwayBuilder.this.height.get() && (this.w == 0 || this.w == this.getWidth() - 1)) {
                  this.y = 0;
                  this.updateW();
               }

               DiagonalBlockPosProvider.this.pos2.set(DiagonalBlockPosProvider.this.pos).offset(HighwayBuilder.this.rightDir, this.w).add(0, this.y++, 0);
               if (this.y >= HighwayBuilder.this.height.get() + 1) {
                  this.y = 0;
                  this.updateW();
               }

               return DiagonalBlockPosProvider.this.pos2;
            }

            private void initPos() {
               if (this.i == 0) {
                  DiagonalBlockPosProvider.this.pos
                     .set(HighwayBuilder.this.mc.player)
                     .offset(HighwayBuilder.this.dir)
                     .offset(HighwayBuilder.this.dir.rotateLeft())
                     .offset(HighwayBuilder.this.leftDir, HighwayBuilder.this.getWidthLeft());
               } else {
                  DiagonalBlockPosProvider.this.pos
                     .set(HighwayBuilder.this.mc.player)
                     .offset(HighwayBuilder.this.dir, 2)
                     .offset(HighwayBuilder.this.leftDir, HighwayBuilder.this.getWidthLeft() + (m ? 1 : 0));
               }
            }

            @Override
            public void save() {
               this.pi = this.i;
               this.pw = this.w;
               this.py = this.y;
               this.i = this.w = this.y = 0;
               this.initPos();
            }

            @Override
            public void restore() {
               this.i = this.pi;
               this.w = this.pw;
               this.y = this.py;
               this.initPos();
            }
         };
      }

      @Override
      public HighwayBuilder.MBPIterator getEChestBlockade(boolean mine) {
         return new HighwayBuilder.MBPIterator() {
            private int i = mine ? -1 : 0;
            private int y;
            private int pi;
            private int py;

            private MBlockPos get(int i) {
               HorizontalDirection dir2 = HighwayBuilder.this.dir.rotateLeft().rotateLeftSkipOne();
               DiagonalBlockPosProvider.this.pos.set(HighwayBuilder.this.mc.player).offset(dir2);

               return switch (i) {
                  case -1 -> DiagonalBlockPosProvider.this.pos;
                  default -> DiagonalBlockPosProvider.this.pos.offset(dir2);
                  case 1 -> DiagonalBlockPosProvider.this.pos.offset(dir2.rotateLeftSkipOne());
                  case 2 -> DiagonalBlockPosProvider.this.pos.offset(dir2.rotateLeftSkipOne().opposite());
                  case 3 -> DiagonalBlockPosProvider.this.pos.offset(dir2.opposite(), 2);
               };
            }

            @Override
            public boolean hasNext() {
               return this.i < 4 && this.y < 2;
            }

            public MBlockPos next() {
               MBlockPos pos = this.get(this.i).add(0, this.y, 0);
               this.y++;
               if (this.y > 1) {
                  this.y = 0;
                  this.i++;
               }

               return pos;
            }

            @Override
            public void save() {
               this.pi = this.i;
               this.py = this.y;
               this.i = this.y = 0;
            }

            @Override
            public void restore() {
               this.i = this.pi;
               this.y = this.py;
            }
         };
      }
   }

   public static enum Floor {
      Replace,
      PlaceMissing;
   }

   private interface IBlockPosProvider {
      HighwayBuilder.MBPIterator getFront();

      HighwayBuilder.MBPIterator getFloor();

      HighwayBuilder.MBPIterator getRailings(boolean var1);

      HighwayBuilder.MBPIterator getLiquids();

      HighwayBuilder.MBPIterator getEChestBlockade(boolean var1);
   }

   private interface MBPIterator extends Iterator<MBlockPos>, Iterable<MBlockPos> {
      void save();

      void restore();

      @NotNull
      @Override
      default Iterator<MBlockPos> iterator() {
         return this;
      }
   }

   private static class MBPIteratorFilter implements HighwayBuilder.MBPIterator {
      private final HighwayBuilder.MBPIterator it;
      private final Predicate<MBlockPos> predicate;
      private MBlockPos pos;
      private boolean isOld = true;
      private boolean pisOld = true;

      public MBPIteratorFilter(HighwayBuilder.MBPIterator it, Predicate<MBlockPos> predicate) {
         this.it = it;
         this.predicate = predicate;
      }

      @Override
      public void save() {
         this.it.save();
         this.pisOld = this.isOld;
         this.isOld = true;
      }

      @Override
      public void restore() {
         this.it.restore();
         this.isOld = this.pisOld;
      }

      @Override
      public boolean hasNext() {
         if (this.isOld) {
            this.isOld = false;

            for (this.pos = null; this.it.hasNext(); this.pos = null) {
               this.pos = this.it.next();
               if (this.predicate.test(this.pos)) {
                  return true;
               }
            }
         }

         return this.pos != null && this.predicate.test(this.pos);
      }

      public MBlockPos next() {
         this.isOld = true;
         return this.pos;
      }
   }

   public static enum Rotation {
      None(false, false),
      Mine(true, false),
      Place(false, true),
      Both(true, true);

      public final boolean mine;
      public final boolean place;

      private Rotation(boolean mine, boolean place) {
         this.mine = mine;
         this.place = place;
      }
   }

   private static enum State {
      Center {
         @Override
         protected void tick(HighwayBuilder b) {
            double x = Math.abs(b.mc.player.getX() - (double)((int)b.mc.player.getX())) - 0.5;
            double z = Math.abs(b.mc.player.getZ() - (double)((int)b.mc.player.getZ())) - 0.5;
            boolean isX = Math.abs(x) <= 0.1;
            boolean isZ = Math.abs(z) <= 0.1;
            if (isX && isZ) {
               b.input.stop();
               b.mc.player.setDeltaMovement(0.0, 0.0, 0.0);
               b.mc
                  .player
                  .setPos(
                     (double)((int)b.mc.player.getX()) + (b.mc.player.getX() < 0.0 ? -0.5 : 0.5),
                     b.mc.player.getY(),
                     (double)((int)b.mc.player.getZ()) + (b.mc.player.getZ() < 0.0 ? -0.5 : 0.5)
                  );
               b.setState(b.lastState);
            } else {
               b.mc.player.setYRot(0.0F);
               if (!isZ) {
                  b.input.up = z < 0.0;
                  b.input.down = z > 0.0;
                  if (b.mc.player.getZ() < 0.0) {
                     boolean forward = b.input.up;
                     b.input.up = b.input.down;
                     b.input.down = forward;
                  }
               }

               if (!isX) {
                  b.input.right = x > 0.0;
                  b.input.left = x < 0.0;
                  if (b.mc.player.getX() < 0.0) {
                     boolean right = b.input.right;
                     b.input.right = b.input.left;
                     b.input.left = right;
                  }
               }

               b.input.shiftKeyDown = true;
            }
         }
      },
      Forward {
         @Override
         protected void start(HighwayBuilder b) {
            b.mc.player.setYRot(b.dir.yaw);
            this.checkTasks(b);
         }

         @Override
         protected void tick(HighwayBuilder b) {
            this.checkTasks(b);
            if (b.state == Forward) {
               b.input.up = true;
            }
         }

         private void checkTasks(HighwayBuilder b) {
            if (this.needsToPlace(b, b.blockPosProvider.getLiquids(), true)) {
               b.setState(FillLiquids);
            } else if (this.needsToMine(b, b.blockPosProvider.getFront(), true)) {
               b.setState(MineFront);
            } else if (b.floor.get() == HighwayBuilder.Floor.Replace && this.needsToMine(b, b.blockPosProvider.getFloor(), false)) {
               b.setState(MineFloor);
            } else if (b.railings.get() && this.needsToMine(b, b.blockPosProvider.getRailings(true), false)) {
               b.setState(MineRailings);
            } else if (b.railings.get() && this.needsToPlace(b, b.blockPosProvider.getRailings(false), false)) {
               b.setState(PlaceRailings);
            } else if (this.needsToPlace(b, b.blockPosProvider.getFloor(), false)) {
               b.setState(PlaceFloor);
            }
         }

         private boolean needsToMine(HighwayBuilder b, HighwayBuilder.MBPIterator it, boolean ignoreBlocksToPlace) {
            for (MBlockPos pos : it) {
               if (b.canMine(pos, ignoreBlocksToPlace)) {
                  return true;
               }
            }

            return false;
         }

         private boolean needsToPlace(HighwayBuilder b, HighwayBuilder.MBPIterator it, boolean liquids) {
            for (MBlockPos pos : it) {
               if (b.canPlace(pos, liquids)) {
                  return true;
               }
            }

            return false;
         }
      },
      FillLiquids {
         @Override
         protected void tick(HighwayBuilder b) {
            int slot = this.findBlocksToPlacePrioritizeTrash(b);
            if (slot != -1) {
               this.place(
                  b, new HighwayBuilder.MBPIteratorFilter(b.blockPosProvider.getLiquids(), pos -> !pos.getState().getFluidState().isEmpty()), slot, Forward
               );
            }
         }
      },
      MineFront {
         @Override
         protected void tick(HighwayBuilder b) {
            this.mine(b, b.blockPosProvider.getFront(), true, MineFloor, this);
         }
      },
      MineFloor {
         @Override
         protected void start(HighwayBuilder b) {
            this.mine(b, b.blockPosProvider.getFloor(), false, MineRailings, this);
         }

         @Override
         protected void tick(HighwayBuilder b) {
            this.mine(b, b.blockPosProvider.getFloor(), false, MineRailings, this);
         }
      },
      MineRailings {
         @Override
         protected void start(HighwayBuilder b) {
            this.mine(b, b.blockPosProvider.getRailings(true), false, PlaceRailings, this);
         }

         @Override
         protected void tick(HighwayBuilder b) {
            this.mine(b, b.blockPosProvider.getRailings(true), false, PlaceRailings, this);
         }
      },
      PlaceRailings {
         @Override
         protected void tick(HighwayBuilder b) {
            int slot = this.findBlocksToPlace(b);
            if (slot != -1) {
               this.place(b, b.blockPosProvider.getRailings(false), slot, Forward);
            }
         }
      },
      PlaceFloor {
         @Override
         protected void start(HighwayBuilder b) {
            int slot = this.findBlocksToPlace(b);
            if (slot != -1) {
               this.place(b, b.blockPosProvider.getFloor(), slot, Forward);
            }
         }

         @Override
         protected void tick(HighwayBuilder b) {
            int slot = this.findBlocksToPlace(b);
            if (slot != -1) {
               this.place(b, b.blockPosProvider.getFloor(), slot, Forward);
            }
         }
      },
      ThrowOutTrash {
         private int skipSlot;
         private boolean timerEnabled;
         private boolean firstTick;
         private int timer;

         @Override
         protected void start(HighwayBuilder b) {
            int biggestCount = 0;

            for (int i = 0; i < b.mc.player.getInventory().items.size(); i++) {
               ItemStack itemStack = b.mc.player.getInventory().getItem(i);
               if (itemStack.getItem() instanceof BlockItem && b.trashItems.get().contains(itemStack.getItem()) && itemStack.getCount() > biggestCount) {
                  biggestCount = itemStack.getCount();
                  this.skipSlot = i;
                  if (biggestCount >= 64) {
                     break;
                  }
               }
            }

            if (biggestCount == 0) {
               this.skipSlot = -1;
            }

            this.timerEnabled = false;
            this.firstTick = true;
         }

         @Override
         protected void tick(HighwayBuilder b) {
            if (this.timerEnabled) {
               if (this.timer > 0) {
                  this.timer--;
               } else {
                  b.setState(b.lastState);
               }
            } else {
               b.mc.player.setYRot(b.dir.opposite().yaw);
               b.mc.player.setXRot(-25.0F);
               if (this.firstTick) {
                  this.firstTick = false;
               } else if (!b.mc.player.containerMenu.getCarried().isEmpty()) {
                  InvUtils.dropHand();
               } else {
                  for (int i = 0; i < b.mc.player.getInventory().items.size(); i++) {
                     if (i != this.skipSlot) {
                        ItemStack itemStack = b.mc.player.getInventory().getItem(i);
                        if (b.trashItems.get().contains(itemStack.getItem())) {
                           InvUtils.drop().slot(i);
                           return;
                        }
                     }
                  }

                  this.timerEnabled = true;
                  this.timer = 10;
               }
            }
         }
      },
      PlaceEChestBlockade {
         @Override
         protected void tick(HighwayBuilder b) {
            int slot = this.findBlocksToPlacePrioritizeTrash(b);
            if (slot != -1) {
               this.place(b, b.blockPosProvider.getEChestBlockade(false), slot, MineEnderChests);
            }
         }
      },
      MineEChestBlockade {
         @Override
         protected void tick(HighwayBuilder b) {
            this.mine(b, b.blockPosProvider.getEChestBlockade(true), true, Center, Forward);
         }
      },
      MineEnderChests {
         private static final MBlockPos pos = new MBlockPos();
         private int minimumObsidian;
         private boolean first;
         private boolean primed;
         private boolean stopTimerEnabled;
         private int stopTimer;
         private int moveTimer;
         private int rebreakTimer;

         @Override
         protected void start(HighwayBuilder b) {
            if (b.lastState != Center && b.lastState != ThrowOutTrash && b.lastState != PlaceEChestBlockade) {
               b.setState(Center);
            } else if (b.lastState == Center) {
               b.setState(ThrowOutTrash);
            } else if (b.lastState == ThrowOutTrash) {
               b.setState(PlaceEChestBlockade);
            } else {
               int emptySlots = 0;

               for (int i = 0; i < b.mc.player.getInventory().items.size(); i++) {
                  if (b.mc.player.getInventory().getItem(i).isEmpty()) {
                     emptySlots++;
                  }
               }

               if (emptySlots == 0) {
                  b.error("No empty slots.");
               } else {
                  int minimumSlots = Math.max(emptySlots - 4, 1);
                  this.minimumObsidian = minimumSlots * 64;
                  this.first = true;
                  this.moveTimer = 0;
                  this.stopTimerEnabled = false;
                  this.primed = false;
               }
            }
         }

         @Override
         protected void tick(HighwayBuilder b) {
            if (this.stopTimerEnabled) {
               if (this.stopTimer > 0) {
                  this.stopTimer--;
               } else {
                  b.setState(MineEChestBlockade);
               }
            } else {
               HorizontalDirection dir = b.dir.diagonal ? b.dir.rotateLeft().rotateLeftSkipOne() : b.dir.opposite();
               pos.set(b.mc.player).offset(dir);
               if (this.moveTimer > 0) {
                  b.mc.player.setYRot(dir.yaw);
                  b.input.up = this.moveTimer > 2;
                  this.moveTimer--;
               } else {
                  int obsidianCount = 0;
                  double var10004 = (double)pos.x;
                  double var10005 = (double)pos.y;
                  double var10006 = (double)pos.z;

                  for (Entity entity : b.mc
                     .level
                     .getEntities(b.mc.player, new AABB(var10004, var10005, var10006, (double)(pos.x + 1), (double)(pos.y + 2), (double)(pos.z + 1)))) {
                     if (entity instanceof ItemEntity) {
                        ItemEntity itemEntity = (ItemEntity)entity;
                        if (itemEntity.getItem().getItem() == Items.OBSIDIAN) {
                           obsidianCount += itemEntity.getItem().getCount();
                        }
                     }
                  }

                  for (int i = 0; i < b.mc.player.getInventory().items.size(); i++) {
                     ItemStack itemStack = b.mc.player.getInventory().getItem(i);
                     if (itemStack.getItem() == Items.OBSIDIAN) {
                        obsidianCount += itemStack.getCount();
                     }
                  }

                  if (obsidianCount >= this.minimumObsidian) {
                     this.stopTimerEnabled = true;
                     this.stopTimer = 8;
                  } else {
                     BlockPos bp = pos.getBlockPos();
                     BlockState blockState = b.mc.level.getBlockState(bp);
                     if (blockState.getBlock() == Blocks.ENDER_CHEST) {
                        if (this.first) {
                           this.moveTimer = 8;
                           this.first = false;
                           return;
                        }

                        int slot = this.findAndMoveBestToolToHotbar(b, blockState, true);
                        if (slot == -1) {
                           b.error("Cannot find pickaxe without silk touch to mine ender chests.");
                           return;
                        }

                        InvUtils.swap(slot, false);
                        if (b.rebreakEchests.get() && this.primed) {
                           if (this.rebreakTimer > 0) {
                              this.rebreakTimer--;
                              return;
                           }

                           ServerboundPlayerActionPacket p = new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, bp, BlockUtils.getDirection(bp));
                           this.rebreakTimer = b.rebreakTimer.get();
                           if (b.rotation.get().mine) {
                              Rotations.rotate(Rotations.getYaw(bp), Rotations.getPitch(bp), () -> b.mc.getConnection().send(p));
                           } else {
                              b.mc.getConnection().send(p);
                           }
                        } else if (b.rotation.get().mine) {
                           Rotations.rotate(Rotations.getYaw(bp), Rotations.getPitch(bp), () -> BlockUtils.breakBlock(bp, true));
                        } else {
                           BlockUtils.breakBlock(bp, true);
                        }
                     } else {
                        int slotx = this.findAndMoveToHotbar(b, itemStack -> itemStack.getItem() == Items.ENDER_CHEST, false);
                        if (slotx == -1 || this.countItem(b, stack -> stack.getItem().equals(Items.ENDER_CHEST)) <= b.saveEchests.get()) {
                           this.stopTimerEnabled = true;
                           this.stopTimer = 4;
                           return;
                        }

                        if (!this.first) {
                           this.primed = true;
                        }

                        BlockUtils.place(bp, InteractionHand.MAIN_HAND, slotx, b.rotation.get().place, 0, true, true, false);
                     }
                  }
               }
            }
         }
      };

      protected void start(HighwayBuilder b) {
      }

      protected abstract void tick(HighwayBuilder var1);

      protected void mine(
         HighwayBuilder b, HighwayBuilder.MBPIterator it, boolean ignoreBlocksToPlace, HighwayBuilder.State nextState, HighwayBuilder.State lastState
      ) {
         boolean breaking = false;
         boolean finishedBreaking = false;

         for (MBlockPos pos : it) {
            if (b.count >= b.blocksPerTick.get()) {
               return;
            }

            if (b.breakTimer > 0) {
               return;
            }

            BlockState state = pos.getState();
            if (!state.isAir() && (ignoreBlocksToPlace || !b.blocksToPlace.get().contains(state.getBlock()))) {
               int slot = this.findAndMoveBestToolToHotbar(b, state, false);
               if (slot == -1) {
                  return;
               }

               InvUtils.swap(slot, false);
               BlockPos mcPos = pos.getBlockPos();
               if (BlockUtils.canBreak(mcPos)) {
                  if (b.rotation.get().mine) {
                     Rotations.rotate(Rotations.getYaw(mcPos), Rotations.getPitch(mcPos), () -> BlockUtils.breakBlock(mcPos, true));
                  } else {
                     BlockUtils.breakBlock(mcPos, true);
                  }

                  breaking = true;
                  b.breakTimer = b.breakDelay.get();
                  if (!b.lastBreakingPos.equals(pos)) {
                     b.lastBreakingPos.set(pos);
                     b.blocksBroken++;
                  }

                  b.count++;
                  if (b.blocksPerTick.get() == 1 || !BlockUtils.canInstaBreak(mcPos) || b.rotation.get().mine) {
                     break;
                  }
               }

               if (!it.hasNext() && BlockUtils.canInstaBreak(mcPos)) {
                  finishedBreaking = true;
               }
            }
         }

         if (finishedBreaking || !breaking) {
            b.setState(nextState);
            b.lastState = lastState;
         }
      }

      protected void place(HighwayBuilder b, HighwayBuilder.MBPIterator it, int slot, HighwayBuilder.State nextState) {
         boolean placed = false;
         boolean finishedPlacing = false;

         for (MBlockPos pos : it) {
            if (b.count >= b.placementsPerTick.get()) {
               return;
            }

            if (b.placeTimer > 0) {
               return;
            }

            if (BlockUtils.place(pos.getBlockPos(), InteractionHand.MAIN_HAND, slot, b.rotation.get().place, 0, true, true, true)) {
               placed = true;
               b.blocksPlaced++;
               b.placeTimer = b.placeDelay.get();
               b.count++;
               if (b.placementsPerTick.get() == 1) {
                  break;
               }
            }

            if (!it.hasNext()) {
               finishedPlacing = true;
            }
         }

         if (finishedPlacing || !placed) {
            b.setState(nextState);
         }
      }

      private int findSlot(HighwayBuilder b, Predicate<ItemStack> predicate, boolean hotbar) {
         for (int i = hotbar ? 0 : 9; i < (hotbar ? 9 : b.mc.player.getInventory().items.size()); i++) {
            if (predicate.test(b.mc.player.getInventory().getItem(i))) {
               return i;
            }
         }

         return -1;
      }

      private int findHotbarSlot(HighwayBuilder b, boolean replaceTools) {
         int thrashSlot = -1;
         int slotsWithBlocks = 0;
         int slotWithLeastBlocks = -1;
         int slotWithLeastBlocksCount = Integer.MAX_VALUE;

         for (int i = 0; i < 9; i++) {
            ItemStack itemStack = b.mc.player.getInventory().getItem(i);
            if (itemStack.isEmpty()) {
               return i;
            }

            if (replaceTools && AutoTool.isTool(itemStack)) {
               return i;
            }

            if (b.trashItems.get().contains(itemStack.getItem())) {
               thrashSlot = i;
            }

            if (itemStack.getItem() instanceof BlockItem blockItem && b.blocksToPlace.get().contains(blockItem.getBlock())) {
               slotsWithBlocks++;
               if (itemStack.getCount() < slotWithLeastBlocksCount) {
                  slotWithLeastBlocksCount = itemStack.getCount();
                  slotWithLeastBlocks = i;
               }
            }
         }

         if (thrashSlot != -1) {
            return thrashSlot;
         } else if (slotsWithBlocks > 1) {
            return slotWithLeastBlocks;
         } else {
            b.error("No empty space in hotbar.");
            return -1;
         }
      }

      private boolean hasItem(HighwayBuilder b, Item item) {
         for (int i = 0; i < b.mc.player.getInventory().items.size(); i++) {
            if (b.mc.player.getInventory().getItem(i).getItem() == item) {
               return true;
            }
         }

         return false;
      }

      protected int countItem(HighwayBuilder b, Predicate<ItemStack> predicate) {
         int count = 0;

         for (int i = 0; i < b.mc.player.getInventory().items.size(); i++) {
            ItemStack stack = b.mc.player.getInventory().getItem(i);
            if (predicate.test(stack)) {
               count += stack.getCount();
            }
         }

         return count;
      }

      protected int findAndMoveToHotbar(HighwayBuilder b, Predicate<ItemStack> predicate, boolean required) {
         int slot = this.findSlot(b, predicate, true);
         if (slot != -1) {
            return slot;
         } else {
            int hotbarSlot = this.findHotbarSlot(b, false);
            if (hotbarSlot == -1) {
               return -1;
            } else {
               slot = this.findSlot(b, predicate, false);
               if (slot == -1) {
                  if (required) {
                     b.error("Out of items.");
                  }

                  return -1;
               } else {
                  InvUtils.move().from(slot).toHotbar(hotbarSlot);
                  InvUtils.dropHand();
                  return hotbarSlot;
               }
            }
         }
      }

      protected int findAndMoveBestToolToHotbar(HighwayBuilder b, BlockState blockState, boolean noSilkTouch) {
         if (b.mc.player.isCreative()) {
            return b.mc.player.getInventory().selected;
         } else {
            double bestScore = -1.0;
            int bestSlot = -1;

            for (int i = 0; i < b.mc.player.getInventory().items.size(); i++) {
               double score = AutoTool.getScore(
                  b.mc.player.getInventory().getItem(i),
                  blockState,
                  false,
                  false,
                  AutoTool.EnchantPreference.None,
                  itemStack -> noSilkTouch && Utils.hasEnchantment(itemStack, Enchantments.SILK_TOUCH)
                        ? false
                        : !b.dontBreakTools.get() || itemStack.getMaxDamage() - itemStack.getDamageValue() > 1
               );
               if (score > bestScore) {
                  bestScore = score;
                  bestSlot = i;
               }
            }

            if (bestSlot == -1) {
               return b.mc.player.getInventory().selected;
            } else {
               if (b.mc.player.getInventory().getItem(bestSlot).getItem() instanceof PickaxeItem) {
                  int count = this.countItem(b, stack -> stack.getItem() instanceof PickaxeItem);
                  if (count <= b.savePickaxes.get()) {
                     b.error("Found less than the selected amount of pickaxes required: " + count + "/" + (b.savePickaxes.get() + 1));
                     return -1;
                  }
               }

               if (bestSlot < 9) {
                  return bestSlot;
               } else {
                  int hotbarSlot = this.findHotbarSlot(b, true);
                  if (hotbarSlot == -1) {
                     return -1;
                  } else {
                     InvUtils.move().from(bestSlot).toHotbar(hotbarSlot);
                     InvUtils.dropHand();
                     return hotbarSlot;
                  }
               }
            }
         }
      }

      protected int findBlocksToPlace(HighwayBuilder b) {
         int slot = this.findAndMoveToHotbar(b, itemStack -> {
            if (itemStack.getItem() instanceof BlockItem blockItem && b.blocksToPlace.get().contains(blockItem.getBlock())) {
               return true;
            }

            return false;
         }, false);
         if (slot != -1) {
            return slot;
         } else {
            if (b.mineEnderChests.get()
               && this.hasItem(b, Items.ENDER_CHEST)
               && this.countItem(b, stack -> stack.getItem().equals(Items.ENDER_CHEST)) > b.saveEchests.get()) {
               b.setState(MineEnderChests);
            } else {
               b.error("Out of blocks to place.");
            }

            return -1;
         }
      }

      protected int findBlocksToPlacePrioritizeTrash(HighwayBuilder b) {
         int slot = this.findAndMoveToHotbar(
            b, itemStack -> !(itemStack.getItem() instanceof BlockItem) ? false : b.trashItems.get().contains(itemStack.getItem()), false
         );
         return slot != -1 ? slot : this.findBlocksToPlace(b);
      }
   }

   private class StraightBlockPosProvider implements HighwayBuilder.IBlockPosProvider {
      private final MBlockPos pos = new MBlockPos();
      private final MBlockPos pos2 = new MBlockPos();

      @Override
      public HighwayBuilder.MBPIterator getFront() {
         this.pos.set(HighwayBuilder.this.mc.player).offset(HighwayBuilder.this.dir).offset(HighwayBuilder.this.leftDir, HighwayBuilder.this.getWidthLeft());
         return new HighwayBuilder.MBPIterator() {
            private int w;
            private int y;
            private int pw;
            private int py;

            @Override
            public boolean hasNext() {
               return this.w < HighwayBuilder.this.width.get() && this.y < HighwayBuilder.this.height.get();
            }

            public MBlockPos next() {
               StraightBlockPosProvider.this.pos2.set(StraightBlockPosProvider.this.pos).offset(HighwayBuilder.this.rightDir, this.w).add(0, this.y, 0);
               this.w++;
               if (this.w >= HighwayBuilder.this.width.get()) {
                  this.w = 0;
                  this.y++;
               }

               return StraightBlockPosProvider.this.pos2;
            }

            @Override
            public void save() {
               this.pw = this.w;
               this.py = this.y;
               this.w = this.y = 0;
            }

            @Override
            public void restore() {
               this.w = this.pw;
               this.y = this.py;
            }
         };
      }

      @Override
      public HighwayBuilder.MBPIterator getFloor() {
         this.pos
            .set(HighwayBuilder.this.mc.player)
            .offset(HighwayBuilder.this.dir)
            .offset(HighwayBuilder.this.leftDir, HighwayBuilder.this.getWidthLeft())
            .add(0, -1, 0);
         return new HighwayBuilder.MBPIterator() {
            private int w;
            private int pw;

            @Override
            public boolean hasNext() {
               return this.w < HighwayBuilder.this.width.get();
            }

            public MBlockPos next() {
               return StraightBlockPosProvider.this.pos2.set(StraightBlockPosProvider.this.pos).offset(HighwayBuilder.this.rightDir, this.w++);
            }

            @Override
            public void save() {
               this.pw = this.w;
               this.w = 0;
            }

            @Override
            public void restore() {
               this.w = this.pw;
            }
         };
      }

      @Override
      public HighwayBuilder.MBPIterator getRailings(boolean mine) {
         final boolean mineAll = mine && HighwayBuilder.this.mineAboveRailings.get();
         this.pos.set(HighwayBuilder.this.mc.player).offset(HighwayBuilder.this.dir);
         return new HighwayBuilder.MBPIterator() {
            private int i;
            private int y;
            private int pi;
            private int py;

            @Override
            public boolean hasNext() {
               return this.i < 2 && this.y < (mineAll ? HighwayBuilder.this.height.get() : 1);
            }

            public MBlockPos next() {
               if (this.i == 0) {
                  StraightBlockPosProvider.this.pos2
                     .set(StraightBlockPosProvider.this.pos)
                     .offset(HighwayBuilder.this.leftDir, HighwayBuilder.this.getWidthLeft() + 1)
                     .add(0, this.y, 0);
               } else {
                  StraightBlockPosProvider.this.pos2
                     .set(StraightBlockPosProvider.this.pos)
                     .offset(HighwayBuilder.this.rightDir, HighwayBuilder.this.getWidthRight() + 1)
                     .add(0, this.y, 0);
               }

               this.y++;
               if (this.y >= (mineAll ? HighwayBuilder.this.height.get() : 1)) {
                  this.y = 0;
                  this.i++;
               }

               return StraightBlockPosProvider.this.pos2;
            }

            @Override
            public void save() {
               this.pi = this.i;
               this.py = this.y;
               this.i = this.y = 0;
            }

            @Override
            public void restore() {
               this.i = this.pi;
               this.y = this.py;
            }
         };
      }

      @Override
      public HighwayBuilder.MBPIterator getLiquids() {
         this.pos
            .set(HighwayBuilder.this.mc.player)
            .offset(HighwayBuilder.this.dir, 2)
            .offset(
               HighwayBuilder.this.leftDir,
               HighwayBuilder.this.getWidthLeft() + (HighwayBuilder.this.railings.get() && HighwayBuilder.this.mineAboveRailings.get() ? 2 : 1)
            );
         return new HighwayBuilder.MBPIterator() {
            private int w;
            private int y;
            private int pw;
            private int py;

            private int getWidth() {
               return HighwayBuilder.this.width.get() + (HighwayBuilder.this.railings.get() && HighwayBuilder.this.mineAboveRailings.get() ? 2 : 0);
            }

            @Override
            public boolean hasNext() {
               return this.w < this.getWidth() + 2 && this.y < HighwayBuilder.this.height.get() + 1;
            }

            public MBlockPos next() {
               StraightBlockPosProvider.this.pos2.set(StraightBlockPosProvider.this.pos).offset(HighwayBuilder.this.rightDir, this.w).add(0, this.y, 0);
               this.w++;
               if (this.w >= this.getWidth() + 2) {
                  this.w = 0;
                  this.y++;
               }

               return StraightBlockPosProvider.this.pos2;
            }

            @Override
            public void save() {
               this.pw = this.w;
               this.py = this.y;
               this.w = this.y = 0;
            }

            @Override
            public void restore() {
               this.w = this.pw;
               this.y = this.py;
            }
         };
      }

      @Override
      public HighwayBuilder.MBPIterator getEChestBlockade(boolean mine) {
         return new HighwayBuilder.MBPIterator() {
            private int i = mine ? -1 : 0;
            private int y;
            private int pi;
            private int py;

            private MBlockPos get(int i) {
               StraightBlockPosProvider.this.pos.set(HighwayBuilder.this.mc.player).offset(HighwayBuilder.this.dir.opposite());

               return switch (i) {
                  case -1 -> StraightBlockPosProvider.this.pos;
                  default -> StraightBlockPosProvider.this.pos.offset(HighwayBuilder.this.dir.opposite());
                  case 1 -> StraightBlockPosProvider.this.pos.offset(HighwayBuilder.this.leftDir);
                  case 2 -> StraightBlockPosProvider.this.pos.offset(HighwayBuilder.this.rightDir);
                  case 3 -> StraightBlockPosProvider.this.pos.offset(HighwayBuilder.this.dir, 2);
               };
            }

            @Override
            public boolean hasNext() {
               return this.i < 4 && this.y < 2;
            }

            public MBlockPos next() {
               if (HighwayBuilder.this.width.get() == 1 && HighwayBuilder.this.railings.get() && this.i > 0 && this.y == 0) {
                  this.y++;
               }

               MBlockPos pos = this.get(this.i).add(0, this.y, 0);
               this.y++;
               if (this.y > 1) {
                  this.y = 0;
                  this.i++;
               }

               return pos;
            }

            @Override
            public void save() {
               this.pi = this.i;
               this.py = this.y;
               this.i = this.y = 0;
            }

            @Override
            public void restore() {
               this.i = this.pi;
               this.y = this.py;
            }
         };
      }
   }
}
