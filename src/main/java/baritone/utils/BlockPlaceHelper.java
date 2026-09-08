package baritone.utils;

import baritone.Baritone;
import baritone.api.utils.IPlayerContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;

public class BlockPlaceHelper {
   private static final int BASE_PLACE_DELAY = 1;
   private final IPlayerContext ctx;
   private int rightClickTimer;

   BlockPlaceHelper(IPlayerContext playerContext) {
      this.ctx = playerContext;
   }

   public void tick(boolean rightClickRequested) {
      if (this.rightClickTimer > 0) {
         this.rightClickTimer--;
      } else {
         HitResult mouseOver = this.ctx.objectMouseOver();
         if (rightClickRequested && !this.ctx.player().isHandsBusy() && mouseOver != null && mouseOver.getType() == Type.BLOCK) {
            this.rightClickTimer = Baritone.settings().rightClickSpeed.value - 1;

            for (InteractionHand hand : InteractionHand.values()) {
               if (this.ctx.playerController().processRightClickBlock(this.ctx.player(), this.ctx.world(), hand, (BlockHitResult)mouseOver)
                  == InteractionResult.SUCCESS) {
                  this.ctx.player().swing(hand);
                  return;
               }

               if (!this.ctx.player().getItemInHand(hand).isEmpty()
                  && this.ctx.playerController().processRightClick(this.ctx.player(), this.ctx.world(), hand) == InteractionResult.SUCCESS) {
                  return;
               }
            }
         }
      }
   }
}
