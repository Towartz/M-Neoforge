package meteordevelopment.meteorclient.utils.render.postprocess;

import com.mojang.blaze3d.platform.TextureUtil;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Optional;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.ResourcePacksReloadedEvent;
import meteordevelopment.meteorclient.renderer.GL;
import meteordevelopment.meteorclient.renderer.Texture;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Chams;
import meteordevelopment.meteorclient.utils.PostInit;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

public class ChamsShader extends EntityShader {
   private static final String[] FILE_FORMATS = new String[]{"png", "jpg"};
   private static Texture IMAGE_TEX;
   private static Chams chams;

   public ChamsShader() {
      MeteorClient.EVENT_BUS.subscribe(ChamsShader.class);
   }

   @PostInit
   public static void load() {
      try {
         ByteBuffer data = null;

         for (String fileFormat : FILE_FORMATS) {
            InputStream in = null;
            if (MeteorClient.mc != null && MeteorClient.mc.getResourceManager() != null) {
               Optional<Resource> optional = MeteorClient.mc.getResourceManager().getResource(MeteorClient.identifier("textures/chams." + fileFormat));
               if (optional.isEmpty()) {
                  optional = MeteorClient.mc.getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath("meteor-client", "textures/chams." + fileFormat));
               }
               if (optional.isPresent()) {
                  in = optional.get().open();
               }
            }
            if (in == null) {
               in = ChamsShader.class.getResourceAsStream("/assets/meteor_client/textures/chams." + fileFormat);
               if (in == null) {
                  in = ChamsShader.class.getResourceAsStream("/assets/meteor-client/textures/chams." + fileFormat);
               }
            }
            if (in != null) {
               try (InputStream s = in) {
                  data = TextureUtil.readResource(s);
               }
               break;
            }
         }

         if (data == null) {
            return;
         }

         data.rewind();
         MemoryStack stack = MemoryStack.stackPush();

         try {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer comp = stack.mallocInt(1);
            STBImage.stbi_set_flip_vertically_on_load(true);
            ByteBuffer image = STBImage.stbi_load_from_memory(data, width, height, comp, 3);
            IMAGE_TEX = new Texture();
            IMAGE_TEX.upload(width.get(0), height.get(0), image, Texture.Format.RGB, Texture.Filter.Nearest, Texture.Filter.Nearest, false);
            STBImage.stbi_image_free(image);
            STBImage.stbi_set_flip_vertically_on_load(false);
         } catch (Throwable var7) {
            if (stack != null) {
               try {
                  stack.close();
               } catch (Throwable var6) {
                  var7.addSuppressed(var6);
               }
            }

            throw var7;
         }

         if (stack != null) {
            stack.close();
         }
      } catch (IOException var8) {
         var8.printStackTrace();
      }
   }

   @EventHandler
   private static void onResourcePacksReloaded(ResourcePacksReloadedEvent event) {
      load();
   }

   @Override
   protected void setUniforms() {
      this.shader.set("u_Color", chams.shaderColor.get());
      if (chams.isShader() && chams.shader.get() == Chams.Shader.Image && IMAGE_TEX != null && IMAGE_TEX.isValid()) {
         IMAGE_TEX.bind(1);
         this.shader.set("u_TextureI", 1);
      }
   }

   @Override
   protected void postDraw() {
      super.postDraw();
      GL.bindTexture(0, 1);
      GL.resetTextureSlot();
   }

   @Override
   protected boolean shouldDraw() {
      if (chams == null) {
         chams = Modules.get().get(Chams.class);
      }

      return chams.isShader();
   }

   @Override
   public boolean shouldDraw(Entity entity) {
      if (!this.shouldDraw()) {
         return false;
      } else if (entity == MeteorClient.mc.cameraEntity && MeteorClient.mc.options.getCameraType().isFirstPerson()) {
         return false;
      } else {
         return chams.entities.get().contains(entity.getType()) && (entity != MeteorClient.mc.player || !chams.ignoreSelfDepth.get());
      }
   }
}
