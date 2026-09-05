package de.florianmichael.waybackauthlib;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.client.MinecraftClient;
import com.mojang.authlib.properties.Property;
import java.net.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class WaybackAuthLib {
   public static final String YGG_PROD = "https://authserver.mojang.com/";
   private static final String ROUTE_AUTHENTICATE = "authenticate";
   private static final String ROUTE_REFRESH = "refresh";
   private static final String ROUTE_INVALIDATE = "invalidate";
   private static final String ROUTE_VALIDATE = "validate";
   private final URI baseURI;
   private final String clientToken;
   private final MinecraftClient client;
   private String username;
   private String password;
   private String accessToken;
   private String userId;
   private boolean loggedIn;
   private GameProfile currentProfile;
   private List<Property> properties = new ArrayList<>();
   private List<GameProfile> profiles = new ArrayList<>();

   public WaybackAuthLib() {
      this("https://authserver.mojang.com/", "");
   }

   public WaybackAuthLib(String authHost) {
      this(authHost, "");
   }

   public WaybackAuthLib(String authHost, String clientToken) {
      this(authHost, clientToken, Proxy.NO_PROXY);
   }

   public WaybackAuthLib(String authHost, String clientToken, Proxy proxy) {
      if (authHost == null) {
         throw new IllegalArgumentException("Authentication is null");
      } else {
         if (!authHost.endsWith("/")) {
            authHost = authHost + "/";
         }

         this.baseURI = URI.create(authHost);
         if (clientToken == null) {
            throw new IllegalArgumentException("ClientToken is null");
         } else {
            this.clientToken = clientToken;
            this.client = MinecraftClient.unauthenticated(Objects.requireNonNullElse(proxy, Proxy.NO_PROXY));
         }
      }
   }

   public void logIn() throws Exception {
      if (this.username != null && !this.username.isEmpty()) {
         boolean refreshAccessToken = this.accessToken != null && !this.accessToken.isEmpty();
         boolean newAuthentication = this.password != null && !this.password.isEmpty();
         if (!refreshAccessToken && !newAuthentication) {
            throw new InvalidCredentialsException("Invalid password or access token.");
         } else {
            WaybackAuthLib.AuthenticateRefreshResponse response;
            if (refreshAccessToken) {
               response = (WaybackAuthLib.AuthenticateRefreshResponse)this.client
                  .post(
                     this.baseURI.resolve("refresh").toURL(),
                     new WaybackAuthLib.RefreshRequest(this.clientToken, this.accessToken, null),
                     WaybackAuthLib.AuthenticateRefreshResponse.class
                  );
            } else {
               response = (WaybackAuthLib.AuthenticateRefreshResponse)this.client
                  .post(
                     this.baseURI.resolve("authenticate").toURL(),
                     new WaybackAuthLib.AuthenticationRequest(WaybackAuthLib.Agent.MINECRAFT, this.username, this.password, this.clientToken),
                     WaybackAuthLib.AuthenticateRefreshResponse.class
                  );
            }

            if (response == null) {
               throw new InvalidRequestException("Server didn't sent a response.");
            } else if (!response.clientToken.equals(this.clientToken)) {
               throw new InvalidRequestException("Server token and provided token doesn't match.");
            } else {
               if (response.user != null && response.user.id != null) {
                  this.userId = response.user.id;
               } else {
                  this.userId = this.getUsername();
               }

               this.accessToken = response.accessToken;
               this.profiles = response.availableProfiles != null ? Arrays.asList(response.availableProfiles) : Collections.emptyList();
               this.currentProfile = response.selectedProfile;
               this.properties.clear();
               if (response.user != null && response.user.properties != null) {
                  this.properties.addAll(response.user.properties);
               }

               this.loggedIn = true;
            }
         }
      } else {
         throw new InvalidCredentialsException("Invalid username.");
      }
   }

   public boolean checkTokenValidity() {
      WaybackAuthLib.ValidateRequest request = new WaybackAuthLib.ValidateRequest(this.accessToken, this.clientToken);

      try {
         this.client.post(this.baseURI.resolve("validate").toURL(), request, WaybackAuthLib.Response.class);
         return true;
      } catch (Exception var3) {
         return false;
      }
   }

   public void logOut() throws Exception {
      WaybackAuthLib.InvalidateRequest request = new WaybackAuthLib.InvalidateRequest(this.clientToken, this.accessToken);
      WaybackAuthLib.Response response = (WaybackAuthLib.Response)this.client
         .post(this.baseURI.resolve("invalidate").toURL(), request, WaybackAuthLib.Response.class);
      if (!this.loggedIn) {
         throw new IllegalStateException("Cannot log out while not logged in.");
      } else if (response != null && response.error != null && !response.error.isEmpty()) {
         throw new InvalidRequestException(response.error, response.errorMessage, response.cause);
      } else {
         this.accessToken = null;
         this.loggedIn = false;
         this.currentProfile = null;
         this.properties = new ArrayList<>();
         this.profiles = new ArrayList<>();
      }
   }

   public String getUsername() {
      return this.username;
   }

   public void setUsername(String username) {
      if (this.loggedIn && this.currentProfile != null) {
         throw new IllegalStateException("Cannot change username whilst logged in & online");
      } else {
         this.username = username;
      }
   }

   public String getPassword() {
      return this.password;
   }

   public void setPassword(String password) {
      if (this.loggedIn && this.currentProfile != null) {
         throw new IllegalStateException("Cannot set password whilst logged in & online");
      } else {
         this.password = password;
      }
   }

   public String getAccessToken() {
      return this.accessToken;
   }

   public void setAccessToken(String accessToken) {
      if (this.loggedIn && this.currentProfile != null) {
         throw new IllegalStateException("Cannot set access token whilst logged in & online");
      } else {
         this.accessToken = accessToken;
      }
   }

   public String getUserId() {
      return this.userId;
   }

   public boolean isLoggedIn() {
      return this.loggedIn;
   }

   public GameProfile getCurrentProfile() {
      return this.currentProfile;
   }

   public List<Property> getProperties() {
      return this.properties;
   }

   public List<GameProfile> getProfiles() {
      return this.profiles;
   }

   @Override
   public String toString() {
      return "WaybackAuthLib{baseURI="
         + this.baseURI
         + ", clientToken='"
         + this.clientToken
         + "', client="
         + this.client
         + ", username='"
         + this.username
         + "', password='"
         + this.password
         + "', accessToken='"
         + this.accessToken
         + "', userId='"
         + this.userId
         + "', loggedIn="
         + this.loggedIn
         + ", currentProfile="
         + this.currentProfile
         + ", properties="
         + this.properties
         + ", profiles="
         + this.profiles
         + "}";
   }

   private static class Agent {
      public static final WaybackAuthLib.Agent MINECRAFT = new WaybackAuthLib.Agent("Minecraft", 1);
      public String name;
      public int version;

      protected Agent(String name, int version) {
         this.name = name;
         this.version = version;
      }
   }

   private static class AuthenticateRefreshResponse extends WaybackAuthLib.Response {
      public String accessToken;
      public String clientToken;
      public GameProfile selectedProfile;
      public GameProfile[] availableProfiles;
      public WaybackAuthLib.User user;
   }

   private static class AuthenticationRequest {
      public WaybackAuthLib.Agent agent;
      public String username;
      public String password;
      public String clientToken;
      private boolean requestUser;

      protected AuthenticationRequest(WaybackAuthLib.Agent agent, String username, String password, String clientToken) {
         this.agent = agent;
         this.username = username;
         this.password = password;
         this.clientToken = clientToken;
         this.requestUser = true;
      }
   }

   private static class InvalidateRequest {
      public String clientToken;
      public String accessToken;

      protected InvalidateRequest(String clientToken, String accessToken) {
         this.clientToken = clientToken;
         this.accessToken = accessToken;
      }
   }

   private static class RefreshRequest {
      public String clientToken;
      public String accessToken;
      public GameProfile selectedProfile;
      public boolean requestUser;

      protected RefreshRequest(String clientToken, String accessToken, GameProfile selectedProfile) {
         this.clientToken = clientToken;
         this.accessToken = accessToken;
         this.selectedProfile = selectedProfile;
         this.requestUser = true;
      }
   }

   private static class Response {
      public String error;
      public String errorMessage;
      public String cause;
   }

   private static class User {
      public String id;
      public List<Property> properties;
   }

   private static class ValidateRequest {
      public String clientToken;
      public String accessToken;

      public ValidateRequest(String accessToken, String clientToken) {
         this.clientToken = clientToken;
         this.accessToken = accessToken;
      }
   }
}
