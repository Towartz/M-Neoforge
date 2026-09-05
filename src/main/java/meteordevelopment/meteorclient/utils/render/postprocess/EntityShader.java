package meteordevelopment.meteorclient.utils.render.postprocess;

import com.mojang.blaze3d.pipeline.RenderTarget;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.mixin.WorldRendererAccessor;
import net.minecraft.client.renderer.LevelRenderer;

public abstract class EntityShader extends PostProcessShader {
   private RenderTarget prevBuffer;

   @Override
   protected void preDraw() {
      LevelRenderer worldRenderer = MeteorClient.mc.levelRenderer;
      WorldRendererAccessor wra = (WorldRendererAccessor)worldRenderer;
      this.prevBuffer = worldRenderer.entityTarget();
      wra.setEntityOutlinesFramebuffer(this.framebuffer);
      this.framebuffer.clear(net.minecraft.client.Minecraft.ON_OSX);
      this.framebuffer.bindWrite(false);
   }

   @Override
   protected void postDraw() {
      if (this.prevBuffer != null) {
         LevelRenderer worldRenderer = MeteorClient.mc.levelRenderer;
         WorldRendererAccessor wra = (WorldRendererAccessor)worldRenderer;
         wra.setEntityOutlinesFramebuffer(this.prevBuffer);
         this.prevBuffer = null;
      }
      MeteorClient.mc.getMainRenderTarget().bindWrite(false);
   }

   public void endRender() {
      this.endRender(() -> this.vertexConsumerProvider.endOutlineBatch());
   }
}
