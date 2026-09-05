package meteordevelopment.meteorclient.utils.tooltip;

import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class ContainerTooltipComponent implements ClientTooltipComponent, MeteorTooltipData {
   private static final ResourceLocation TEXTURE_CONTAINER_BACKGROUND = MeteorClient.identifier("textures/container.png");
   private final ItemStack[] items;
   private final Color color;

   public ContainerTooltipComponent(ItemStack[] items, Color color) {
      this.items = items;
      this.color = color;
   }

   @Override
   public ClientTooltipComponent getComponent() {
      return this;
   }

   public int getHeight() {
      return 67;
   }

   public int getWidth(Font textRenderer) {
      return 176;
   }

   public void renderImage(Font textRenderer, int x, int y, GuiGraphics context) {
      RenderSystem.setShader(GameRenderer::getPositionTexShader);
      RenderSystem.setShaderColor((float)this.color.r / 255.0F, (float)this.color.g / 255.0F, (float)this.color.b / 255.0F, (float)this.color.a / 255.0F);
      context.blit(TEXTURE_CONTAINER_BACKGROUND, x, y, 0, 0.0F, 0.0F, 176, 67, 176, 67);
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      int row = 0;
      int i = 0;

      for (ItemStack itemStack : this.items) {
         RenderUtils.drawItem(context, itemStack, x + 8 + i * 18, y + 7 + row * 18, 1.0F, true);
         if (++i >= 9) {
            i = 0;
            row++;
         }
      }
   }
}
