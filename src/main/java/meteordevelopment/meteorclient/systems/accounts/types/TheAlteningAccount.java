package meteordevelopment.meteorclient.systems.accounts.types;

import com.mojang.authlib.Environment;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import de.florianmichael.waybackauthlib.InvalidCredentialsException;
import de.florianmichael.waybackauthlib.WaybackAuthLib;
import java.util.Optional;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.mixin.MinecraftClientAccessor;
import meteordevelopment.meteorclient.mixin.YggdrasilMinecraftSessionServiceAccessor;
import meteordevelopment.meteorclient.systems.accounts.Account;
import meteordevelopment.meteorclient.systems.accounts.AccountType;
import meteordevelopment.meteorclient.systems.accounts.TokenAccount;
import meteordevelopment.meteorclient.utils.misc.NbtException;
import net.minecraft.client.User;
import net.minecraft.client.User.Type;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

public class TheAlteningAccount extends Account<TheAlteningAccount> implements TokenAccount {
   private static final Environment ENVIRONMENT = new Environment("http://sessionserver.thealtening.com", "http://authserver.thealtening.com", "The Altening");
   private static final YggdrasilAuthenticationService SERVICE = new YggdrasilAuthenticationService(
      ((MinecraftClientAccessor)MeteorClient.mc).getProxy(), ENVIRONMENT
   );
   private String token;
   @Nullable
   private WaybackAuthLib auth;

   public TheAlteningAccount(String token) {
      super(AccountType.TheAltening, token);
      this.token = token;
   }

   @Override
   public boolean fetchInfo() {
      this.auth = this.getAuth();

      try {
         this.auth.logIn();
         this.cache.username = this.auth.getCurrentProfile().getName();
         this.cache.uuid = this.auth.getCurrentProfile().getId().toString();
         this.cache.loadHead();
         return true;
      } catch (InvalidCredentialsException var2) {
         MeteorClient.LOG.error("Invalid TheAltening credentials.");
         return false;
      } catch (Exception var3) {
         MeteorClient.LOG.error("Failed to fetch info for TheAltening account!");
         return false;
      }
   }

   @Override
   public boolean login() {
      if (this.auth == null) {
         return false;
      } else {
         applyLoginEnvironment(
            SERVICE,
            YggdrasilMinecraftSessionServiceAccessor.createYggdrasilMinecraftSessionService(SERVICE.getServicesKeySet(), SERVICE.getProxy(), ENVIRONMENT)
         );

         try {
            setSession(
               new User(
                  this.auth.getCurrentProfile().getName(),
                  this.auth.getCurrentProfile().getId(),
                  this.auth.getAccessToken(),
                  Optional.empty(),
                  Optional.empty(),
                  Type.MOJANG
               )
            );
            return true;
         } catch (Exception var2) {
            MeteorClient.LOG.error("Failed to login with TheAltening.");
            return false;
         }
      }
   }

   private WaybackAuthLib getAuth() {
      WaybackAuthLib auth = new WaybackAuthLib(ENVIRONMENT.servicesHost());
      auth.setUsername(this.name);
      auth.setPassword("Meteor on Crack!");
      return auth;
   }

   @Override
   public String getToken() {
      return this.token;
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.type.name());
      tag.putString("name", this.name);
      tag.putString("token", this.token);
      tag.put("cache", this.cache.toTag());
      return tag;
   }

   public TheAlteningAccount fromTag(CompoundTag tag) {
      if (tag.contains("name") && tag.contains("cache") && tag.contains("token")) {
         this.name = tag.getString("name");
         this.token = tag.getString("token");
         this.cache.fromTag(tag.getCompound("cache"));
         return this;
      } else {
         throw new NbtException();
      }
   }
}
