package meteordevelopment.meteorclient.mixin;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.GUIMove;
import meteordevelopment.meteorclient.systems.modules.render.NoRender;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.text.MeteorClickEvent;
import meteordevelopment.meteorclient.utils.misc.text.RunnableClickEvent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {Screen.class},
   priority = 500
)
public abstract class ScreenMixin {
   @Inject(
      method = {"renderInGameBackground"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onRenderInGameBackground(CallbackInfo info) {
      if (Utils.canUpdate() && Modules.get().get(NoRender.class).noGuiBackground()) {
         info.cancel();
      }
   }

   @Inject(
      method = {"handleTextClick"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onInvalidClickEvent(@Nullable Style style, CallbackInfoReturnable<Boolean> cir) {
      if (style != null && style.getClickEvent() instanceof RunnableClickEvent runnableClickEvent) {
         runnableClickEvent.runnable.run();
         cir.setReturnValue(true);
      }
   }

   @Inject(
      method = {"handleTextClick"},
      at = {@At(
         value = "INVOKE",
         target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;)V",
         ordinal = 1,
         remap = false
      )},
      cancellable = true
   )
   private void onRunCommand(Style style, CallbackInfoReturnable<Boolean> cir) {
      if (style.getClickEvent() instanceof MeteorClickEvent clickEvent && clickEvent.getValue().startsWith(Config.get().prefix.get())) {
         try {
            Commands.dispatch(style.getClickEvent().getValue().substring(Config.get().prefix.get().length()));
            cir.setReturnValue(true);
         } catch (CommandSyntaxException var5) {
            MeteorClient.LOG.error("Failed to run command", var5);
         }
      }
   }

   @Inject(
      method = {"keyPressed"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> info) {
      if (!((Object)this instanceof ChatScreen)) {
         GUIMove guiMove = Modules.get() != null ? Modules.get().get(GUIMove.class) : null;
         if (guiMove != null && guiMove.isActive() && !guiMove.skip() && guiMove.isScreenValid()) {
            List<Integer> arrows = List.of(262, 263, 264, 265);
            if (guiMove.disableArrows() && arrows.contains(keyCode) || guiMove.disableSpace() && keyCode == 32) {
               info.setReturnValue(true);
            }
         }
      }
   }
}
