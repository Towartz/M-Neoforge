package meteordevelopment.meteorclient.mixin;

import java.util.List;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.BetterTooltips;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ShulkerBoxBlock.class})
public abstract class ShulkerBoxBlockMixin {
   @Inject(
      method = {"appendTooltip"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onAppendTooltip(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag options, CallbackInfo ci) {
      if (Modules.get() != null) {
         BetterTooltips tooltips = Modules.get().get(BetterTooltips.class);
         if (tooltips.isActive()) {
            if (tooltips.previewShulkers()) {
               ci.cancel();
            } else if (tooltips.shulkerCompactTooltip()) {
               ci.cancel();
               tooltips.applyCompactShulkerTooltip(stack, tooltip);
            }
         }
      }
   }
}
