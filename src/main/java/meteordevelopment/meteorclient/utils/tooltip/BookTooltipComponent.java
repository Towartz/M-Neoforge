package meteordevelopment.meteorclient.utils.tooltip;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

public class BookTooltipComponent implements ClientTooltipComponent, MeteorTooltipData {
   private static final ResourceLocation TEXTURE_BOOK_BACKGROUND = ResourceLocation.parse("textures/gui/book.png");
   private final Component page;

   public BookTooltipComponent(Component page) {
      this.page = page;
   }

   @Override
   public ClientTooltipComponent getComponent() {
      return this;
   }

   public int getHeight() {
      return 134;
   }

   public int getWidth(Font textRenderer) {
      return 112;
   }

   public void renderImage(Font textRenderer, int x, int y, GuiGraphics context) {
      RenderSystem.setShader(GameRenderer::getPositionTexShader);
      context.blit(TEXTURE_BOOK_BACKGROUND, x, y, 0, 12.0F, 0.0F, 112, 134, 179, 179);
      PoseStack matrices = context.pose();
      matrices.pushPose();
      matrices.translate((float)(x + 16), (float)(y + 12), 1.0F);
      matrices.scale(0.7F, 0.7F, 1.0F);
      int offset = 0;

      for (FormattedCharSequence line : textRenderer.split(this.page, 112)) {
         context.drawString(textRenderer, line, 0, offset, 0, false);
         offset += 8;
      }

      matrices.popPose();
   }
}
