package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.GUIMove;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({KeyMapping.class})
public abstract class KeyMappingMixin {
   @Shadow
   private boolean isDown;

   @Inject(
      method = {"isDown"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onIsDown(CallbackInfoReturnable<Boolean> cir) {
      if (this.isDown && Modules.get() != null) {
         GUIMove guiMove = Modules.get().get(GUIMove.class);
         if (guiMove != null && guiMove.isActive() && !guiMove.skip() && guiMove.isScreenValid()) {
            Options opts = Minecraft.getInstance().options;
            if (opts != null) {
               KeyMapping self = (KeyMapping)(Object)this;
               if (self == opts.keyUp
                  || self == opts.keyDown
                  || self == opts.keyLeft
                  || self == opts.keyRight
                  || (guiMove.jump.get() && self == opts.keyJump)
                  || (guiMove.sneak.get() && self == opts.keyShift)
                  || (guiMove.sprint.get() && self == opts.keySprint)) {
                  cir.setReturnValue(true);
               }
            }
         }
      }
   }
}
