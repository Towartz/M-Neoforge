package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.AutoSmelter;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({AbstractFurnaceScreen.class})
public abstract class AbstractFurnaceScreenMixin<T extends AbstractFurnaceMenu> extends AbstractContainerScreen<T> implements RecipeUpdateListener {
   public AbstractFurnaceScreenMixin(T container, Inventory playerInventory, Component name) {
      super(container, playerInventory, name);
   }

   @Inject(
      method = {"handledScreenTick"},
      at = {@At("TAIL")}
   )
   private void onTick(CallbackInfo info) {
      if (Modules.get().isActive(AutoSmelter.class)) {
         Modules.get().get(AutoSmelter.class).tick((AbstractFurnaceMenu)this.menu);
      }
   }
}
