package meteordevelopment.meteorclient.utils.tooltip;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.BetterTooltips;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public class MapTooltipComponent implements ClientTooltipComponent, MeteorTooltipData {
   private static final ResourceLocation TEXTURE_MAP_BACKGROUND = ResourceLocation.parse("textures/map/map_background.png");
   private final int mapId;

   public MapTooltipComponent(int mapId) {
      this.mapId = mapId;
   }

   public int getHeight() {
      double scale = Modules.get().get(BetterTooltips.class).mapsScale.get();
      return (int)(144.0 * scale) + 2;
   }

   public int getWidth(Font textRenderer) {
      double scale = Modules.get().get(BetterTooltips.class).mapsScale.get();
      return (int)(144.0 * scale);
   }

   @Override
   public ClientTooltipComponent getComponent() {
      return this;
   }

   public void renderImage(Font textRenderer, int x, int y, GuiGraphics context) {
      double scale = Modules.get().get(BetterTooltips.class).mapsScale.get();
      PoseStack matrices = context.pose();
      matrices.pushPose();
      matrices.translate((float)x, (float)y, 0.0F);
      matrices.scale((float)scale * 2.0F, (float)scale * 2.0F, 0.0F);
      matrices.scale(1.125F, 1.125F, 0.0F);
      RenderSystem.setShader(GameRenderer::getPositionTexShader);
      context.blit(TEXTURE_MAP_BACKGROUND, 0, 0, 0, 0.0F, 0.0F, 64, 64, 64, 64);
      matrices.popPose();
      BufferSource consumer = MeteorClient.mc.renderBuffers().bufferSource();
      MapItemSavedData mapState = MapItem.getSavedData(new MapId(this.mapId), MeteorClient.mc.level);
      if (mapState != null) {
         matrices.pushPose();
         matrices.translate((float)x, (float)y, 0.0F);
         matrices.scale((float)scale, (float)scale, 0.0F);
         matrices.translate(8.0F, 8.0F, 0.0F);
         MeteorClient.mc.gameRenderer.getMapRenderer().render(matrices, consumer, new MapId(this.mapId), mapState, false, 15728880);
         consumer.endBatch();
         matrices.popPose();
      }
   }
}
