package meteordevelopment.meteorclient.utils.render.postprocess;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.PreInit;
import net.minecraft.client.renderer.MultiBufferSource;

public class PostProcessShaders {
   public static EntityShader CHAMS;
   public static EntityShader ENTITY_OUTLINE;
   public static PostProcessShader STORAGE_OUTLINE;
   public static boolean rendering;

   private PostProcessShaders() {
   }

   @PreInit
   public static void init() {
      CHAMS = new ChamsShader();
      ENTITY_OUTLINE = new EntityOutlineShader();
      STORAGE_OUTLINE = new StorageOutlineShader();
   }

   public static void beginRender() {
      if (CHAMS != null) CHAMS.beginRender();
      if (ENTITY_OUTLINE != null) ENTITY_OUTLINE.beginRender();
      if (STORAGE_OUTLINE != null) STORAGE_OUTLINE.beginRender();
   }

   public static void endRender() {
      if (CHAMS != null) CHAMS.endRender();
      if (ENTITY_OUTLINE != null) ENTITY_OUTLINE.endRender();
   }

   public static void onResized(int width, int height) {
      if (MeteorClient.mc != null) {
         if (CHAMS != null) CHAMS.onResized(width, height);
         if (ENTITY_OUTLINE != null) ENTITY_OUTLINE.onResized(width, height);
         if (STORAGE_OUTLINE != null) STORAGE_OUTLINE.onResized(width, height);
      }
   }

   public static boolean isCustom(MultiBufferSource vcp) {
      return (CHAMS != null && vcp == CHAMS.vertexConsumerProvider) || (ENTITY_OUTLINE != null && vcp == ENTITY_OUTLINE.vertexConsumerProvider);
   }
}
