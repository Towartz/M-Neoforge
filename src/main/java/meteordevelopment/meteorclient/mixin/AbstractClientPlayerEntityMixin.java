package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.misc.FakeClientPlayer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({AbstractClientPlayer.class})
public abstract class AbstractClientPlayerEntityMixin {
   @Inject(
      method = {"getPlayerListEntry"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onGetPlayerListEntry(CallbackInfoReturnable<PlayerInfo> info) {
      if (MeteorClient.mc.getConnection() == null) {
         info.setReturnValue(FakeClientPlayer.getPlayerListEntry());
      }
   }

   @Inject(
      method = {"isSpectator"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onIsSpectator(CallbackInfoReturnable<Boolean> info) {
      if (MeteorClient.mc.getConnection() == null) {
         info.setReturnValue(false);
      }
   }

   @Inject(
      method = {"isCreative"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onIsCreative(CallbackInfoReturnable<Boolean> info) {
      if (MeteorClient.mc.getConnection() == null) {
         info.setReturnValue(false);
      }
   }
}
