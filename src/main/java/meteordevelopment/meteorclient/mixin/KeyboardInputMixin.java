package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.GUIMove;
import meteordevelopment.meteorclient.systems.modules.movement.Sneak;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import net.minecraft.client.Options;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({KeyboardInput.class})
public abstract class KeyboardInputMixin extends net.minecraft.client.player.Input {
   @Shadow
   @Final
   private Options options;

   @Inject(
      method = {"tick"},
      at = {@At("TAIL")}
   )
   private void isPressed(boolean slowDown, float f, CallbackInfo ci) {
      if (Modules.get() != null) {
         if (Modules.get().get(Sneak.class).doVanilla()) {
            this.shiftKeyDown = true;
         }

         GUIMove guiMove = Modules.get().get(GUIMove.class);
         if (guiMove != null && guiMove.isActive() && !guiMove.skip() && guiMove.isScreenValid()) {
            this.up = Input.isPressed(this.options.keyUp);
            this.down = Input.isPressed(this.options.keyDown);
            this.left = Input.isPressed(this.options.keyLeft);
            this.right = Input.isPressed(this.options.keyRight);

            this.forwardImpulse = this.up == this.down ? 0.0F : (this.up ? 1.0F : -1.0F);
            this.leftImpulse = this.left == this.right ? 0.0F : (this.left ? 1.0F : -1.0F);

            if (guiMove.jump.get()) {
               this.jumping = Input.isPressed(this.options.keyJump);
            }

            if (guiMove.sneak.get()) {
               this.shiftKeyDown = Input.isPressed(this.options.keyShift);
            }

            if (slowDown) {
               this.leftImpulse *= f;
               this.forwardImpulse *= f;
            }
         }
      }
   }
}
