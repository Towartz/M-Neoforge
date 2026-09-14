package meteordevelopment.meteorclient.mixin.compat;

import com.corosus.watut.PlayerStatus;
import com.corosus.watut.PlayerStatusManagerClient;
import com.corosus.watut.config.ConfigClient;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.utils.compat.WatutCompat;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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

    @Redirect(
        method = {"shouldAnimate", "tickOtherPlayerClient"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z"),
        remap = false
    )
    private boolean redirectIsFirstPerson(CameraType cameraType) {
        if (WatutCompat.isFreecamSelfViewActive()) {
            return false;
        }
        return cameraType.isFirstPerson();
    }

    @Redirect(
        method = "tickOtherPlayerClient",
        at = @At(value = "FIELD", target = "Lcom/corosus/watut/config/ConfigClient;showGuisForYourOwnPlayerIn3rdPerson:Z"),
        remap = false
    )
    private boolean redirectShowGuisIn3rdPerson() {
        if (WatutCompat.isFreecamSelfViewActive()) {
            return true;
        }
        return ConfigClient.showGuisForYourOwnPlayerIn3rdPerson;
    }

    @Redirect(
        method = "setupRotationsHook",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;screen:Lnet/minecraft/client/gui/screens/Screen;"),
        remap = false
    )
    private Screen redirectScreen(Minecraft mc) {
        if (WatutCompat.isFreecamSelfViewActive()) {
            return null;
        }
        return mc.screen;
    }

    @Redirect(
        method = {"tickOtherPlayerClient", "setupRotationsHook"},
        at = @At(value = "INVOKE", target = "Lcom/corosus/watut/PlayerStatusManagerClient;getStatus(Lnet/minecraft/world/entity/player/Player;)Lcom/corosus/watut/PlayerStatus;"),
        remap = false
    )
    private PlayerStatus redirectGetStatus(PlayerStatusManagerClient instance, Player player) {
        if (player == MeteorClient.mc.player) {
            return instance.getStatusLocal();
        }
        return instance.getStatus(player);
    }

    @Redirect(
        method = "tickOtherPlayerClient",
        at = @At(value = "INVOKE", target = "Lcom/corosus/watut/PlayerStatusManagerClient;getStatusPrev(Lnet/minecraft/world/entity/player/Player;)Lcom/corosus/watut/PlayerStatus;"),
        remap = false
    )
    private PlayerStatus redirectGetStatusPrev(PlayerStatusManagerClient instance, Player player) {
        if (player == MeteorClient.mc.player) {
            return instance.getStatusPrevLocal();
        }
        return instance.getStatusPrev(player);
    }
}
