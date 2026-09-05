package meteordevelopment.meteorclient.utils.tooltip;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.animal.goat.Goat;
import org.joml.Quaternionf;

public class EntityTooltipComponent implements MeteorTooltipData, ClientTooltipComponent {
   protected final Entity entity;

   public EntityTooltipComponent(Entity entity) {
      this.entity = entity;
   }

   @Override
   public ClientTooltipComponent getComponent() {
      return this;
   }

   public int getHeight() {
      return 24;
   }

   public int getWidth(Font textRenderer) {
      return 60;
   }

   public void renderImage(Font textRenderer, int x, int y, GuiGraphics context) {
      PoseStack matrices = context.pose();
      matrices.pushPose();
      matrices.translate(15.0F, 2.0F, 0.0F);
      this.entity.setDeltaMovement(1.0, 1.0, 1.0);
      this.renderEntity(matrices, x, y);
      matrices.popPose();
   }

   protected void renderEntity(PoseStack matrices, int x, int y) {
      if (MeteorClient.mc.player != null) {
         float size = 24.0F;
         if ((double)Math.max(this.entity.getBbWidth(), this.entity.getBbHeight()) > 1.0) {
            size /= Math.max(this.entity.getBbWidth(), this.entity.getBbHeight());
         }

         Lighting.setupForFlatItems();
         matrices.pushPose();
         int yOffset = 16;
         if (this.entity instanceof Squid) {
            size = 16.0F;
            yOffset = 2;
         }

         matrices.translate((float)(x + 10), (float)(y + yOffset), 1050.0F);
         matrices.scale(1.0F, 1.0F, -1.0F);
         matrices.translate(0.0F, 0.0F, 1000.0F);
         matrices.scale(size, size, size);
         Quaternionf quaternion = Axis.ZP.rotationDegrees(180.0F);
         Quaternionf quaternion2 = Axis.XP.rotationDegrees(-10.0F);
         this.hamiltonProduct(quaternion, quaternion2);
         matrices.mulPose(quaternion);
         this.setupAngles();
         EntityRenderDispatcher entityRenderDispatcher = MeteorClient.mc.getEntityRenderDispatcher();
         quaternion2.conjugate();
         entityRenderDispatcher.overrideCameraOrientation(quaternion2);
         entityRenderDispatcher.setRenderShadow(false);
         BufferSource immediate = MeteorClient.mc.renderBuffers().bufferSource();
         this.entity.tickCount = MeteorClient.mc.player.tickCount;
         this.entity.setCustomNameVisible(false);
         entityRenderDispatcher.render(this.entity, 0.0, 0.0, 0.0, 0.0F, 1.0F, matrices, immediate, 15728880);
         immediate.endBatch();
         entityRenderDispatcher.setRenderShadow(true);
         matrices.popPose();
         Lighting.setupFor3DItems();
      }
   }

   public void hamiltonProduct(Quaternionf q, Quaternionf other) {
      float f = q.x();
      float g = q.y();
      float h = q.z();
      float i = q.w();
      float j = other.x();
      float k = other.y();
      float l = other.z();
      float m = other.w();
      q.x = i * j + f * m + g * l - h * k;
      q.y = i * k - f * l + g * m + h * j;
      q.z = i * l + f * k - g * j + h * m;
      q.w = i * m - f * j - g * k - h * l;
   }

   protected void setupAngles() {
      float yaw = (float)System.currentTimeMillis() / 10.0F % 360.0F;
      this.entity.setYRot(yaw);
      this.entity.setYHeadRot(yaw);
      this.entity.setXRot(0.0F);
      if (this.entity instanceof LivingEntity livingEntity) {
         if (this.entity instanceof Goat) {
            livingEntity.yHeadRot = yaw;
         }

         livingEntity.yBodyRot = yaw;
      }
   }
}
