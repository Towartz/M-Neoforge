package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import meteordevelopment.meteorclient.utils.tooltip.MeteorTooltipData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin({GuiGraphics.class})
public abstract class DrawContextMixin {
   @Inject(
      method = {"drawTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;Ljava/util/Optional;II)V"},
      at = {@At(
         value = "INVOKE",
         target = "Ljava/util/Optional;ifPresent(Ljava/util/function/Consumer;)V",
         shift = Shift.BEFORE
      )},
      locals = LocalCapture.CAPTURE_FAILSOFT,
      require = 0
   )
   private void onDrawTooltip(
      Font textRenderer, List<Component> text, Optional<TooltipComponent> data, int x, int y, CallbackInfo ci, List<ClientTooltipComponent> list
   ) {
      if (data.isPresent() && data.get() instanceof MeteorTooltipData meteorTooltipData) {
         list.add(meteorTooltipData.getComponent());
      }
   }

   @ModifyReceiver(
      method = {"drawTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;Ljava/util/Optional;II)V"},
      at = {@At(
         value = "INVOKE",
         target = "Ljava/util/Optional;ifPresent(Ljava/util/function/Consumer;)V"
      )}
   )
   private Optional<TooltipComponent> onDrawTooltip_modifyIfPresentReceiver(Optional<TooltipComponent> data, Consumer<TooltipComponent> consumer) {
      return data.isPresent() && data.get() instanceof MeteorTooltipData ? Optional.empty() : data;
   }
}
