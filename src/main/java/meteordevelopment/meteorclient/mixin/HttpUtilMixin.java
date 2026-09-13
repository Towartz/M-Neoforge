package meteordevelopment.meteorclient.mixin;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import java.net.Proxy;
import java.net.URL;
import java.nio.file.Path;
import java.util.Map;
import javax.annotation.Nullable;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.ExploitPreventer;
import net.minecraft.util.HttpUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HttpUtil.class)
public abstract class HttpUtilMixin {
    @Inject(method = "downloadFile", at = @At("HEAD"), cancellable = true)
    private static void onDownloadFileHead(
        Path savePath,
        URL url,
        Map<String, String> headers,
        HashFunction hashFunction,
        @Nullable HashCode hashCode,
        int maxSize,
        Proxy proxy,
        HttpUtil.DownloadProgressListener listener,
        CallbackInfoReturnable<Path> cir
    ) {
        if (Modules.get() != null) {
            ExploitPreventer ep = Modules.get().get(ExploitPreventer.class);
            if (ep != null && ep.isActive() && ep.antiSsrf.get()) {
                if (ExploitPreventer.isLocalDownloadBlocked(url)) {
                    if (ep.alertOnExploit.get()) {
                        ep.warning("Blocked local SSRF resource pack exploit attempt: %s", url);
                    }
                    if (listener != null) {
                        listener.requestFinished(false);
                    }
                    throw new IllegalStateException("Blocked SSRF request to local address: " + url);
                }
            }
        }
    }
}
