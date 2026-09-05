package meteordevelopment.meteorclient.systems.modules.combat;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.AbstractBlockAccessor;
import meteordevelopment.meteorclient.mixininterface.IBox;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.meteorclient.utils.world.Dir;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class HoleFiller extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgSmart = this.settings.createGroup("Smart");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<List<Block>> blocks = this.sgGeneral
      .add(
         new BlockListSetting.Builder()
            .name("blocks")
            .description("Which blocks can be used to fill holes.")
            .defaultValue(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.NETHERITE_BLOCK, Blocks.RESPAWN_ANCHOR, Blocks.COBWEB)
            .build()
      );
   private final Setting<Integer> searchRadius = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("search-radius")
            .description("Horizontal radius in which to search for holes.")
            .defaultValue(Integer.valueOf(5))
            .min(0)
            .sliderMax(6)
            .build()
      );
   private final Setting<Double> placeRange = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("place-range")
            .description("How far away from the player you can place a block.")
            .defaultValue(4.5)
            .min(0.0)
            .sliderMax(6.0)
            .build()
      );
   private final Setting<Boolean> doubles = this.sgGeneral
      .add(new BoolSetting.Builder().name("doubles").description("Fills double holes.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<Boolean> rotate = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("rotate")
            .description("Automatically rotates towards the holes being filled.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   private final Setting<Integer> placeDelay = this.sgGeneral
      .add(new IntSetting.Builder().name("place-delay").description("The ticks delay between placement.").defaultValue(Integer.valueOf(1)).min(0).build());
   private final Setting<Integer> blocksPerTick = this.sgGeneral
      .add(
         new IntSetting.Builder().name("blocks-per-tick").description("How many blocks to place in one tick.").defaultValue(Integer.valueOf(3)).min(1).build()
      );
   private final Setting<Boolean> smart = this.sgSmart
      .add(
         new BoolSetting.Builder()
            .name("smart")
            .description("Take more factors into account before filling a hole.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   public final Setting<Keybind> forceFill = this.sgSmart
      .add(
         new KeybindSetting.Builder()
            .name("force-fill")
            .description("Fills all holes around you regardless of target checks.")
            .defaultValue(Keybind.none())
            .visible(this.smart::get)
            .build()
      );
   private final Setting<Boolean> predict = this.sgSmart
      .add(
         new BoolSetting.Builder()
            .name("predict")
            .description("Predict target movement to account for ping.")
            .defaultValue(Boolean.valueOf(true))
            .visible(this.smart::get)
            .build()
      );
   private final Setting<Boolean> ignoreSafe = this.sgSmart
      .add(
         new BoolSetting.Builder()
            .name("ignore-safe")
            .description("Ignore players in safe holes.")
            .defaultValue(Boolean.valueOf(true))
            .visible(this.smart::get)
            .build()
      );
   private final Setting<Boolean> onlyMoving = this.sgSmart
      .add(
         new BoolSetting.Builder()
            .name("only-moving")
            .description("Ignore players if they're standing still.")
            .defaultValue(Boolean.valueOf(true))
            .visible(this.smart::get)
            .build()
      );
   private final Setting<Double> targetRange = this.sgSmart
      .add(
         new DoubleSetting.Builder()
            .name("target-range")
            .description("How far away to target players.")
            .defaultValue(7.0)
            .min(0.0)
            .sliderMin(1.0)
            .sliderMax(10.0)
            .visible(this.smart::get)
            .build()
      );
   private final Setting<Double> feetRange = this.sgSmart
      .add(
         new DoubleSetting.Builder()
            .name("feet-range")
            .description("How far from a hole a player's feet must be to fill it.")
            .defaultValue(1.5)
            .min(0.0)
            .sliderMax(4.0)
            .visible(this.smart::get)
            .build()
      );
   private final Setting<Boolean> swing = this.sgRender
      .add(new BoolSetting.Builder().name("swing").description("Swing the player's hand when placing.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<Boolean> render = this.sgRender
      .add(new BoolSetting.Builder().name("render").description("Renders an overlay where blocks will be placed.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode"))
                     .description("How the shapes are rendered."))
                  .defaultValue(ShapeMode.Both))
               .visible(this.render::get))
            .build()
      );
   private final Setting<SettingColor> sideColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("side-color")
            .description("The side color of the target block rendering.")
            .defaultValue(new SettingColor(197, 137, 232, 10))
            .visible(() -> this.render.get() && this.shapeMode.get().sides())
            .build()
      );
   private final Setting<SettingColor> lineColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("line-color")
            .description("The line color of the target block rendering.")
            .defaultValue(new SettingColor(197, 137, 232))
            .visible(() -> this.render.get() && this.shapeMode.get().lines())
            .build()
      );
   private final Setting<SettingColor> nextSideColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("next-side-color")
            .description("The side color of the next block to be placed.")
            .defaultValue(new SettingColor(227, 196, 245, 10))
            .visible(() -> this.render.get() && this.shapeMode.get().sides())
            .build()
      );
   private final Setting<SettingColor> nextLineColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("next-line-color")
            .description("The line color of the next block to be placed.")
            .defaultValue(new SettingColor(227, 196, 245))
            .visible(() -> this.render.get() && this.shapeMode.get().lines())
            .build()
      );
   private final List<Player> targets = new ArrayList<>();
   private final List<HoleFiller.Hole> holes = new ArrayList<>();
   private final MutableBlockPos testPos = new MutableBlockPos();
   private final AABB box = new AABB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
   private int timer;

   public HoleFiller() {
      super(Categories.Combat, "hole-filler", "Fills holes with specified blocks.");
   }

   @Override
   public void onActivate() {
      this.timer = 0;
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.smart.get()) {
         this.setTargets();
      }

      this.holes.clear();
      FindItemResult block = InvUtils.findInHotbar(itemStack -> this.blocks.get().contains(Block.byItem(itemStack.getItem())));
      if (block.found()) {
         BlockIterator.register(this.searchRadius.get(), this.searchRadius.get(), (blockPos, blockState) -> {
            if (this.validHole(blockPos)) {
               int bedrock = 0;
               int obsidian = 0;
               Direction air = null;

               for (Direction direction : Direction.values()) {
                  if (direction != Direction.UP) {
                     BlockState state = this.mc.level.getBlockState(blockPos.relative(direction));
                     if (state.getBlock() == Blocks.BEDROCK) {
                        bedrock++;
                     } else if (state.getBlock() == Blocks.OBSIDIAN) {
                        obsidian++;
                     } else {
                        if (direction == Direction.DOWN) {
                           return;
                        }

                        if (this.validHole(blockPos.relative(direction)) && air == null) {
                           for (Direction dir : Direction.values()) {
                              if (dir != direction.getOpposite() && dir != Direction.UP) {
                                 BlockState blockState1 = this.mc.level.getBlockState(blockPos.relative(direction).relative(dir));
                                 if (blockState1.getBlock() == Blocks.BEDROCK) {
                                    bedrock++;
                                 } else {
                                    if (blockState1.getBlock() != Blocks.OBSIDIAN) {
                                       return;
                                    }

                                    obsidian++;
                                 }
                              }
                           }

                           air = direction;
                        }
                     }

                     if (obsidian + bedrock == 5 && air == null) {
                        this.holes.add(new HoleFiller.Hole(blockPos, (byte)0));
                     } else if (obsidian + bedrock == 8 && this.doubles.get() && air != null) {
                        this.holes.add(new HoleFiller.Hole(blockPos, Dir.get(air)));
                     }
                  }
               }
            }
         });
         BlockIterator.after(() -> {
            if (this.timer <= 0 && !this.holes.isEmpty()) {
               int bpt = 0;

               for (HoleFiller.Hole hole : this.holes) {
                  if (bpt < this.blocksPerTick.get() && BlockUtils.place(hole.blockPos, block, this.rotate.get(), 10, this.swing.get(), true)) {
                     bpt++;
                  }
               }

               this.timer = this.placeDelay.get();
            }
         });
         this.timer--;
      }
   }

   @EventHandler(
      priority = 100
   )
   private void onRender(Render3DEvent event) {
      if (this.render.get() && !this.holes.isEmpty()) {
         for (HoleFiller.Hole hole : this.holes) {
            boolean isNext = false;

            for (int i = 0; i < this.holes.size(); i++) {
               if (this.holes.get(i).equals(hole) && i < this.blocksPerTick.get()) {
                  isNext = true;
               }
            }

            Color side = isNext ? this.nextSideColor.get() : this.sideColor.get();
            Color line = isNext ? this.nextLineColor.get() : this.lineColor.get();
            event.renderer.box(hole.blockPos, side, line, this.shapeMode.get(), hole.exclude);
         }
      }
   }

   private boolean validHole(BlockPos pos) {
      this.testPos.set(pos);
      if (this.mc.player.blockPosition().equals(this.testPos)) {
         return false;
      } else if (this.distance(this.mc.player, this.testPos, false) > this.placeRange.get()) {
         return false;
      } else if (this.mc.level.getBlockState(this.testPos).getBlock() == Blocks.COBWEB) {
         return false;
      } else if (((AbstractBlockAccessor)this.mc.level.getBlockState(this.testPos).getBlock()).isCollidable()) {
         return false;
      } else {
         this.testPos.offset(0, 1, 0);
         if (((AbstractBlockAccessor)this.mc.level.getBlockState(this.testPos).getBlock()).isCollidable()) {
            return false;
         } else {
            this.testPos.offset(0, -1, 0);
            ((IBox)this.box).set(pos);
            if (!this.mc
               .level
               .getEntities((net.minecraft.world.entity.Entity) null, this.box, entity -> entity instanceof Player || entity instanceof PrimedTnt || entity instanceof EndCrystal)
               .isEmpty()) {
               return false;
            } else {
               return this.smart.get() && !this.forceFill.get().isPressed()
                  ? this.targets
                     .stream()
                     .anyMatch(target -> target.getY() > (double)this.testPos.getY() && this.distance(target, this.testPos, true) < this.feetRange.get())
                  : true;
            }
         }
      }
   }

   private void setTargets() {
      this.targets.clear();

      for (Player player : this.mc.level.players()) {
         if (player.distanceToSqr(this.mc.player) <= (this.targetRange.get() * this.targetRange.get())
            && !player.isCreative()
            && player != this.mc.player
            && !player.isDeadOrDying()
            && Friends.get().shouldAttack(player)
            && (!this.ignoreSafe.get() || !this.isSurrounded(player))
            && (!this.onlyMoving.get() || player.getX() - player.xo == 0.0 && player.getY() - player.yo == 0.0 && player.getZ() - player.zo == 0.0)) {
            this.targets.add(player);
         }
      }
   }

   private boolean isSurrounded(Player target) {
      for (Direction dir : Direction.values()) {
         if (dir != Direction.UP && dir != Direction.DOWN) {
            this.testPos.set(target.blockPosition().relative(dir));
            Block block = this.mc.level.getBlockState(this.testPos).getBlock();
            if (block != Blocks.OBSIDIAN
               && block != Blocks.BEDROCK
               && block != Blocks.RESPAWN_ANCHOR
               && block != Blocks.CRYING_OBSIDIAN
               && block != Blocks.NETHERITE_BLOCK) {
               return false;
            }
         }
      }

      return true;
   }

   private double distance(Player player, BlockPos pos, boolean feet) {
      Vec3 testVec = player.position();
      if (!feet) {
         testVec.add(0.0, (double)player.getEyeHeight(this.mc.player.getPose()), 0.0);
      } else if (this.predict.get()) {
         testVec.add(player.getX() - player.xo, player.getY() - player.yo, player.getZ() - player.zo);
      }

      double i = testVec.x - ((double)pos.getX() + 0.5);
      double j = testVec.y - ((double)pos.getY() + (feet ? 1.0 : 0.5));
      double k = testVec.z - ((double)pos.getZ() + 0.5);
      return Math.sqrt(i * i + j * j + k * k);
   }

   private static class Hole {
      private final MutableBlockPos blockPos = new MutableBlockPos();
      private final byte exclude;

      public Hole(BlockPos blockPos, byte exclude) {
         this.blockPos.set(blockPos);
         this.exclude = exclude;
      }
   }
}
