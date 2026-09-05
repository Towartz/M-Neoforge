package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.InventoryTweaks;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(
   targets = {"net/minecraft/world/inventory/InventoryMenu$1"}
)
public abstract class PlayerArmorSlotMixin extends Slot {
   public PlayerArmorSlotMixin(Container inventory, int index, int x, int y) {
      super(inventory, index, x, y);
   }

   public int getMaxStackSize() {
      return Modules.get().get(InventoryTweaks.class).armorStorage() ? 64 : super.getMaxStackSize();
   }

   public boolean mayPlace(ItemStack stack) {
      return Modules.get().get(InventoryTweaks.class).armorStorage() ? true : super.mayPlace(stack);
   }

   public boolean mayPickup(Player playerEntity) {
      return Modules.get().get(InventoryTweaks.class).armorStorage() ? true : super.mayPickup(playerEntity);
   }
}
