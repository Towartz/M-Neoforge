package meteordevelopment.meteorclient.systems.accounts;

import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ServicesKeyType;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.mixin.FileCacheAccessor;
import meteordevelopment.meteorclient.mixin.MinecraftClientAccessor;
import meteordevelopment.meteorclient.mixin.PlayerSkinProviderAccessor;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import meteordevelopment.meteorclient.utils.misc.NbtException;
import net.minecraft.Util;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.client.resources.SkinManager.TextureCache;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.SignatureValidator;

public abstract class Account<T extends Account<?>> implements ISerializable<T> {
   protected AccountType type;
   protected String name;
   protected final AccountCache cache;

   protected Account(AccountType type, String name) {
      this.type = type;
      this.name = name;
      this.cache = new AccountCache();
   }

   public abstract boolean fetchInfo();

   public boolean login() {
      YggdrasilAuthenticationService authenticationService = new YggdrasilAuthenticationService(((MinecraftClientAccessor)MeteorClient.mc).getProxy());
      applyLoginEnvironment(authenticationService, authenticationService.createMinecraftSessionService());
      return true;
   }

   public String getUsername() {
      return this.cache.username.isEmpty() ? this.name : this.cache.username;
   }

   public AccountType getType() {
      return this.type;
   }

   public AccountCache getCache() {
      return this.cache;
   }

   public static void setSession(User session) {
      MinecraftClientAccessor mca = (MinecraftClientAccessor)MeteorClient.mc;
      mca.setSession(session);
      UserApiService apiService = mca.getAuthenticationService().createUserApiService(session.getAccessToken());
      mca.setUserApiService(apiService);
      mca.setSocialInteractionsManager(new PlayerSocialManager(MeteorClient.mc, apiService));
      mca.setProfileKeys(ProfileKeyPairManager.create(apiService, session, MeteorClient.mc.gameDirectory.toPath()));
      mca.setAbuseReportContext(ReportingContext.create(ReportEnvironment.local(), apiService));
      mca.setGameProfileFuture(
         CompletableFuture.supplyAsync(
            () -> MeteorClient.mc.getMinecraftSessionService().fetchProfile(MeteorClient.mc.getUser().getProfileId(), true), Util.ioPool()
         )
      );
   }

   public static void applyLoginEnvironment(YggdrasilAuthenticationService authService, MinecraftSessionService sessService) {
      MinecraftClientAccessor mca = (MinecraftClientAccessor)MeteorClient.mc;
      mca.setAuthenticationService(authService);
      SignatureValidator.from(authService.getServicesKeySet(), ServicesKeyType.PROFILE_KEY);
      mca.setSessionService(sessService);
      TextureCache skinCache = ((PlayerSkinProviderAccessor)MeteorClient.mc.getSkinManager()).getSkinCache();
      Path skinCachePath = ((FileCacheAccessor)skinCache).getDirectory();
      mca.setSkinProvider(new SkinManager(MeteorClient.mc.getTextureManager(), skinCachePath, sessService, MeteorClient.mc));
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.type.name());
      tag.putString("name", this.name);
      tag.put("cache", this.cache.toTag());
      return tag;
   }

   public T fromTag(CompoundTag tag) {
      if (tag.contains("name") && tag.contains("cache")) {
         this.name = tag.getString("name");
         this.cache.fromTag(tag.getCompound("cache"));
         return (T)this;
      } else {
         throw new NbtException();
      }
   }
}
