package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.BetterTooltips;
import net.minecraft.world.item.component.DyedItemColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({DyedItemColor.class})
public abstract class DyedColorComponentMixin {
   @ModifyExpressionValue(
      method = {"appendTooltip"},
      at = {@At(
         value = "FIELD",
         target = "Lnet/minecraft/component/type/DyedColorComponent;showInTooltip:Z"
      )}
   )
   private boolean modifyShowInTooltip(boolean original) {
      BetterTooltips bt = Modules.get().get(BetterTooltips.class);
      return bt.isActive() && bt.dye.get() || original;
   }
}
