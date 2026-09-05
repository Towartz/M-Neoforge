package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.InventoryTweaks;
import meteordevelopment.meteorclient.systems.modules.render.BetterTooltips;
import meteordevelopment.meteorclient.systems.modules.render.ItemHighlight;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button.Builder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.gui.screens.inventory.BookViewScreen.BookAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({AbstractContainerScreen.class})
public abstract class HandledScreenMixin<T extends AbstractContainerMenu> extends Screen implements MenuAccess<T> {
   @Shadow
   protected Slot hoveredSlot;
   @Shadow
   protected int leftPos;
   @Shadow
   protected int topPos;
   @Shadow
   private boolean doubleclick;
   @Unique
   private static final ItemStack[] ITEMS = new ItemStack[27];

   @Shadow
   @Nullable
   protected abstract Slot findSlot(double var1, double var3);

   @Shadow
   public abstract T getMenu();

   @Shadow
   protected abstract void slotClicked(Slot var1, int var2, int var3, ClickType var4);

   @Shadow
   public abstract void onClose();

   public HandledScreenMixin(Component title) {
      super(title);
   }

   @Inject(
      method = {"init"},
      at = {@At("TAIL")}
   )
   private void onInit(CallbackInfo info) {
      InventoryTweaks invTweaks = Modules.get().get(InventoryTweaks.class);
      if (invTweaks.isActive() && invTweaks.showButtons() && invTweaks.canSteal(this.getMenu())) {
         this.addRenderableWidget(
            new Builder(Component.literal("Steal"), button -> invTweaks.steal(this.getMenu())).pos(this.leftPos, this.topPos - 22).size(40, 20).build()
         );
         this.addRenderableWidget(
            new Builder(Component.literal("Dump"), button -> invTweaks.dump(this.getMenu())).pos(this.leftPos + 42, this.topPos - 22).size(40, 20).build()
         );
      }
   }

   @Inject(
      method = {"mouseDragged"},
      at = {@At("TAIL")}
   )
   private void onMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> info) {
      if (button == 0 && !this.doubleclick && Modules.get().get(InventoryTweaks.class).mouseDragItemMove()) {
         Slot slot = this.findSlot(mouseX, mouseY);
         if (slot != null && slot.hasItem() && hasShiftDown()) {
            this.slotClicked(slot, slot.index, button, ClickType.QUICK_MOVE);
         }
      }
   }

   @Inject(
      method = {"mouseClicked"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
      BetterTooltips toolips = Modules.get().get(BetterTooltips.class);
      if (button == 2
         && this.hoveredSlot != null
         && !this.hoveredSlot.getItem().isEmpty()
         && MeteorClient.mc.player.containerMenu.getCarried().isEmpty()
         && toolips.middleClickOpen()) {
         ItemStack itemStack = this.hoveredSlot.getItem();
         if (Utils.hasItems(itemStack) || itemStack.getItem() == Items.ENDER_CHEST) {
            cir.setReturnValue(Utils.openContainer(this.hoveredSlot.getItem(), ITEMS, false));
         } else if (itemStack.get(DataComponents.WRITTEN_BOOK_CONTENT) != null || itemStack.get(DataComponents.WRITABLE_BOOK_CONTENT) != null) {
            this.onClose();
            MeteorClient.mc.setScreen(new BookViewScreen(BookAccess.fromItem(itemStack)));
            cir.setReturnValue(true);
         }
      }
   }

   @Inject(
      method = {"drawSlot"},
      at = {@At("HEAD")}
   )
   private void onDrawSlot(GuiGraphics context, Slot slot, CallbackInfo ci) {
      int color = Modules.get().get(ItemHighlight.class).getColor(slot.getItem());
      if (color != -1) {
         context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
      }
   }
}
