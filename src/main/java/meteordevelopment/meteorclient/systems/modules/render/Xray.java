package meteordevelopment.meteorclient.systems.modules.render;

import java.util.List;
import meteordevelopment.meteorclient.MixinPlugin;
import meteordevelopment.meteorclient.events.render.RenderBlockEntityEvent;
import meteordevelopment.meteorclient.events.world.AmbientOcclusionEvent;
import meteordevelopment.meteorclient.events.world.ChunkOcclusionEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.meteorclient.utils.world.OreDiscovery;
import meteordevelopment.orbit.EventHandler;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;

public class Xray extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   public static final List<Block> ORES = List.of(
      Blocks.COAL_ORE,
      Blocks.DEEPSLATE_COAL_ORE,
      Blocks.IRON_ORE,
      Blocks.DEEPSLATE_IRON_ORE,
      Blocks.GOLD_ORE,
      Blocks.DEEPSLATE_GOLD_ORE,
      Blocks.LAPIS_ORE,
      Blocks.DEEPSLATE_LAPIS_ORE,
      Blocks.REDSTONE_ORE,
      Blocks.DEEPSLATE_REDSTONE_ORE,
      Blocks.DIAMOND_ORE,
      Blocks.DEEPSLATE_DIAMOND_ORE,
      Blocks.EMERALD_ORE,
      Blocks.DEEPSLATE_EMERALD_ORE,
      Blocks.COPPER_ORE,
      Blocks.DEEPSLATE_COPPER_ORE,
      Blocks.NETHER_GOLD_ORE,
      Blocks.NETHER_QUARTZ_ORE,
      Blocks.ANCIENT_DEBRIS
   );
   private final Setting<List<Block>> blocks = this.sgGeneral
      .add(new BlockListSetting.Builder().name("whitelist").description("Which blocks to show x-rayed.").defaultValue(ORES).onChanged(v -> {
         if (this.isActive()) {
            this.mc.levelRenderer.allChanged();
         }
      }).build());
   public final Setting<Integer> opacity = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("opacity")
            .description("The opacity for all other blocks.")
            .defaultValue(Integer.valueOf(25))
            .range(0, 255)
            .sliderMax(255)
            .onChanged(onChanged -> {
               if (this.isActive()) {
                  this.mc.levelRenderer.allChanged();
               }
            })
            .build()
      );
   private final Setting<Boolean> autoDiscover = this.sgGeneral
      .add(new BoolSetting.Builder()
         .name("auto-discover")
         .description("Automatically discovers all modded ores from installed mods.")
         .defaultValue(true)
         .onChanged(v -> {
            if (v) {
               OreDiscovery.applyTo(this.blocks.get());
               if (this.isActive()) {
                  this.mc.levelRenderer.allChanged();
               }
            }
         })
         .build()
      );
   private final Setting<Boolean> exposedOnly = this.sgGeneral
      .add(new BoolSetting.Builder().name("exposed-only").description("Show only exposed ores.").defaultValue(Boolean.valueOf(false)).onChanged(onChanged -> {
         if (this.isActive()) {
            this.mc.levelRenderer.allChanged();
         }
      }).build());

   public Xray() {
      super(Categories.Render, "xray", "Only renders specified blocks. Good for mining.");
   }

   @Override
   public void onActivate() {
      if (this.autoDiscover.get()) {
         OreDiscovery.applyTo(this.blocks.get());
      }
      this.mc.levelRenderer.allChanged();
   }

   @Override
   public void onDeactivate() {
      this.mc.levelRenderer.allChanged();
   }

   @Override
   public WWidget getWidget(GuiTheme theme) {
      meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList list = theme.verticalList();
      meteordevelopment.meteorclient.gui.widgets.pressable.WButton discoverBtn = list.add(theme.button("Auto-Discover Modded Ores")).expandX().widget();
      discoverBtn.action = () -> {
         int count = OreDiscovery.applyTo(this.blocks.get());
         this.info("Discovered and added (highlight)%d(default) ores to whitelist.", count);
         if (this.isActive()) {
            this.mc.levelRenderer.allChanged();
         }
      };

      if (MixinPlugin.isSodiumPresent) {
         list.add(theme.label("Warning: Due to Sodium in use, opacity is overridden to 0."));
      } else if (MixinPlugin.isIrisPresent && IrisApi.getInstance().isShaderPackInUse()) {
         list.add(theme.label("Warning: Due to shaders in use, opacity is overridden to 0."));
      }

      return list;
   }

   @EventHandler
   private void onRenderBlockEntity(RenderBlockEntityEvent event) {
      if (this.isBlocked(event.blockEntity.getBlockState().getBlock(), event.blockEntity.getBlockPos())) {
         event.cancel();
      }
   }

   @EventHandler
   private void onChunkOcclusion(ChunkOcclusionEvent event) {
      event.cancel();
   }

   @EventHandler
   private void onAmbientOcclusion(AmbientOcclusionEvent event) {
      event.lightLevel = 1.0F;
   }

   public boolean modifyDrawSide(BlockState state, BlockGetter view, BlockPos pos, Direction facing, boolean returns) {
      if (!returns && !this.isBlocked(state.getBlock(), pos)) {
         BlockPos adjPos = pos.relative(facing);
         BlockState adjState = view.getBlockState(adjPos);
         return adjState.getFaceOcclusionShape(view, adjPos, facing.getOpposite()) != Shapes.block()
            || adjState.getBlock() != state.getBlock()
            || BlockUtils.isExposed(adjPos);
      } else {
         return returns;
      }
   }

   public boolean isBlocked(Block block, BlockPos blockPos) {
      return !this.blocks.get().contains(block) || this.exposedOnly.get() && blockPos != null && !BlockUtils.isExposed(blockPos);
   }

   public static int getAlpha(BlockState state, BlockPos pos) {
      WallHack wallHack = Modules.get().get(WallHack.class);
      Xray xray = Modules.get().get(Xray.class);
      if (wallHack.isActive() && wallHack.blocks.get().contains(state.getBlock())) {
         if (!MixinPlugin.isSodiumPresent && (!MixinPlugin.isIrisPresent || !IrisApi.getInstance().isShaderPackInUse())) {
            int alpha;
            if (xray.isActive()) {
               alpha = xray.opacity.get();
            } else {
               alpha = wallHack.opacity.get();
            }

            return alpha;
         } else {
            return 0;
         }
      } else if (xray.isActive() && !wallHack.isActive() && xray.isBlocked(state.getBlock(), pos)) {
         return !MixinPlugin.isSodiumPresent && (!MixinPlugin.isIrisPresent || !IrisApi.getInstance().isShaderPackInUse()) ? xray.opacity.get() : 0;
      } else {
         return -1;
      }
   }
}
