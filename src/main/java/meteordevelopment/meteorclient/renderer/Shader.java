package meteordevelopment.meteorclient.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.apache.commons.io.IOUtils;
import org.joml.Matrix4f;

public class Shader {
   public static Shader BOUND;
   private final int id;
   private final Object2IntMap<String> uniformLocations = new Object2IntOpenHashMap();

   public Shader(String vertPath, String fragPath) {
      int vert = GL.createShader(35633);
      GL.shaderSource(vert, this.read(vertPath));
      String vertError = GL.compileShader(vert);
      if (vertError != null) {
         MeteorClient.LOG.error("Failed to compile vertex shader (" + vertPath + "): " + vertError);
         throw new RuntimeException("Failed to compile vertex shader (" + vertPath + "): " + vertError);
      } else {
         int frag = GL.createShader(35632);
         GL.shaderSource(frag, this.read(fragPath));
         String fragError = GL.compileShader(frag);
         if (fragError != null) {
            MeteorClient.LOG.error("Failed to compile fragment shader (" + fragPath + "): " + fragError);
            throw new RuntimeException("Failed to compile fragment shader (" + fragPath + "): " + fragError);
         } else {
            this.id = GL.createProgram();
            String programError = GL.linkProgram(this.id, vert, frag);
            if (programError != null) {
               MeteorClient.LOG.error("Failed to link program: " + programError);
               throw new RuntimeException("Failed to link program: " + programError);
            } else {
               GL.deleteShader(vert);
               GL.deleteShader(frag);
            }
         }
      }
   }

   private String read(String path) {
      try {
         if (MeteorClient.mc != null && MeteorClient.mc.getResourceManager() != null) {
            Optional<Resource> res = MeteorClient.mc.getResourceManager().getResource(MeteorClient.identifier("shaders/" + path));
            if (res.isEmpty()) {
               res = MeteorClient.mc.getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath("meteor-client", "shaders/" + path));
            }
            if (res.isPresent()) {
               try (InputStream in = res.get().open()) {
                  return IOUtils.toString(in, StandardCharsets.UTF_8);
               }
            }
         }

         InputStream classIn = Shader.class.getResourceAsStream("/assets/meteor_client/shaders/" + path);
         if (classIn == null) {
            classIn = Shader.class.getResourceAsStream("/assets/meteor-client/shaders/" + path);
         }
         if (classIn != null) {
            try (InputStream in = classIn) {
               return IOUtils.toString(in, StandardCharsets.UTF_8);
            }
         }

         throw new IllegalStateException("Could not find shader resource: " + path);
      } catch (IOException var3) {
         throw new IllegalStateException("Could not read shader '" + path + "'", var3);
      }
   }

   public void bind() {
      GL.useProgram(this.id);
      BOUND = this;
   }

   private int getLocation(String name) {
      if (this.uniformLocations.containsKey(name)) {
         return this.uniformLocations.getInt(name);
      } else {
         int location = GL.getUniformLocation(this.id, name);
         this.uniformLocations.put(name, location);
         return location;
      }
   }

   public void set(String name, boolean v) {
      GL.uniformInt(this.getLocation(name), v ? 1 : 0);
   }

   public void set(String name, int v) {
      GL.uniformInt(this.getLocation(name), v);
   }

   public void set(String name, double v) {
      GL.uniformFloat(this.getLocation(name), (float)v);
   }

   public void set(String name, double v1, double v2) {
      GL.uniformFloat2(this.getLocation(name), (float)v1, (float)v2);
   }

   public void set(String name, Color color) {
      GL.uniformFloat4(this.getLocation(name), (float)color.r / 255.0F, (float)color.g / 255.0F, (float)color.b / 255.0F, (float)color.a / 255.0F);
   }

   public void set(String name, Matrix4f mat) {
      GL.uniformMatrix(this.getLocation(name), mat);
   }

   public void setDefaults() {
      this.set("u_Proj", RenderSystem.getProjectionMatrix());
      this.set("u_ModelView", RenderSystem.getModelViewStack());
   }
}
