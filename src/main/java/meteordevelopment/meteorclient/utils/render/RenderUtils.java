package meteordevelopment.meteorclient.utils.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.meteorclient.utils.PostInit;
import meteordevelopment.meteorclient.utils.misc.Pool;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class RenderUtils {
   public static Vec3 center = Vec3.ZERO;
   private static final Matrix4f PROJ_MODEL_INV = new Matrix4f();
   private static final Matrix4f MODEL_VIEW_MAT = new Matrix4f();
   private static final Vector4f NEAR_PT = new Vector4f();
   private static final Vector4f FAR_PT = new Vector4f();
   private static final Quaternionf SCRATCH_QUAT = new Quaternionf();
   private static final Pool<RenderUtils.RenderBlock> renderBlockPool = new Pool<>(RenderUtils.RenderBlock::new);
   private static final List<RenderUtils.RenderBlock> renderBlocks = new ArrayList<>();

   private RenderUtils() {
   }

   @PostInit
   public static void init() {
      MeteorClient.EVENT_BUS.subscribe(RenderUtils.class);
   }

   public static void drawItem(GuiGraphics drawContext, ItemStack itemStack, int x, int y, float scale, boolean overlay, String countOverride) {
      PoseStack matrices = drawContext.pose();
      matrices.pushPose();
      matrices.scale(scale, scale, 1.0F);
      matrices.translate(0.0F, 0.0F, 401.0F);
      int scaledX = (int)((float)x / scale);
      int scaledY = (int)((float)y / scale);
      drawContext.renderItem(itemStack, scaledX, scaledY);
      if (overlay) {
         drawContext.renderItemDecorations(MeteorClient.mc.font, itemStack, scaledX, scaledY, countOverride);
      }

      matrices.popPose();
   }

   public static void drawItem(GuiGraphics drawContext, ItemStack itemStack, int x, int y, float scale, boolean overlay) {
      drawItem(drawContext, itemStack, x, y, scale, overlay, null);
   }

   public static void updateScreenCenter() {
      updateScreenCenter(null);
   }

   public static void updateScreenCenter(Matrix4f modelView) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.gameRenderer == null) return;
      Camera camera = mc.gameRenderer.getMainCamera();
      if (camera == null) return;

      if (modelView != null) {
         MODEL_VIEW_MAT.set(modelView);
      } else {
         MODEL_VIEW_MAT.rotation(camera.rotation().conjugate(SCRATCH_QUAT));
      }

      PROJ_MODEL_INV.set(RenderSystem.getProjectionMatrix()).mul(MODEL_VIEW_MAT);
      PROJ_MODEL_INV.invert();

      // Unproject crosshair on near plane (z_ndc = -1.0) and far plane (z_ndc = 1.0)
      NEAR_PT.set(0.0F, 0.0F, -1.0F, 1.0F);
      PROJ_MODEL_INV.transform(NEAR_PT);

      FAR_PT.set(0.0F, 0.0F, 1.0F, 1.0F);
      PROJ_MODEL_INV.transform(FAR_PT);

      if (Math.abs(NEAR_PT.w) > 1e-6F && Math.abs(FAR_PT.w) > 1e-6F) {
         float nearX = NEAR_PT.x / NEAR_PT.w;
         float nearY = NEAR_PT.y / NEAR_PT.w;
         float nearZ = NEAR_PT.z / NEAR_PT.w;

         float farX = FAR_PT.x / FAR_PT.w;
         float farY = FAR_PT.y / FAR_PT.w;
         float farZ = FAR_PT.z / FAR_PT.w;

         // True direction of the optical axis line passing through the crosshair
         float dirX = farX - nearX;
         float dirY = farY - nearY;
         float dirZ = farZ - nearZ;

         float lenSq = dirX * dirX + dirY * dirY + dirZ * dirZ;
         if (lenSq > 1e-6F) {
            float invLen = (float)(1.0 / Math.sqrt((double)lenSq));
            dirX *= invLen;
            dirY *= invLen;
            dirZ *= invLen;

            Vector3f look = camera.getLookVector();
            if (dirX * look.x + dirY * look.y + dirZ * look.z < 0.0F) {
               dirX = -dirX;
               dirY = -dirY;
               dirZ = -dirZ;
            }

            // Place center along the true optical axis line in front of the camera:
            // nearPt + 0.5m forward along the optical axis.
            // Guaranteed by projective linearity to map strictly to (x_ndc = 0, y_ndc = 0)
            Vec3 camPos = camera.getPosition();
            center = new Vec3(
               camPos.x + (double)nearX + (double)dirX * 0.5,
               camPos.y + (double)nearY + (double)dirY * 0.5,
               camPos.z + (double)nearZ + (double)dirZ * 0.5
            );
            return;
         }
      }

      Vector3f look = camera.getLookVector();
      Vec3 camPos = camera.getPosition();
      center = new Vec3(camPos.x + (double)look.x, camPos.y + (double)look.y, camPos.z + (double)look.z);
   }

   public static void renderTickingBlock(
      BlockPos blockPos, Color sideColor, Color lineColor, ShapeMode shapeMode, int excludeDir, int duration, boolean fade, boolean shrink
   ) {
      for (int i = renderBlocks.size() - 1; i >= 0; i--) {
         RenderUtils.RenderBlock block = renderBlocks.get(i);
         if (block.pos.equals(blockPos)) {
            renderBlocks.remove(i);
            renderBlockPool.free(block);
         }
      }

      renderBlocks.add(renderBlockPool.get().set(blockPos, sideColor, lineColor, shapeMode, excludeDir, duration, fade, shrink));
   }

   @EventHandler
   private static void onTick(TickEvent.Pre event) {
      if (renderBlocks.isEmpty()) return;
      for (int i = renderBlocks.size() - 1; i >= 0; i--) {
         RenderUtils.RenderBlock block = renderBlocks.get(i);
         block.tick();
         if (block.ticks <= 0) {
            renderBlocks.remove(i);
            renderBlockPool.free(block);
         }
      }
   }

   @EventHandler
   private static void onRender(Render3DEvent event) {
      if (renderBlocks.isEmpty()) return;
      for (int i = 0; i < renderBlocks.size(); i++) {
         renderBlocks.get(i).render(event);
      }
   }

   @EventHandler
   private static void onGameLeft(GameLeftEvent event) {
      for (int i = 0; i < renderBlocks.size(); i++) {
         renderBlockPool.free(renderBlocks.get(i));
      }
      renderBlocks.clear();
   }

   public static class RenderBlock {
      public MutableBlockPos pos = new MutableBlockPos();
      public Color sideColor;
      public Color lineColor;
      public ShapeMode shapeMode;
      public int excludeDir;
      public int ticks;
      public int duration;
      public boolean fade;
      public boolean shrink;

      public RenderUtils.RenderBlock set(
         BlockPos blockPos, Color sideColor, Color lineColor, ShapeMode shapeMode, int excludeDir, int duration, boolean fade, boolean shrink
      ) {
         this.pos.set(blockPos);
         this.sideColor = sideColor;
         this.lineColor = lineColor;
         this.shapeMode = shapeMode;
         this.excludeDir = excludeDir;
         this.fade = fade;
         this.shrink = shrink;
         this.ticks = duration;
         this.duration = duration;
         return this;
      }

      public void tick() {
         this.ticks--;
      }

      public void render(Render3DEvent event) {
         int preSideA = this.sideColor.a;
         int preLineA = this.lineColor.a;
         double x1 = (double)this.pos.getX();
         double y1 = (double)this.pos.getY();
         double z1 = (double)this.pos.getZ();
         double x2 = (double)(this.pos.getX() + 1);
         double y2 = (double)(this.pos.getY() + 1);
         double z2 = (double)(this.pos.getZ() + 1);
         double d = (double)((float)this.ticks - event.tickDelta) / (double)this.duration;
         if (this.fade) {
            this.sideColor.a = (int)((double)this.sideColor.a * d);
            this.lineColor.a = (int)((double)this.lineColor.a * d);
         }

         if (this.shrink) {
            x1 += d;
            y1 += d;
            z1 += d;
            x2 -= d;
            y2 -= d;
            z2 -= d;
         }

         event.renderer.box(x1, y1, z1, x2, y2, z2, this.sideColor, this.lineColor, this.shapeMode, this.excludeDir);
         this.sideColor.a = preSideA;
         this.lineColor.a = preLineA;
      }
   }
}
