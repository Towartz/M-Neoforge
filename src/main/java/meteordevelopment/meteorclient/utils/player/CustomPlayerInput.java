package meteordevelopment.meteorclient.utils.player;

import net.minecraft.client.player.Input;

public class CustomPlayerInput extends Input {
   public void tick(boolean slowDown, float f) {
      this.forwardImpulse = this.up == this.down ? 0.0F : (this.up ? 1.0F : -1.0F);
      this.leftImpulse = this.left == this.right ? 0.0F : (this.left ? 1.0F : -1.0F);
      if (slowDown) {
         this.forwardImpulse *= f;
         this.leftImpulse *= f;
      } else if (this.shiftKeyDown) {
         this.forwardImpulse = (float)((double)this.forwardImpulse * 0.3);
         this.leftImpulse = (float)((double)this.leftImpulse * 0.3);
      }
   }

   public void stop() {
      this.up = false;
      this.down = false;
      this.right = false;
      this.left = false;
      this.jumping = false;
      this.shiftKeyDown = false;
      this.forwardImpulse = 0.0F;
      this.leftImpulse = 0.0F;
   }
}
