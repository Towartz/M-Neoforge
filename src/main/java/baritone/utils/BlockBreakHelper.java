package baritone.utils;

import baritone.api.BaritoneAPI;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.VecUtils;
import baritone.utils.accessor.IPlayerControllerMP;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;

public final class BlockBreakHelper {
   private static final int BASE_BREAK_DELAY = 1;
   private final IPlayerContext ctx;
   private boolean wasHitting;
   private int breakDelayTimer = 0;
   private BlockPos lockedBlockPos = null;
   private Direction lockedBlockSide = null;

   BlockBreakHelper(IPlayerContext ctx) {
      this.ctx = ctx;
   }

   public void stopBreakingBlock() {
      if (this.ctx.player() != null && this.wasHitting) {
         this.ctx.playerController().setHittingBlock(false);
         this.ctx.playerController().resetBlockRemoving();
         this.wasHitting = false;
      }
      this.lockedBlockPos = null;
      this.lockedBlockSide = null;
   }

   public boolean isBreakingBlock() {
      return this.wasHitting;
   }

   public void tick(boolean isLeftClick) {
      if (this.breakDelayTimer > 0) {
         this.breakDelayTimer--;
      } else {
         HitResult trace = this.ctx.objectMouseOver();
         boolean isBlockTrace = trace != null && trace.getType() == Type.BLOCK;

         // Check if locked target block is still being broken (prevents crosshair jitter resetting mining progress)
         if (isLeftClick && this.lockedBlockPos != null && this.wasHitting) {
            if (this.ctx.world() != null
                && !this.ctx.world().getBlockState(this.lockedBlockPos).isAir()
                && this.ctx.playerHead().distanceTo(VecUtils.getBlockPosCenter(this.lockedBlockPos)) <= this.ctx.playerController().getBlockReachDistance() + 0.5) {

               this.ctx.playerController().setHittingBlock(this.wasHitting);
               if (this.ctx.playerController().onPlayerDamageBlock(this.lockedBlockPos, this.lockedBlockSide)) {
                  this.ctx.player().swing(InteractionHand.MAIN_HAND);
               }

               if (this.ctx.playerController().hasBrokenBlock()) {
                  this.breakDelayTimer = BaritoneAPI.getSettings().blockBreakSpeed.value - 1;
                  ((IPlayerControllerMP)this.ctx.minecraft().gameMode).setDestroyDelay(0);
                  this.wasHitting = false;
                  this.lockedBlockPos = null;
                  this.lockedBlockSide = null;
               } else {
                  this.wasHitting = true;
               }
               this.ctx.playerController().setHittingBlock(false);
               return;
            } else {
               this.lockedBlockPos = null;
               this.lockedBlockSide = null;
            }
         }

         if (isLeftClick && isBlockTrace) {
            BlockPos hitPos = ((BlockHitResult)trace).getBlockPos();
            Direction hitSide = ((BlockHitResult)trace).getDirection();
            this.lockedBlockPos = hitPos;
            this.lockedBlockSide = hitSide;

            this.ctx.playerController().setHittingBlock(this.wasHitting);
            if (this.ctx.playerController().hasBrokenBlock()) {
               this.ctx.playerController().syncHeldItem();
               this.ctx.playerController().clickBlock(hitPos, hitSide);
               this.ctx.player().swing(InteractionHand.MAIN_HAND);
            } else {
               if (this.ctx.playerController().onPlayerDamageBlock(hitPos, hitSide)) {
                  this.ctx.player().swing(InteractionHand.MAIN_HAND);
               }

               if (this.ctx.playerController().hasBrokenBlock()) {
                  this.breakDelayTimer = BaritoneAPI.getSettings().blockBreakSpeed.value - 1;
                  ((IPlayerControllerMP)this.ctx.minecraft().gameMode).setDestroyDelay(0);
                  this.lockedBlockPos = null;
                  this.lockedBlockSide = null;
               }
            }

            this.wasHitting = !this.ctx.playerController().hasBrokenBlock();
            this.ctx.playerController().setHittingBlock(false);
         } else {
            this.wasHitting = false;
            this.lockedBlockPos = null;
            this.lockedBlockSide = null;
         }
      }
   }
}
