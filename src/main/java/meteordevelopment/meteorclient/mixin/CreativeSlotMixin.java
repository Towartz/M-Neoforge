package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.mixininterface.ISlot;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(
   targets = {"net/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen$SlotWrapper"}
)
public abstract class CreativeSlotMixin implements ISlot {
   @Shadow
   @Final
   Slot target;

   @Override
   public int getId() {
      return this.target.index;
   }

   @Override
   public int getIndex() {
      return this.target.getContainerSlot();
   }
}
