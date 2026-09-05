package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.meteor.CharTypedEvent;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.gui.GuiKeyEvents;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({KeyboardHandler.class})
public abstract class KeyboardMixin {
   @Shadow
   @Final
   private Minecraft minecraft;

   @Inject(
      method = {"keyPress"},
      at = {@At("HEAD")},
      cancellable = true
   )
   public void onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo info) {
      if (key != -1) {
         if (action == 1) {
            modifiers |= Input.getModifier(key);
         } else if (action == 0) {
            modifiers &= ~Input.getModifier(key);
         }

         if (this.minecraft.screen instanceof WidgetScreen && action == 2) {
            ((WidgetScreen)this.minecraft.screen).keyRepeated(key, modifiers);
         }

         if (action == 0) {
            Input.setKeyState(key, false);
         }

         if (GuiKeyEvents.canUseKeys) {
            Input.setKeyState(key, action != 0);
            if (MeteorClient.EVENT_BUS.post(KeyEvent.get(key, modifiers, KeyAction.get(action))).isCancelled()) {
               info.cancel();
            }
         }
      }
   }

   @Inject(
      method = {"charTyped"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onChar(long window, int i, int j, CallbackInfo info) {
      if (Utils.canUpdate()
         && !this.minecraft.isPaused()
         && (this.minecraft.screen == null || this.minecraft.screen instanceof WidgetScreen)
         && MeteorClient.EVENT_BUS.post(CharTypedEvent.get((char)i)).isCancelled()) {
         info.cancel();
      }
   }
}
