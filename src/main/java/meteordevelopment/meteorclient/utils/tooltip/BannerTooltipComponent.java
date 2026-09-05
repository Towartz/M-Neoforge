package meteordevelopment.meteorclient.utils.tooltip;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

public class BannerTooltipComponent implements MeteorTooltipData, ClientTooltipComponent {
   private final DyeColor color;
   private final BannerPatternLayers patterns;
   private final ModelPart bannerField;

   public BannerTooltipComponent(ItemStack banner) {
      this.color = ((BannerItem)banner.getItem()).getColor();
      this.patterns = (BannerPatternLayers)banner.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
      this.bannerField = MeteorClient.mc.getEntityModels().bakeLayer(ModelLayers.BANNER).getChild("flag");
   }

   public BannerTooltipComponent(DyeColor color, BannerPatternLayers patterns) {
      this.color = color;
      this.patterns = patterns;
      this.bannerField = MeteorClient.mc.getEntityModels().bakeLayer(ModelLayers.BANNER).getChild("flag");
   }

   @Override
   public ClientTooltipComponent getComponent() {
      return this;
   }

   public int getHeight() {
      return 158;
   }

   public int getWidth(Font textRenderer) {
      return 80;
   }

   public void renderImage(Font textRenderer, int x, int y, GuiGraphics context) {
      Lighting.setupForFlatItems();
      PoseStack matrices = context.pose();
      matrices.pushPose();
      matrices.translate((float)(x + 8), (float)(y + 8), 0.0F);
      matrices.pushPose();
      matrices.translate(0.5, 16.0, 0.0);
      matrices.scale(6.0F, -6.0F, 1.0F);
      matrices.scale(2.0F, -2.0F, -2.0F);
      matrices.pushPose();
      matrices.translate(2.5, 8.5, 0.0);
      matrices.scale(5.0F, 5.0F, 5.0F);
      BufferSource immediate = MeteorClient.mc.renderBuffers().bufferSource();
      this.bannerField.xRot = 0.0F;
      this.bannerField.y = -32.0F;
      BannerRenderer.renderPatterns(
         matrices, immediate, 15728880, OverlayTexture.NO_OVERLAY, this.bannerField, ModelBakery.BANNER_BASE, true, this.color, this.patterns
      );
      matrices.popPose();
      matrices.popPose();
      immediate.endBatch();
      matrices.popPose();
      Lighting.setupFor3DItems();
   }
}
