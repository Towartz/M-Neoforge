package meteordevelopment.meteorclient.systems.modules.world;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Pool;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.TripWireBlock;

public class SpawnProofer extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> range = this.sgGeneral
      .add(new IntSetting.Builder().name("range").description("Range for block placement and rendering").defaultValue(Integer.valueOf(3)).min(0).build());
   private final Setting<List<Block>> blocks = this.sgGeneral
      .add(
         new BlockListSetting.Builder()
            .name("blocks")
            .description("Block to use for spawn proofing")
            .defaultValue(Blocks.TORCH, Blocks.STONE_BUTTON, Blocks.STONE_SLAB)
            .filter(this::filterBlocks)
            .build()
      );
   private final Setting<Integer> delay = this.sgGeneral
      .add(new IntSetting.Builder().name("delay").description("Delay in ticks between placing blocks").defaultValue(Integer.valueOf(0)).min(0).build());
   private final Setting<Boolean> rotate = this.sgGeneral
      .add(new BoolSetting.Builder().name("rotate").description("Rotates towards the blocks being placed.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<SpawnProofer.Mode> mode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("mode"))
                  .description("Which spawn types should be spawn proofed."))
               .defaultValue(SpawnProofer.Mode.Both))
            .build()
      );
   private final Setting<Boolean> newMobSpawnLightLevel = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("new-mob-spawn-light-level")
            .description("Use the new (1.18+) mob spawn behavior")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   private final Pool<MutableBlockPos> spawnPool = new Pool<>(MutableBlockPos::new);
   private final List<MutableBlockPos> spawns = new ArrayList<>();
   private int ticksWaited;

   public SpawnProofer() {
      super(Categories.World, "spawn-proofer", "Automatically spawnproofs unlit areas.");
   }

   @EventHandler
   private void onTickPre(TickEvent.Pre event) {
      if (this.delay.get() == 0 || this.ticksWaited >= this.delay.get() - 1) {
         boolean foundBlock = InvUtils.testInHotbar(itemStack -> this.blocks.get().contains(Block.byItem(itemStack.getItem())));
         if (!foundBlock) {
            this.error("Found none of the chosen blocks in hotbar", new Object[0]);
            this.toggle();
         } else {
            for (MutableBlockPos blockPos : this.spawns) {
               this.spawnPool.free(blockPos);
            }

            this.spawns.clear();
            int lightLevel = this.newMobSpawnLightLevel.get() ? 0 : 7;
            BlockIterator.register(
               this.range.get(),
               this.range.get(),
               (blockPosx, blockState) -> {
                  BlockUtils.MobSpawn spawn = BlockUtils.isValidMobSpawn(blockPosx, blockState, lightLevel);
                  if (spawn == BlockUtils.MobSpawn.Always && (this.mode.get() == SpawnProofer.Mode.Always || this.mode.get() == SpawnProofer.Mode.Both)
                     || spawn == BlockUtils.MobSpawn.Potential && (this.mode.get() == SpawnProofer.Mode.Potential || this.mode.get() == SpawnProofer.Mode.Both)
                     )
                   {
                     this.spawns.add(this.spawnPool.get().set(blockPosx));
                  }
               }
            );
         }
      }
   }

   @EventHandler
   private void onTickPost(TickEvent.Post event) {
      if (this.delay.get() != 0 && this.ticksWaited < this.delay.get() - 1) {
         this.ticksWaited++;
      } else if (!this.spawns.isEmpty()) {
         FindItemResult block = InvUtils.findInHotbar(itemStack -> this.blocks.get().contains(Block.byItem(itemStack.getItem())));
         if (!block.found()) {
            this.error("Found none of the chosen blocks in hotbar", new Object[0]);
            this.toggle();
         } else {
            if (this.delay.get() == 0) {
               for (BlockPos blockPos : this.spawns) {
                  BlockUtils.place(blockPos, block, this.rotate.get(), -50, false);
               }
            } else if (this.isLightSource(Block.byItem(this.mc.player.getInventory().getItem(block.slot()).getItem()))) {
               int lowestLightLevel = 16;
               MutableBlockPos selectedBlockPos = this.spawns.getFirst();

               for (BlockPos blockPos : this.spawns) {
                  int lightLevel = this.mc.level.getMaxLocalRawBrightness(blockPos);
                  if (lightLevel < lowestLightLevel) {
                     lowestLightLevel = lightLevel;
                     selectedBlockPos.set(blockPos);
                  }
               }

               BlockUtils.place(selectedBlockPos, block, this.rotate.get(), -50, false);
            } else {
               BlockUtils.place((BlockPos)this.spawns.getFirst(), block, this.rotate.get(), -50, false);
            }

            this.ticksWaited = 0;
         }
      }
   }

   private boolean filterBlocks(Block block) {
      return this.isNonOpaqueBlock(block) || this.isLightSource(block);
   }

   private boolean isNonOpaqueBlock(Block block) {
      return block instanceof ButtonBlock
         || block instanceof SlabBlock
         || block instanceof BasePressurePlateBlock
         || block instanceof TransparentBlock
         || block instanceof TripWireBlock
         || block instanceof CarpetBlock
         || block instanceof LeverBlock
         || block instanceof DiodeBlock
         || block instanceof BaseRailBlock;
   }

   private boolean isLightSource(Block block) {
      return block.defaultBlockState().getLightEmission() > 0;
   }

   public static enum Mode {
      Always,
      Potential,
      Both,
      None;
   }
}
