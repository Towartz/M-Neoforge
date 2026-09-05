package meteordevelopment.meteorclient.systems.modules.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

public class LiquidFiller extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgWhitelist = this.settings.createGroup("Whitelist");
   private final Setting<LiquidFiller.PlaceIn> placeInLiquids = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("place-in"))
                  .description("What type of liquids to place in."))
               .defaultValue(LiquidFiller.PlaceIn.Both))
            .build()
      );
   private final Setting<LiquidFiller.Shape> shape = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape"))
                  .description("The shape of placing algorithm."))
               .defaultValue(LiquidFiller.Shape.Sphere))
            .build()
      );
   private final Setting<Double> range = this.sgGeneral
      .add(new DoubleSetting.Builder().name("range").description("The place range.").defaultValue(4.0).min(0.0).build());
   private final Setting<Integer> delay = this.sgGeneral
      .add(new IntSetting.Builder().name("delay").description("Delay between actions in ticks.").defaultValue(Integer.valueOf(0)).min(0).build());
   private final Setting<Integer> maxBlocksPerTick = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("max-blocks-per-tick")
            .description("Maximum blocks to try to place per tick.")
            .defaultValue(Integer.valueOf(1))
            .min(1)
            .sliderRange(1, 10)
            .build()
      );
   private final Setting<LiquidFiller.SortMode> sortMode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("sort-mode"))
                  .description("The blocks you want to place first."))
               .defaultValue(LiquidFiller.SortMode.Closest))
            .build()
      );
   private final Setting<Boolean> rotate = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("rotate")
            .description("Automatically rotates towards the space targeted for filling.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   private final Setting<LiquidFiller.ListMode> listMode = this.sgWhitelist
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("list-mode")).description("Selection mode."))
               .defaultValue(LiquidFiller.ListMode.Whitelist))
            .build()
      );
   private final Setting<List<Block>> whitelist = this.sgWhitelist
      .add(
         new BlockListSetting.Builder()
            .name("whitelist")
            .description("The allowed blocks that it will use to fill up the liquid.")
            .defaultValue(Blocks.DIRT, Blocks.COBBLESTONE, Blocks.STONE, Blocks.NETHERRACK, Blocks.DIORITE, Blocks.GRANITE, Blocks.ANDESITE)
            .visible(() -> this.listMode.get() == LiquidFiller.ListMode.Whitelist)
            .build()
      );
   private final Setting<List<Block>> blacklist = this.sgWhitelist
      .add(
         new BlockListSetting.Builder()
            .name("blacklist")
            .description("The denied blocks that it not will use to fill up the liquid.")
            .visible(() -> this.listMode.get() == LiquidFiller.ListMode.Blacklist)
            .build()
      );
   private final List<MutableBlockPos> blocks = new ArrayList<>();
   private int timer;

   public LiquidFiller() {
      super(Categories.World, "liquid-filler", "Places blocks inside of liquid source blocks within range of you.");
   }

   @Override
   public void onActivate() {
      this.timer = 0;
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.timer < this.delay.get()) {
         this.timer++;
      } else {
         this.timer = 0;
         double pX = this.mc.player.getX();
         double pY = this.mc.player.getY();
         double pZ = this.mc.player.getZ();
         double r = this.range.get();
         double rangeSq = r * r;
         if (this.shape.get() == LiquidFiller.Shape.UniformCube) {
            this.range.set((double)Math.round(this.range.get()));
         }

         FindItemResult item;
         if (this.listMode.get() == LiquidFiller.ListMode.Whitelist) {
            item = InvUtils.findInHotbar(
               itemStack -> itemStack.getItem() instanceof BlockItem && this.whitelist.get().contains(Block.byItem(itemStack.getItem()))
            );
         } else {
            item = InvUtils.findInHotbar(
               itemStack -> itemStack.getItem() instanceof BlockItem && !this.blacklist.get().contains(Block.byItem(itemStack.getItem()))
            );
         }

         if (item.found()) {
            BlockIterator.register(
               (int)Math.ceil(this.range.get() + 1.0),
               (int)Math.ceil(this.range.get()),
               (blockPos, blockState) -> {
                  boolean toofarSphere = Utils.squaredDistance(
                        pX, pY, pZ, (double)blockPos.getX() + 0.5, (double)blockPos.getY() + 0.5, (double)blockPos.getZ() + 0.5
                     )
                     > rangeSq;
                  boolean toofarUniformCube = maxDist(
                        Math.floor(pX), Math.floor(pY), Math.floor(pZ), (double)blockPos.getX(), (double)blockPos.getY(), (double)blockPos.getZ()
                     )
                     >= this.range.get();
                  if ((!toofarSphere || this.shape.get() != LiquidFiller.Shape.Sphere)
                     && (!toofarUniformCube || this.shape.get() != LiquidFiller.Shape.UniformCube)) {
                     Fluid fluid = blockState.getFluidState().getType();
                     if ((this.placeInLiquids.get() != LiquidFiller.PlaceIn.Both || fluid == Fluids.WATER || fluid == Fluids.LAVA)
                        && (this.placeInLiquids.get() != LiquidFiller.PlaceIn.Water || fluid == Fluids.WATER)
                        && (this.placeInLiquids.get() != LiquidFiller.PlaceIn.Lava || fluid == Fluids.LAVA)) {
                        if (BlockUtils.canPlace(blockPos)) {
                           this.blocks.add(blockPos.mutable());
                        }
                     }
                  }
               }
            );
            BlockIterator.after(
               () -> {
                  if (this.sortMode.get() == LiquidFiller.SortMode.TopDown || this.sortMode.get() == LiquidFiller.SortMode.BottomUp) {
                     this.blocks
                        .sort(Comparator.comparingDouble(value -> (double)(value.getY() * (this.sortMode.get() == LiquidFiller.SortMode.BottomUp ? 1 : -1))));
                  } else if (this.sortMode.get() != LiquidFiller.SortMode.None) {
                     this.blocks
                        .sort(
                           Comparator.comparingDouble(
                              value -> Utils.squaredDistance(pX, pY, pZ, (double)value.getX() + 0.5, (double)value.getY() + 0.5, (double)value.getZ() + 0.5)
                                    * (double)(this.sortMode.get() == LiquidFiller.SortMode.Closest ? 1 : -1)
                           )
                        );
                  }

                  int count = 0;

                  for (BlockPos pos : this.blocks) {
                     if (count >= this.maxBlocksPerTick.get()) {
                        break;
                     }

                     BlockUtils.place(pos, item, this.rotate.get(), 0, true);
                     count++;
                  }

                  this.blocks.clear();
               }
            );
         }
      }
   }

   private static double maxDist(double x1, double y1, double z1, double x2, double y2, double z2) {
      double dX = Math.ceil(Math.abs(x2 - x1));
      double dY = Math.ceil(Math.abs(y2 - y1));
      double dZ = Math.ceil(Math.abs(z2 - z1));
      return Math.max(Math.max(dX, dY), dZ);
   }

   public static enum ListMode {
      Whitelist,
      Blacklist;
   }

   public static enum PlaceIn {
      Both,
      Water,
      Lava;
   }

   public static enum Shape {
      Sphere,
      UniformCube;
   }

   public static enum SortMode {
      None,
      Closest,
      Furthest,
      TopDown,
      BottomUp;
   }
}
