package meteordevelopment.meteorclient.systems.modules.render;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.AbstractBlockAccessor;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.Pool;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.Dir;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

public class HoleESP extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Integer> horizontalRadius = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("horizontal-radius")
            .description("Horizontal radius in which to search for holes.")
            .defaultValue(Integer.valueOf(10))
            .min(0)
            .sliderMax(32)
            .build()
      );
   private final Setting<Integer> verticalRadius = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("vertical-radius")
            .description("Vertical radius in which to search for holes.")
            .defaultValue(Integer.valueOf(5))
            .min(0)
            .sliderMax(32)
            .build()
      );
   private final Setting<Integer> holeHeight = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("min-height")
            .description("Minimum hole height required to be rendered.")
            .defaultValue(Integer.valueOf(3))
            .min(1)
            .sliderMin(1)
            .build()
      );
   private final Setting<Boolean> doubles = this.sgGeneral
      .add(
         new BoolSetting.Builder().name("doubles").description("Highlights double holes that can be stood across.").defaultValue(Boolean.valueOf(true)).build()
      );
   private final Setting<Boolean> ignoreOwn = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("ignore-own")
            .description("Ignores rendering the hole you are currently standing in.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   private final Setting<Boolean> webs = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("webs")
            .description("Whether to show holes that have webs inside of them.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode"))
                  .description("How the shapes are rendered."))
               .defaultValue(ShapeMode.Both))
            .build()
      );
   private final Setting<Double> height = this.sgRender
      .add(new DoubleSetting.Builder().name("height").description("The height of rendering.").defaultValue(0.2).min(0.0).build());
   private final Setting<Boolean> topQuad = this.sgRender
      .add(
         new BoolSetting.Builder().name("top-quad").description("Whether to render a quad at the top of the hole.").defaultValue(Boolean.valueOf(true)).build()
      );
   private final Setting<Boolean> bottomQuad = this.sgRender
      .add(
         new BoolSetting.Builder()
            .name("bottom-quad")
            .description("Whether to render a quad at the bottom of the hole.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   private final Setting<SettingColor> bedrockColorTop = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("bedrock-top")
            .description("The top color for holes that are completely bedrock.")
            .defaultValue(new SettingColor(100, 255, 0, 200))
            .build()
      );
   private final Setting<SettingColor> bedrockColorBottom = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("bedrock-bottom")
            .description("The bottom color for holes that are completely bedrock.")
            .defaultValue(new SettingColor(100, 255, 0, 0))
            .build()
      );
   private final Setting<SettingColor> obsidianColorTop = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("obsidian-top")
            .description("The top color for holes that are completely obsidian.")
            .defaultValue(new SettingColor(255, 0, 0, 200))
            .build()
      );
   private final Setting<SettingColor> obsidianColorBottom = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("obsidian-bottom")
            .description("The bottom color for holes that are completely obsidian.")
            .defaultValue(new SettingColor(255, 0, 0, 0))
            .build()
      );
   private final Setting<SettingColor> mixedColorTop = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("mixed-top")
            .description("The top color for holes that have mixed bedrock and obsidian.")
            .defaultValue(new SettingColor(255, 127, 0, 200))
            .build()
      );
   private final Setting<SettingColor> mixedColorBottom = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("mixed-bottom")
            .description("The bottom color for holes that have mixed bedrock and obsidian.")
            .defaultValue(new SettingColor(255, 127, 0, 0))
            .build()
      );
   private final Pool<HoleESP.Hole> holePool = new Pool<>(HoleESP.Hole::new);
   private final List<HoleESP.Hole> holes = new ArrayList<>();
   private final byte NULL = 0;

   public HoleESP() {
      super(Categories.Render, "hole-esp", "Displays holes that you will take less damage in.");
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      for (HoleESP.Hole hole : this.holes) {
         this.holePool.free(hole);
      }

      this.holes.clear();
      BlockIterator.register(
         this.horizontalRadius.get(),
         this.verticalRadius.get(),
         (blockPos, blockState) -> {
            if (this.validHole(blockPos)) {
               int bedrock = 0;
               int obsidian = 0;
               Direction air = null;

               for (Direction direction : Direction.values()) {
                  if (direction != Direction.UP) {
                     BlockPos offsetPos = blockPos.relative(direction);
                     BlockState state = this.mc.level.getBlockState(offsetPos);
                     if (state.getBlock() == Blocks.BEDROCK) {
                        bedrock++;
                     } else if (state.getBlock() == Blocks.OBSIDIAN) {
                        obsidian++;
                     } else {
                        if (direction == Direction.DOWN) {
                           return;
                        }

                        if (this.doubles.get() && air == null && this.validHole(offsetPos)) {
                           for (Direction dir : Direction.values()) {
                              if (dir != direction.getOpposite() && dir != Direction.UP) {
                                 BlockState blockState1 = this.mc.level.getBlockState(offsetPos.relative(dir));
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
                  }
               }

               if (obsidian + bedrock == 5 && air == null) {
                  this.holes
                     .add(
                        this.holePool
                           .get()
                           .set(
                              blockPos,
                              obsidian == 5 ? HoleESP.Hole.Type.Obsidian : (bedrock == 5 ? HoleESP.Hole.Type.Bedrock : HoleESP.Hole.Type.Mixed),
                              (byte)0
                           )
                     );
               } else if (obsidian + bedrock == 8 && this.doubles.get() && air != null) {
                  this.holes
                     .add(
                        this.holePool
                           .get()
                           .set(
                              blockPos,
                              obsidian == 8 ? HoleESP.Hole.Type.Obsidian : (bedrock == 8 ? HoleESP.Hole.Type.Bedrock : HoleESP.Hole.Type.Mixed),
                              Dir.get(air)
                           )
                     );
               }
            }
         }
      );
   }

   private boolean validHole(BlockPos pos) {
      if (this.ignoreOwn.get() && this.mc.player.blockPosition().equals(pos)) {
         return false;
      } else {
         LevelChunk chunk = this.mc.level.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
         Block block = chunk.getBlockState(pos).getBlock();
         if (!this.webs.get() && block == Blocks.COBWEB) {
            return false;
         } else if (((AbstractBlockAccessor)block).isCollidable()) {
            return false;
         } else {
            for (int i = 0; i < this.holeHeight.get(); i++) {
               if (((AbstractBlockAccessor)chunk.getBlockState(pos.above(i)).getBlock()).isCollidable()) {
                  return false;
               }
            }

            return true;
         }
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      for (HoleESP.Hole hole : this.holes) {
         hole.render(event.renderer, this.shapeMode.get(), this.height.get(), this.topQuad.get(), this.bottomQuad.get());
      }
   }

   private static class Hole {
      public MutableBlockPos blockPos = new MutableBlockPos();
      public byte exclude;
      public HoleESP.Hole.Type type;

      public HoleESP.Hole set(BlockPos blockPos, HoleESP.Hole.Type type, byte exclude) {
         this.blockPos.set(blockPos);
         this.exclude = exclude;
         this.type = type;
         return this;
      }

      public Color getTopColor() {
         return switch (this.type) {
            case Bedrock -> (SettingColor)Modules.get().get(HoleESP.class).bedrockColorTop.get();
            case Obsidian -> (SettingColor)Modules.get().get(HoleESP.class).obsidianColorTop.get();
            default -> (SettingColor)Modules.get().get(HoleESP.class).mixedColorTop.get();
         };
      }

      public Color getBottomColor() {
         return switch (this.type) {
            case Bedrock -> (SettingColor)Modules.get().get(HoleESP.class).bedrockColorBottom.get();
            case Obsidian -> (SettingColor)Modules.get().get(HoleESP.class).obsidianColorBottom.get();
            default -> (SettingColor)Modules.get().get(HoleESP.class).mixedColorBottom.get();
         };
      }

      public void render(Renderer3D renderer, ShapeMode mode, double height, boolean topQuad, boolean bottomQuad) {
         int x = this.blockPos.getX();
         int y = this.blockPos.getY();
         int z = this.blockPos.getZ();
         Color top = this.getTopColor();
         Color bottom = this.getBottomColor();
         int originalTopA = top.a;
         int originalBottompA = bottom.a;
         if (mode.lines()) {
            if (Dir.isNot(this.exclude, (byte)32) && Dir.isNot(this.exclude, (byte)8)) {
               renderer.line((double)x, (double)y, (double)z, (double)x, (double)y + height, (double)z, bottom, top);
            }

            if (Dir.isNot(this.exclude, (byte)32) && Dir.isNot(this.exclude, (byte)16)) {
               renderer.line((double)x, (double)y, (double)(z + 1), (double)x, (double)y + height, (double)(z + 1), bottom, top);
            }

            if (Dir.isNot(this.exclude, (byte)64) && Dir.isNot(this.exclude, (byte)8)) {
               renderer.line((double)(x + 1), (double)y, (double)z, (double)(x + 1), (double)y + height, (double)z, bottom, top);
            }

            if (Dir.isNot(this.exclude, (byte)64) && Dir.isNot(this.exclude, (byte)16)) {
               renderer.line((double)(x + 1), (double)y, (double)(z + 1), (double)(x + 1), (double)y + height, (double)(z + 1), bottom, top);
            }

            if (Dir.isNot(this.exclude, (byte)8)) {
               renderer.line((double)x, (double)y, (double)z, (double)(x + 1), (double)y, (double)z, bottom);
            }

            if (Dir.isNot(this.exclude, (byte)8)) {
               renderer.line((double)x, (double)y + height, (double)z, (double)(x + 1), (double)y + height, (double)z, top);
            }

            if (Dir.isNot(this.exclude, (byte)16)) {
               renderer.line((double)x, (double)y, (double)(z + 1), (double)(x + 1), (double)y, (double)(z + 1), bottom);
            }

            if (Dir.isNot(this.exclude, (byte)16)) {
               renderer.line((double)x, (double)y + height, (double)(z + 1), (double)(x + 1), (double)y + height, (double)(z + 1), top);
            }

            if (Dir.isNot(this.exclude, (byte)32)) {
               renderer.line((double)x, (double)y, (double)z, (double)x, (double)y, (double)(z + 1), bottom);
            }

            if (Dir.isNot(this.exclude, (byte)32)) {
               renderer.line((double)x, (double)y + height, (double)z, (double)x, (double)y + height, (double)(z + 1), top);
            }

            if (Dir.isNot(this.exclude, (byte)64)) {
               renderer.line((double)(x + 1), (double)y, (double)z, (double)(x + 1), (double)y, (double)(z + 1), bottom);
            }

            if (Dir.isNot(this.exclude, (byte)64)) {
               renderer.line((double)(x + 1), (double)y + height, (double)z, (double)(x + 1), (double)y + height, (double)(z + 1), top);
            }
         }

         if (mode.sides()) {
            top.a = originalTopA / 2;
            bottom.a = originalBottompA / 2;
            if (Dir.isNot(this.exclude, (byte)2) && topQuad) {
               renderer.quad(
                  (double)x,
                  (double)y + height,
                  (double)z,
                  (double)x,
                  (double)y + height,
                  (double)(z + 1),
                  (double)(x + 1),
                  (double)y + height,
                  (double)(z + 1),
                  (double)(x + 1),
                  (double)y + height,
                  (double)z,
                  top
               );
            }

            if (Dir.isNot(this.exclude, (byte)4) && bottomQuad) {
               renderer.quad(
                  (double)x,
                  (double)y,
                  (double)z,
                  (double)x,
                  (double)y,
                  (double)(z + 1),
                  (double)(x + 1),
                  (double)y,
                  (double)(z + 1),
                  (double)(x + 1),
                  (double)y,
                  (double)z,
                  bottom
               );
            }

            if (Dir.isNot(this.exclude, (byte)8)) {
               renderer.gradientQuadVertical((double)x, (double)y, (double)z, (double)(x + 1), (double)y + height, (double)z, top, bottom);
            }

            if (Dir.isNot(this.exclude, (byte)16)) {
               renderer.gradientQuadVertical((double)x, (double)y, (double)(z + 1), (double)(x + 1), (double)y + height, (double)(z + 1), top, bottom);
            }

            if (Dir.isNot(this.exclude, (byte)32)) {
               renderer.gradientQuadVertical((double)x, (double)y, (double)z, (double)x, (double)y + height, (double)(z + 1), top, bottom);
            }

            if (Dir.isNot(this.exclude, (byte)64)) {
               renderer.gradientQuadVertical((double)(x + 1), (double)y, (double)z, (double)(x + 1), (double)y + height, (double)(z + 1), top, bottom);
            }

            top.a = originalTopA;
            bottom.a = originalBottompA;
         }
      }

      public static enum Type {
         Bedrock,
         Obsidian,
         Mixed;
      }
   }
}
