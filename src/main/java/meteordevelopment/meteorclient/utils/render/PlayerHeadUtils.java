package meteordevelopment.meteorclient.utils.render;

import com.google.gson.Gson;
import java.util.Base64;
import java.util.UUID;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.accounts.TexturesJson;
import meteordevelopment.meteorclient.systems.accounts.UuidToProfileResponse;
import meteordevelopment.meteorclient.utils.PostInit;
import meteordevelopment.meteorclient.utils.network.Http;

public class PlayerHeadUtils {
   public static PlayerHeadTexture STEVE_HEAD;

   private PlayerHeadUtils() {
   }

   @PostInit
   public static void init() {
      STEVE_HEAD = new PlayerHeadTexture();
   }

   public static PlayerHeadTexture fetchHead(UUID id) {
      if (id == null) {
         return null;
      } else {
         String url = getSkinUrl(id);
         return url != null ? new PlayerHeadTexture(url) : null;
      }
   }

   public static String getSkinUrl(UUID id) {
      UuidToProfileResponse res2 = Http.get("https://sessionserver.mojang.com/session/minecraft/profile/" + id)
         .exceptionHandler(e -> MeteorClient.LOG.error("Could not contact mojang session servers.", e))
         .sendJson(UuidToProfileResponse.class);
      if (res2 == null) {
         return null;
      } else {
         String base64Textures = res2.getPropertyValue("textures");
         if (base64Textures == null) {
            return null;
         } else {
            try {
               TexturesJson textures = new Gson().fromJson(new String(Base64.getDecoder().decode(base64Textures)), TexturesJson.class);
               return (textures != null && textures.textures != null && textures.textures.SKIN != null) ? textures.textures.SKIN.url : null;
            } catch (Throwable ignored) {
               return null;
            }
         }
      }
   }
}
