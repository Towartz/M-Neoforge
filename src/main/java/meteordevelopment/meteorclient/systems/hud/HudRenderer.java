package meteordevelopment.meteorclient.systems.hud;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.meteor.CustomFontChangedEvent;
import meteordevelopment.meteorclient.renderer.DrawMode;
import meteordevelopment.meteorclient.renderer.Fonts;
import meteordevelopment.meteorclient.renderer.GL;
import meteordevelopment.meteorclient.renderer.Mesh;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.ShaderMesh;
import meteordevelopment.meteorclient.renderer.Shaders;
import meteordevelopment.meteorclient.renderer.text.CustomTextRenderer;
import meteordevelopment.meteorclient.renderer.text.Font;
import meteordevelopment.meteorclient.renderer.text.VanillaTextRenderer;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.BufferUtils;

public class HudRenderer {
   public static final HudRenderer INSTANCE = new HudRenderer();
   private static final double SCALE_TO_HEIGHT = 0.05555555555555555;
   private final Hud hud = Hud.get();
   private final List<Runnable> postTasks = new ArrayList<>();
   private final Int2ObjectMap<HudRenderer.FontHolder> fontsInUse = new Int2ObjectOpenHashMap();
   private final LoadingCache<Integer, HudRenderer.FontHolder> fontCache = CacheBuilder.newBuilder()
      .maximumSize(4L)
      .expireAfterAccess(Duration.ofMinutes(10L))
      .removalListener(notification -> {
         if (notification.wasEvicted()) {
            ((HudRenderer.FontHolder)notification.getValue()).destroy();
         }
      })
      .build(CacheLoader.from(HudRenderer::loadFont));
   public GuiGraphics drawContext;
   public double delta;

   private HudRenderer() {
      MeteorClient.EVENT_BUS.subscribe(this);
   }

   public void begin(GuiGraphics drawContext) {
      Renderer2D.COLOR.begin();
      this.drawContext = drawContext;
      this.delta = Utils.frameTime;
      if (!this.hud.hasCustomFont()) {
         VanillaTextRenderer.INSTANCE.scaleIndividually = true;
         VanillaTextRenderer.INSTANCE.begin();
      }
   }

   public void end() {
      Renderer2D.COLOR.render(new PoseStack());
      if (this.hud.hasCustomFont()) {
         Iterator<HudRenderer.FontHolder> it = this.fontsInUse.values().iterator();

         while (it.hasNext()) {
            HudRenderer.FontHolder fontHolder = it.next();
            if (fontHolder.visited) {
               GL.bindTexture(fontHolder.font.texture.getId());
               fontHolder.getMesh().render(null);
            } else {
               it.remove();
               this.fontCache.put(fontHolder.font.getHeight(), fontHolder);
            }

            fontHolder.visited = false;
         }
      } else {
         VanillaTextRenderer.INSTANCE.end();
         VanillaTextRenderer.INSTANCE.scaleIndividually = false;
      }

      for (Runnable task : this.postTasks) {
         task.run();
      }

      this.postTasks.clear();
      this.drawContext = null;
      GL.useProgram(0);
      GL.bindVertexArray(0);
      GL.bindTexture(0);
      com.mojang.blaze3d.vertex.BufferUploader.reset();
   }

   public void line(double x1, double y1, double x2, double y2, Color color) {
      Renderer2D.COLOR.line(x1, y1, x2, y2, color);
   }

   public void quad(double x, double y, double width, double height, Color color) {
      Renderer2D.COLOR.quad(x, y, width, height, color);
   }

   public void quad(double x, double y, double width, double height, Color cTopLeft, Color cTopRight, Color cBottomRight, Color cBottomLeft) {
      Renderer2D.COLOR.quad(x, y, width, height, cTopLeft, cTopRight, cBottomRight, cBottomLeft);
   }

   public void triangle(double x1, double y1, double x2, double y2, double x3, double y3, Color color) {
      Renderer2D.COLOR.triangle(x1, y1, x2, y2, x3, y3, color);
   }

   public void texture(ResourceLocation id, double x, double y, double width, double height, Color color) {
      GL.bindTexture(id);
      Renderer2D.TEXTURE.begin();
      Renderer2D.TEXTURE.texQuad(x, y, width, height, color);
      Renderer2D.TEXTURE.render(null);
   }

   public double text(String text, double x, double y, Color color, boolean shadow, double scale) {
      if (scale == -1.0) {
         scale = this.hud.getTextScale();
      }

      if (!this.hud.hasCustomFont()) {
         VanillaTextRenderer.INSTANCE.scale = scale * 2.0;
         return VanillaTextRenderer.INSTANCE.render(text, x, y, color, shadow);
      } else {
         HudRenderer.FontHolder fontHolder = this.getFontHolder(scale, true);
         Font font = fontHolder.font;
         Mesh mesh = fontHolder.getMesh();
         double width;
         if (shadow) {
            int preShadowA = CustomTextRenderer.SHADOW_COLOR.a;
            CustomTextRenderer.SHADOW_COLOR.a = (int)((double)color.a / 255.0 * (double)preShadowA);
            width = font.render(mesh, text, x + 1.0, y + 1.0, CustomTextRenderer.SHADOW_COLOR, scale);
            font.render(mesh, text, x, y, color, scale);
            CustomTextRenderer.SHADOW_COLOR.a = preShadowA;
         } else {
            width = font.render(mesh, text, x, y, color, scale);
         }

         return width;
      }
   }

   public double text(String text, double x, double y, Color color, boolean shadow) {
      return this.text(text, x, y, color, shadow, -1.0);
   }

   public double textWidth(String text, boolean shadow, double scale) {
      if (text.isEmpty()) {
         return 0.0;
      } else if (this.hud.hasCustomFont()) {
         double width = this.getFont(scale).getWidth(text, text.length());
         return (width + (double)(shadow ? 1 : 0)) * (scale == -1.0 ? this.hud.getTextScale() : scale) + (double)(shadow ? 1 : 0);
      } else {
         VanillaTextRenderer.INSTANCE.scale = (scale == -1.0 ? this.hud.getTextScale() : scale) * 2.0;
         return VanillaTextRenderer.INSTANCE.getWidth(text, shadow);
      }
   }

   public double textWidth(String text, boolean shadow) {
      return this.textWidth(text, shadow, -1.0);
   }

   public double textWidth(String text, double scale) {
      return this.textWidth(text, false, scale);
   }

   public double textWidth(String text) {
      return this.textWidth(text, false, -1.0);
   }

   public double textHeight(boolean shadow, double scale) {
      if (this.hud.hasCustomFont()) {
         double height = (double)(this.getFont(scale).getHeight() + 1);
         return (height + (double)(shadow ? 1 : 0)) * (scale == -1.0 ? this.hud.getTextScale() : scale);
      } else {
         VanillaTextRenderer.INSTANCE.scale = (scale == -1.0 ? this.hud.getTextScale() : scale) * 2.0;
         return VanillaTextRenderer.INSTANCE.getHeight(shadow);
      }
   }

   public double textHeight(boolean shadow) {
      return this.textHeight(shadow, -1.0);
   }

   public double textHeight() {
      return this.textHeight(false, -1.0);
   }

   public void post(Runnable task) {
      this.postTasks.add(task);
   }

   public void item(ItemStack itemStack, int x, int y, float scale, boolean overlay, String countOverlay) {
      RenderUtils.drawItem(this.drawContext, itemStack, x, y, scale, overlay, countOverlay);
   }

   public void item(ItemStack itemStack, int x, int y, float scale, boolean overlay) {
      RenderUtils.drawItem(this.drawContext, itemStack, x, y, scale, overlay);
   }

   private HudRenderer.FontHolder getFontHolder(double scale, boolean render) {
      if (scale == -1.0) {
         scale = this.hud.getTextScale();
      }

      int height = (int)Math.round(scale / 0.05555555555555555);
      HudRenderer.FontHolder fontHolder = (HudRenderer.FontHolder)this.fontsInUse.get(height);
      if (fontHolder != null) {
         if (render) {
            fontHolder.visited = true;
         }

         return fontHolder;
      } else if (render) {
         fontHolder = (HudRenderer.FontHolder)this.fontCache.getIfPresent(height);
         if (fontHolder == null) {
            fontHolder = loadFont(height);
         } else {
            this.fontCache.invalidate(height);
         }

         this.fontsInUse.put(height, fontHolder);
         fontHolder.visited = true;
         return fontHolder;
      } else {
         return (HudRenderer.FontHolder)this.fontCache.getUnchecked(height);
      }
   }

   private Font getFont(double scale) {
      return this.getFontHolder(scale, false).font;
   }

   @EventHandler
   private void onCustomFontChanged(CustomFontChangedEvent event) {
      ObjectIterator var2 = this.fontsInUse.values().iterator();

      while (var2.hasNext()) {
         HudRenderer.FontHolder fontHolder = (HudRenderer.FontHolder)var2.next();
         fontHolder.destroy();
      }

      for (HudRenderer.FontHolder fontHolder : this.fontCache.asMap().values()) {
         fontHolder.destroy();
      }

      this.fontsInUse.clear();
      this.fontCache.invalidateAll();
   }

   private static HudRenderer.FontHolder loadFont(int height) {
      byte[] data = Utils.readBytes(Fonts.RENDERER.fontFace.toStream());
      ByteBuffer buffer = BufferUtils.createByteBuffer(data.length).put(data).flip();
      return new HudRenderer.FontHolder(new Font(buffer, height));
   }

   private static class FontHolder {
      public final Font font;
      public boolean visited;
      private Mesh mesh;

      public FontHolder(Font font) {
         this.font = font;
      }

      public Mesh getMesh() {
         if (this.mesh == null) {
            this.mesh = new ShaderMesh(Shaders.TEXT, DrawMode.Triangles, Mesh.Attrib.Vec2, Mesh.Attrib.Vec2, Mesh.Attrib.Color);
         }

         if (!this.mesh.isBuilding()) {
            this.mesh.begin();
         }

         return this.mesh;
      }

      public void destroy() {
         this.font.texture.releaseId();
         if (this.mesh != null) {
            this.mesh.destroy();
         }
      }
   }
}
