package meteordevelopment.meteorclient.utils.render.postprocess;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Collections;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.renderer.GL;
import meteordevelopment.meteorclient.renderer.PostProcessRenderer;
import meteordevelopment.meteorclient.renderer.Shader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;

public abstract class PostProcessShader {
   public OutlineBufferSource vertexConsumerProvider;
   public RenderTarget framebuffer;
   protected Shader shader;

   private static final class DiscardingBufferSource extends MultiBufferSource.BufferSource {
      private static final VertexConsumer NOOP = new VertexConsumer() {
         @Override public VertexConsumer addVertex(float x, float y, float z) { return this; }
         @Override public VertexConsumer setColor(int red, int green, int blue, int alpha) { return this; }
         @Override public VertexConsumer setUv(float u, float v) { return this; }
         @Override public VertexConsumer setUv1(int u, int v) { return this; }
         @Override public VertexConsumer setUv2(int u, int v) { return this; }
         @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
      };

      public DiscardingBufferSource() {
         super(new ByteBufferBuilder(1536), Collections.emptyNavigableMap());
      }

      @Override
      public VertexConsumer getBuffer(RenderType renderType) {
         return NOOP;
      }
   }

   public void init(String frag) {
      this.vertexConsumerProvider = new OutlineBufferSource(new DiscardingBufferSource());
      this.framebuffer = new TextureTarget(MeteorClient.mc.getWindow().getWidth(), MeteorClient.mc.getWindow().getHeight(), false, Minecraft.ON_OSX);
      this.shader = new Shader("post-process/base.vert", "post-process/" + frag + ".frag");
   }

   protected abstract boolean shouldDraw();

   public abstract boolean shouldDraw(Entity var1);

   protected void preDraw() {
   }

   protected void postDraw() {
   }

   protected abstract void setUniforms();

   public void beginRender() {
      if (this.shouldDraw()) {
         this.framebuffer.clear(Minecraft.ON_OSX);
         MeteorClient.mc.getMainRenderTarget().bindWrite(true);
      }
   }

   public void endRender(Runnable draw) {
      if (this.shouldDraw()) {
         this.preDraw();
         draw.run();
         this.postDraw();
         MeteorClient.mc.getMainRenderTarget().bindWrite(true);
         GL.bindTexture(this.framebuffer.getColorTextureId(), 0);
         this.shader.bind();
         this.shader.set("u_Size", (double)MeteorClient.mc.getWindow().getWidth(), (double)MeteorClient.mc.getWindow().getHeight());
         this.shader.set("u_Texture", 0);
         this.shader.set("u_Time", GLFW.glfwGetTime());
         this.setUniforms();
         PostProcessRenderer.render();
         PostProcessRenderer.endRender();
         GL.useProgram(0);
         GL.bindTexture(0);
         GL.bindTexture(0, 1);
         GL.resetTextureSlot();
         MeteorClient.mc.getMainRenderTarget().bindWrite(true);
      }
   }

   public void onResized(int width, int height) {
      if (this.framebuffer != null) {
         this.framebuffer.resize(width, height, Minecraft.ON_OSX);
      }
   }
}
