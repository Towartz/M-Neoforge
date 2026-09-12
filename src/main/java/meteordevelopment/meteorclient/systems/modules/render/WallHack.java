package meteordevelopment.meteorclient.systems.modules.render;

import java.util.List;
import meteordevelopment.meteorclient.MixinPlugin;
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
import meteordevelopment.orbit.EventHandler;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.world.level.block.Block;

public class WallHack extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   public final Setting<Integer> opacity = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("opacity")
            .description("The opacity for rendered blocks.")
            .defaultValue(Integer.valueOf(0))
            .range(0, 255)
            .sliderMax(255)
            .onChanged(onChanged -> {
               if (this.isActive()) {
                  this.mc.levelRenderer.allChanged();
               }
            })
            .build()
      );
   private volatile java.util.Set<Block> blockSet = java.util.Collections.emptySet();

   public final Setting<List<Block>> blocks = this.sgGeneral
      .add(new BlockListSetting.Builder().name("blocks").description("What blocks should be targeted for Wall Hack.").defaultValue().onChanged(onChanged -> {
         this.updateBlockSet();
         if (this.isActive()) {
            this.mc.levelRenderer.allChanged();
         }
      }).build());
   public final Setting<Boolean> occludeChunks = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("occlude-chunks")
            .description("Whether caves should occlude underground (may look wonky when on).")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );

   public WallHack() {
      super(Categories.Render, "wall-hack", "Makes blocks translucent.");
   }

   public void updateBlockSet() {
      List<Block> list = this.blocks.get();
      if (list != null && !list.isEmpty()) {
         this.blockSet = new it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet<>(list);
      } else {
         this.blockSet = java.util.Collections.emptySet();
      }
   }

   public boolean contains(Block block) {
      return this.blockSet.contains(block);
   }

   @Override
   public void onActivate() {
      this.updateBlockSet();
      this.mc.levelRenderer.allChanged();
   }

   @Override
   public void onDeactivate() {
      this.mc.levelRenderer.allChanged();
   }

   @Override
   public WWidget getWidget(GuiTheme theme) {
      if (MixinPlugin.isIrisPresent && IrisApi.getInstance().isShaderPackInUse()) {
         return theme.label("Warning: Due to shaders in use, opacity is overridden to 0.");
      }
      return null;
   }

   @EventHandler
   private void onChunkOcclusion(ChunkOcclusionEvent event) {
      if (!this.occludeChunks.get()) {
         event.cancel();
      }
   }
}
