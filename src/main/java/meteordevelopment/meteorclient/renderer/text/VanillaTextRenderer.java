package meteordevelopment.meteorclient.renderer.text;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

public class VanillaTextRenderer implements TextRenderer {
   public static final VanillaTextRenderer INSTANCE = new VanillaTextRenderer();
   private final ByteBufferBuilder buffer = new ByteBufferBuilder(2048);
   private final BufferSource immediate = MultiBufferSource.immediate(this.buffer);
   private final PoseStack matrices = new PoseStack();
   private final Matrix4f emptyMatrix = new Matrix4f();
   public double scale = 2.0;
   public boolean scaleIndividually;
   private boolean building;
   private double alpha = 1.0;

   private VanillaTextRenderer() {
   }

   @Override
   public void setAlpha(double a) {
      this.alpha = a;
   }

   @Override
   public double getWidth(String text, int length, boolean shadow) {
      if (text.isEmpty()) {
         return 0.0;
      } else {
         if (length != text.length()) {
            text = text.substring(0, length);
         }

         return (double)(MeteorClient.mc.font.width(text) + (shadow ? 1 : 0)) * this.scale;
      }
   }

   @Override
   public double getHeight(boolean shadow) {
      return (double)(9 + (shadow ? 1 : 0)) * this.scale;
   }

   @Override
   public void begin(double scale, boolean scaleOnly, boolean big) {
      if (this.building) {
         throw new RuntimeException("VanillaTextRenderer.begin() called twice");
      } else {
         this.scale = scale * 2.0;
         this.building = true;
      }
   }

   @Override
   public double render(String text, double x, double y, Color color, boolean shadow) {
      boolean wasBuilding = this.building;
      if (!wasBuilding) {
         this.begin();
      }

      x += 0.5 * this.scale;
      y += 0.5 * this.scale;
      int preA = color.a;
      color.a = (int)((double)color.a * this.alpha);
      Matrix4f matrix = this.emptyMatrix;
      if (this.scaleIndividually) {
         this.matrices.pushPose();
         this.matrices.scale((float)this.scale, (float)this.scale, 1.0F);
         matrix = this.matrices.last().pose();
      }

      double x2 = (double)MeteorClient.mc
         .font
         .drawInBatch(
            text, (float)(x / this.scale), (float)(y / this.scale), color.getPacked(), shadow, matrix, this.immediate, DisplayMode.NORMAL, 0, 15728880
         );
      if (this.scaleIndividually) {
         this.matrices.popPose();
      }

      color.a = preA;
      if (!wasBuilding) {
         this.end();
      }

      return (x2 - 1.0) * this.scale;
   }

   @Override
   public boolean isBuilding() {
      return this.building;
   }

   @Override
   public void end(PoseStack matrices) {
      if (!this.building) {
         throw new RuntimeException("VanillaTextRenderer.end() called without calling begin()");
      } else {
         Matrix4fStack matrixStack = RenderSystem.getModelViewStack();
         RenderSystem.disableDepthTest();
         matrixStack.pushMatrix();
         if (matrices != null) {
            matrixStack.mul(matrices.last().pose());
         }

         if (!this.scaleIndividually) {
            matrixStack.scale((float)this.scale, (float)this.scale, 1.0F);
         }

         RenderSystem.applyModelViewMatrix();
         this.immediate.endBatch();
         matrixStack.popMatrix();
         RenderSystem.enableDepthTest();
         RenderSystem.applyModelViewMatrix();
         this.scale = 2.0;
         this.building = false;
      }
   }
}
