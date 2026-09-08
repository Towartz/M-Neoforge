package baritone.utils.player;

import baritone.api.utils.IPlayerController;
import baritone.utils.accessor.IPlayerControllerMP;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

public final class BaritonePlayerController implements IPlayerController {
   private final Minecraft mc;

   public BaritonePlayerController(Minecraft mc) {
      this.mc = mc;
   }

   @Override
   public void syncHeldItem() {
      ((IPlayerControllerMP)this.mc.gameMode).callSyncCurrentPlayItem();
   }

   @Override
   public boolean hasBrokenBlock() {
      return !((IPlayerControllerMP)this.mc.gameMode).isHittingBlock();
   }

   @Override
   public boolean onPlayerDamageBlock(BlockPos pos, Direction side) {
      return this.mc.gameMode.continueDestroyBlock(pos, side);
   }

   @Override
   public void resetBlockRemoving() {
      this.mc.gameMode.stopDestroyBlock();
   }

   @Override
   public void windowClick(int windowId, int slotId, int mouseButton, ClickType type, Player player) {
      this.mc.gameMode.handleInventoryMouseClick(windowId, slotId, mouseButton, type, player);
   }

   @Override
   public GameType getGameType() {
      return this.mc.gameMode.getPlayerMode();
   }

   @Override
   public InteractionResult processRightClickBlock(LocalPlayer player, Level world, InteractionHand hand, BlockHitResult result) {
      return this.mc.gameMode.useItemOn(player, hand, result);
   }

   @Override
   public InteractionResult processRightClick(LocalPlayer player, Level world, InteractionHand hand) {
      return this.mc.gameMode.useItem(player, hand);
   }

   @Override
   public boolean clickBlock(BlockPos loc, Direction face) {
      return this.mc.gameMode.startDestroyBlock(loc, face);
   }

   @Override
   public void setHittingBlock(boolean hittingBlock) {
      ((IPlayerControllerMP)this.mc.gameMode).setIsHittingBlock(hittingBlock);
   }
}
