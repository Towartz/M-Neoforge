package meteordevelopment.meteorclient.mixin;

import com.mojang.authlib.GameProfile;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.NameProtect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({PlayerInfo.class})
public abstract class PlayerListEntryMixin {
   @Shadow
   public abstract GameProfile getProfile();

   @Inject(
      method = {"getSkinTextures"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onGetTexture(CallbackInfoReturnable<PlayerSkin> info) {
      if (this.getProfile().getName().equals(Minecraft.getInstance().getUser().getName()) && Modules.get().get(NameProtect.class).skinProtect()) {
         info.setReturnValue(DefaultPlayerSkin.get(this.getProfile()));
      }
   }
}
