package baritone.api.utils;

import baritone.api.BaritoneAPI;
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

public interface IPlayerController {
   void syncHeldItem();

   boolean hasBrokenBlock();

   boolean onPlayerDamageBlock(BlockPos var1, Direction var2);

   void resetBlockRemoving();

   void windowClick(int var1, int var2, int var3, ClickType var4, Player var5);

   GameType getGameType();

   InteractionResult processRightClickBlock(LocalPlayer var1, Level var2, InteractionHand var3, BlockHitResult var4);

   InteractionResult processRightClick(LocalPlayer var1, Level var2, InteractionHand var3);

   boolean clickBlock(BlockPos var1, Direction var2);

   void setHittingBlock(boolean var1);

   default double getBlockReachDistance() {
      return this.getGameType().isCreative() ? 5.0 : (double)BaritoneAPI.getSettings().blockReachDistance.value.floatValue();
   }
}
