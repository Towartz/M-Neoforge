package meteordevelopment.meteorclient.mixin;

import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import java.net.Proxy;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ResourceLoadStateTracker;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.resources.SkinManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({Minecraft.class})
public interface MinecraftClientAccessor {
   @Accessor("fps")
   static int getFps() {
      return 0;
   }

   @Mutable
   @Accessor("user")
   void setSession(User var1);

   @Accessor("proxy")
   Proxy getProxy();

   @Accessor("reloadStateTracker")
   ResourceLoadStateTracker getResourceReloadLogger();

   @Invoker("startAttack")
   boolean leftClick();

   @Mutable
   @Accessor("profileKeyPairManager")
   void setProfileKeys(ProfileKeyPairManager var1);

   @Accessor("authenticationService")
   YggdrasilAuthenticationService getAuthenticationService();

   @Mutable
   @Accessor
   void setUserApiService(UserApiService var1);

   @Mutable
   @Accessor("minecraftSessionService")
   void setSessionService(MinecraftSessionService var1);

   @Mutable
   @Accessor("authenticationService")
   void setAuthenticationService(YggdrasilAuthenticationService var1);

   @Mutable
   @Accessor("skinManager")
   void setSkinProvider(SkinManager var1);

   @Mutable
   @Accessor("playerSocialManager")
   void setSocialInteractionsManager(PlayerSocialManager var1);

   @Mutable
   @Accessor("reportingContext")
   void setAbuseReportContext(ReportingContext var1);

   @Mutable
   @Accessor("profileFuture")
   void setGameProfileFuture(CompletableFuture<ProfileResult> var1);
}
