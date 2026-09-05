package meteordevelopment.meteorclient.mixin;

import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.inventory.HorseInventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({HorseInventoryMenu.class})
public interface HorseScreenHandlerAccessor {
   @Accessor("entity")
   AbstractHorse getEntity();
}
