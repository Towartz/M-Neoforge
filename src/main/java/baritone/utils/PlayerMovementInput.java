package baritone.utils;

import net.minecraft.client.player.Input;

public class PlayerMovementInput extends Input {
   private final InputOverrideHandler handler;

   PlayerMovementInput(InputOverrideHandler handler) {
      this.handler = handler;
   }

   public void tick(boolean p_225607_1_, float f) {
      this.leftImpulse = 0.0F;
      this.forwardImpulse = 0.0F;
      this.jumping = this.handler.isInputForcedDown(baritone.api.utils.input.Input.JUMP);
      if (this.up = this.handler.isInputForcedDown(baritone.api.utils.input.Input.MOVE_FORWARD)) {
         this.forwardImpulse++;
      }

      if (this.down = this.handler.isInputForcedDown(baritone.api.utils.input.Input.MOVE_BACK)) {
         this.forwardImpulse--;
      }

      if (this.left = this.handler.isInputForcedDown(baritone.api.utils.input.Input.MOVE_LEFT)) {
         this.leftImpulse++;
      }

      if (this.right = this.handler.isInputForcedDown(baritone.api.utils.input.Input.MOVE_RIGHT)) {
         this.leftImpulse--;
      }

      if (this.shiftKeyDown = this.handler.isInputForcedDown(baritone.api.utils.input.Input.SNEAK)) {
         this.leftImpulse = (float)((double)this.leftImpulse * 0.3);
         this.forwardImpulse = (float)((double)this.forwardImpulse * 0.3);
      }
   }
}
