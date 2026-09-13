package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.ExploitPreventer;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.DownloadQueue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DownloadQueue.class)
public abstract class DownloadQueueMixin {
    @Shadow @Final private Path cacheDir;

    @ModifyExpressionValue(
        method = "lambda$runDownload$0",
        at = @At(value = "INVOKE", target = "Ljava/nio/file/Path;resolve(Ljava/lang/String;)Ljava/nio/file/Path;")
    )
    private Path onResolvePackPath(Path original) {
        if (Modules.get() != null) {
            ExploitPreventer ep = Modules.get().get(ExploitPreventer.class);
            if (ep != null && ep.isActive() && ep.antiFingerprint.get()) {
                try {
                    UUID playerUuid = Minecraft.getInstance().getUser().getProfileId();
                    if (playerUuid != null && original != null && original.getFileName() != null) {
                        Path userDir = this.cacheDir.resolve(playerUuid.toString());
                        Files.createDirectories(userDir);
                        return userDir.resolve(original.getFileName().toString());
                    }
                } catch (Throwable ignored) {}
            }
        }
        return original;
    }
}
