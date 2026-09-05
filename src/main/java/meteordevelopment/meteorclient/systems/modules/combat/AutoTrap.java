package meteordevelopment.meteorclient.systems.modules.combat;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class AutoTrap extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<List<Block>> blocks = this.sgGeneral
      .add(new BlockListSetting.Builder().name("whitelist").description("Which blocks to use.").defaultValue(Blocks.OBSIDIAN, Blocks.NETHERITE_BLOCK).build());
   private final Setting<Integer> range = this.sgGeneral
      .add(new IntSetting.Builder().name("target-range").description("The range players can be targeted.").defaultValue(Integer.valueOf(4)).build());
   private final Setting<SortPriority> priority = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("target-priority"))
                  .description("How to select the player to target."))
               .defaultValue(SortPriority.LowestHealth))
            .build()
      );
   private final Setting<Integer> delay = this.sgGeneral
      .add(new IntSetting.Builder().name("place-delay").description("How many ticks between block placements.").defaultValue(Integer.valueOf(1)).build());
   private final Setting<AutoTrap.TopMode> topPlacement = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("top-blocks"))
                  .description("Which blocks to place on the top half of the target."))
               .defaultValue(AutoTrap.TopMode.Full))
            .build()
      );
   private final Setting<AutoTrap.BottomMode> bottomPlacement = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("bottom-blocks"))
                  .description("Which blocks to place on the bottom half of the target."))
               .defaultValue(AutoTrap.BottomMode.Platform))
            .build()
      );
   private final Setting<Boolean> selfToggle = this.sgGeneral
      .add(new BoolSetting.Builder().name("self-toggle").description("Turns off after placing all blocks.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<Boolean> rotate = this.sgGeneral
      .add(new BoolSetting.Builder().name("rotate").description("Rotates towards blocks when placing.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<Boolean> render = this.sgRender
      .add(new BoolSetting.Builder().name("render").description("Renders an overlay where blocks will be placed.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode"))
                  .description("How the shapes are rendered."))
               .defaultValue(ShapeMode.Both))
            .build()
      );
   private final Setting<SettingColor> sideColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("side-color")
            .description("The side color of the target block rendering.")
            .defaultValue(new SettingColor(197, 137, 232, 10))
            .build()
      );
   private final Setting<SettingColor> lineColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("line-color")
            .description("The line color of the target block rendering.")
            .defaultValue(new SettingColor(197, 137, 232))
            .build()
      );
   private final Setting<SettingColor> nextSideColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("next-side-color")
            .description("The side color of the next block to be placed.")
            .defaultValue(new SettingColor(227, 196, 245, 10))
            .build()
      );
   private final Setting<SettingColor> nextLineColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("next-line-color")
            .description("The line color of the next block to be placed.")
            .defaultValue(new SettingColor(227, 196, 245))
            .build()
      );
   private final List<BlockPos> placePositions = new ArrayList<>();
   private Player target;
   private boolean placed;
   private int timer;

   public AutoTrap() {
      super(Categories.Combat, "auto-trap", "Traps people in a box to prevent them from moving.");
   }

   @Override
   public void onActivate() {
      this.target = null;
      this.placePositions.clear();
      this.timer = 0;
      this.placed = false;
   }

   @Override
   public void onDeactivate() {
      this.placePositions.clear();
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.selfToggle.get() && this.placed && this.placePositions.isEmpty()) {
         this.placed = false;
         this.toggle();
      } else {
         for (Block currentBlock : this.blocks.get()) {
            FindItemResult itemResult = InvUtils.findInHotbar(currentBlock.asItem());
            if (itemResult.isHotbar() || itemResult.isOffhand()) {
               if (TargetUtils.isBadTarget(this.target, (double)this.range.get().intValue())) {
                  this.target = TargetUtils.getPlayerTarget((double)this.range.get().intValue(), this.priority.get());
                  if (TargetUtils.isBadTarget(this.target, (double)this.range.get().intValue())) {
                     return;
                  }
               }

               this.fillPlaceArray(this.target);
               if (this.timer >= this.delay.get() && !this.placePositions.isEmpty()) {
                  BlockPos blockPos = this.placePositions.getLast();
                  if (BlockUtils.place(blockPos, itemResult, this.rotate.get(), 50, true)) {
                     this.placePositions.remove(blockPos);
                     this.placed = true;
                  }

                  this.timer = 0;
               } else {
                  this.timer++;
               }

               return;
            }

            this.placePositions.clear();
            this.placed = false;
         }
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (this.render.get() && !this.placePositions.isEmpty()) {
         for (BlockPos pos : this.placePositions) {
            boolean isFirst = pos.equals(this.placePositions.getLast());
            Color side = isFirst ? this.nextSideColor.get() : this.sideColor.get();
            Color line = isFirst ? this.nextLineColor.get() : this.lineColor.get();
            event.renderer.box(pos, side, line, this.shapeMode.get(), 0);
         }
      }
   }

   private void fillPlaceArray(Player target) {
      this.placePositions.clear();
      BlockPos targetPos = target.blockPosition();
      switch ((AutoTrap.TopMode)this.topPlacement.get()) {
         case Full:
            this.add(targetPos.offset(0, 2, 0));
            this.add(targetPos.offset(1, 1, 0));
            this.add(targetPos.offset(-1, 1, 0));
            this.add(targetPos.offset(0, 1, 1));
            this.add(targetPos.offset(0, 1, -1));
            break;
         case Top:
            this.add(targetPos.offset(0, 2, 0));
            break;
         case Face:
            this.add(targetPos.offset(1, 1, 0));
            this.add(targetPos.offset(-1, 1, 0));
            this.add(targetPos.offset(0, 1, 1));
            this.add(targetPos.offset(0, 1, -1));
      }

      switch ((AutoTrap.BottomMode)this.bottomPlacement.get()) {
         case Single:
            this.add(targetPos.offset(0, -1, 0));
            break;
         case Platform:
            this.add(targetPos.offset(0, -1, 0));
            this.add(targetPos.offset(1, -1, 0));
            this.add(targetPos.offset(-1, -1, 0));
            this.add(targetPos.offset(0, -1, 1));
            this.add(targetPos.offset(0, -1, -1));
            break;
         case Full:
            this.add(targetPos.offset(1, 0, 0));
            this.add(targetPos.offset(-1, 0, 0));
            this.add(targetPos.offset(0, 0, -1));
            this.add(targetPos.offset(0, 0, 1));
      }
   }

   private void add(BlockPos blockPos) {
      if (!this.placePositions.contains(blockPos) && BlockUtils.canPlace(blockPos)) {
         this.placePositions.add(blockPos);
      }
   }

   @Override
   public String getInfoString() {
      return EntityUtils.getName(this.target);
   }

   public static enum BottomMode {
      Single,
      Platform,
      Full,
      None;
   }

   public static enum TopMode {
      Full,
      Top,
      Face,
      None;
   }
}
