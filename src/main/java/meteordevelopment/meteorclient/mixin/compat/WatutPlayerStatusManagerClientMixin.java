package meteordevelopment.meteorclient.mixin.compat;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.utils.compat.WatutCompat;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.corosus.watut.PlayerStatusManagerClient", remap = false)
public abstract class WatutPlayerStatusManagerClientMixin {
    @Inject(method = "tickLocalPlayerClient", at = @At("HEAD"), cancellable = true, remap = false)
    private void onTickLocalPlayerClient(Player player, CallbackInfo ci) {
        if (Config.get() != null && Config.get().hideWatutInGui.get()) {
            if (WatutCompat.isMeteorScreen(MeteorClient.mc.screen)) {
                ci.cancel();
            }
        }
    }
}
