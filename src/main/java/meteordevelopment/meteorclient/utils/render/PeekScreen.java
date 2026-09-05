package meteordevelopment.meteorclient.utils.render;

import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.BetterTooltips;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen.BookAccess;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class PeekScreen extends ShulkerBoxScreen {
   private final ResourceLocation TEXTURE = ResourceLocation.parse("textures/gui/container/shulker_box.png");
   private final ItemStack[] contents;
   private final ItemStack storageBlock;

   public PeekScreen(ItemStack storageBlock, ItemStack[] contents) {
      super(
         new ShulkerBoxMenu(0, MeteorClient.mc.player.getInventory(), new SimpleContainer(contents)),
         MeteorClient.mc.player.getInventory(),
         storageBlock.getHoverName()
      );
      this.contents = contents;
      this.storageBlock = storageBlock;
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      BetterTooltips tooltips = Modules.get().get(BetterTooltips.class);
      if (button == 2
         && this.hoveredSlot != null
         && !this.hoveredSlot.getItem().isEmpty()
         && MeteorClient.mc.player.containerMenu.getCarried().isEmpty()
         && tooltips.middleClickOpen()) {
         ItemStack itemStack = this.hoveredSlot.getItem();
         if (Utils.hasItems(itemStack) || itemStack.getItem() == Items.ENDER_CHEST) {
            return Utils.openContainer(this.hoveredSlot.getItem(), this.contents, false);
         }

         if (itemStack.get(DataComponents.WRITTEN_BOOK_CONTENT) != null || itemStack.get(DataComponents.WRITABLE_BOOK_CONTENT) != null) {
            this.onClose();
            MeteorClient.mc.setScreen(new BookViewScreen(BookAccess.fromItem(itemStack)));
            return true;
         }
      }

      return false;
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      return false;
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (keyCode != 256 && !MeteorClient.mc.options.keyInventory.matches(keyCode, scanCode)) {
         return false;
      } else {
         this.onClose();
         return true;
      }
   }

   public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
      if (keyCode == 256) {
         this.onClose();
         return true;
      } else {
         return false;
      }
   }

   protected void renderBg(GuiGraphics context, float delta, int mouseX, int mouseY) {
      Color color = Utils.getShulkerColor(this.storageBlock);
      RenderSystem.setShader(GameRenderer::getPositionTexShader);
      RenderSystem.setShaderColor((float)color.r / 255.0F, (float)color.g / 255.0F, (float)color.b / 255.0F, (float)color.a / 255.0F);
      int i = (this.width - this.imageWidth) / 2;
      int j = (this.height - this.imageHeight) / 2;
      context.blit(this.TEXTURE, i, j, 0, 0, this.imageWidth, this.imageHeight);
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
   }
}
