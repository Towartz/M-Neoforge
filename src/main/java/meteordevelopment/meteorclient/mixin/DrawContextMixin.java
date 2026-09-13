package meteordevelopment.meteorclient.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import meteordevelopment.meteorclient.utils.tooltip.MeteorTooltipData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({GuiGraphics.class})
public abstract class DrawContextMixin {
   @Invoker("renderTooltipInternal")
   public abstract void meteor$renderTooltipInternal(Font font, List<ClientTooltipComponent> components, int x, int y, ClientTooltipPositioner positioner);

   @Inject(
      method = "renderTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;II)V",
      at = @At("HEAD"),
      cancellable = true,
      require = 0
   )
   private void onRenderTooltip(
      Font font,
      List<Component> textComponents,
      Optional<TooltipComponent> tooltipComponent,
      int mouseX,
      int mouseY,
      CallbackInfo ci
   ) {
      if (tooltipComponent != null && tooltipComponent.isPresent() && tooltipComponent.get() instanceof MeteorTooltipData meteorTooltipData) {
         List<ClientTooltipComponent> list = new ArrayList<>();
         for (Component c : textComponents) {
            list.add(ClientTooltipComponent.create(c.getVisualOrderText()));
         }
         list.add(meteorTooltipData.getComponent());
         meteor$renderTooltipInternal(font, list, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE);
         ci.cancel();
      }
   }
}
