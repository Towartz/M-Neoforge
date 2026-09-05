package meteordevelopment.meteorclient.gui.renderer;

import com.mojang.blaze3d.platform.GlStateManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.operations.TextOperation;
import meteordevelopment.meteorclient.gui.renderer.packer.GuiTexture;
import meteordevelopment.meteorclient.gui.renderer.packer.TexturePacker;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.renderer.GL;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.Texture;
import meteordevelopment.meteorclient.utils.PostInit;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Pool;
import meteordevelopment.meteorclient.utils.render.ByteTexture;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public class GuiRenderer {
   private static final Color WHITE = new Color(255, 255, 255);
   private static final TexturePacker TEXTURE_PACKER = new TexturePacker();
   private static ByteTexture TEXTURE;
   public static GuiTexture CIRCLE;
   public static GuiTexture TRIANGLE;
   public static GuiTexture EDIT;
   public static GuiTexture RESET;
   public static GuiTexture FAVORITE_NO;
   public static GuiTexture FAVORITE_YES;
   public GuiTheme theme;
   private final Renderer2D r = new Renderer2D(false);
   private final Renderer2D rTex = new Renderer2D(true);
   private final Pool<Scissor> scissorPool = new Pool<>(Scissor::new);
   private final Stack<Scissor> scissorStack = new Stack<>();
   private final Pool<TextOperation> textPool = new Pool<>(TextOperation::new);
   private final List<TextOperation> texts = new ArrayList<>();
   private final List<Runnable> postTasks = new ArrayList<>();
   public String tooltip;
   public String lastTooltip;
   public WWidget tooltipWidget;
   private double tooltipAnimProgress;
   private GuiGraphics drawContext;

   public static GuiTexture addTexture(ResourceLocation id) {
      return TEXTURE_PACKER.add(id);
   }

   @PostInit
   public static void init() {
      CIRCLE = addTexture(MeteorClient.identifier("textures/icons/gui/circle.png"));
      TRIANGLE = addTexture(MeteorClient.identifier("textures/icons/gui/triangle.png"));
      EDIT = addTexture(MeteorClient.identifier("textures/icons/gui/edit.png"));
      RESET = addTexture(MeteorClient.identifier("textures/icons/gui/reset.png"));
      FAVORITE_NO = addTexture(MeteorClient.identifier("textures/icons/gui/favorite_no.png"));
      FAVORITE_YES = addTexture(MeteorClient.identifier("textures/icons/gui/favorite_yes.png"));
      TEXTURE = TEXTURE_PACKER.pack();
   }

   public void begin(GuiGraphics drawContext) {
      this.drawContext = drawContext;
      GL.saveState();
      GL.enableBlend();
      GlStateManager._blendEquation(GL14.GL_FUNC_ADD);
      GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
      GlStateManager._disableDepthTest();
      GlStateManager._depthMask(true);
      GlStateManager._colorMask(true, true, true, true);
      GlStateManager._disableCull();
      GL.resetTextureSlot();
      GL.enableScissorTest();
      this.scissorStart(0.0, 0.0, (double)Utils.getWindowWidth(), (double)Utils.getWindowHeight());
   }

   public void end() {
      this.scissorEnd();

      for (Runnable task : this.postTasks) {
         task.run();
      }

      this.postTasks.clear();
      GL.disableScissorTest();
      GL.useProgram(0);
      GL.bindVertexArray(0);
      GL.bindTexture(0);
      GL.resetTextureSlot();
      com.mojang.blaze3d.vertex.BufferUploader.reset();
      GL.restoreState();
   }

   public void beginRender() {
      this.r.begin();
      this.rTex.begin();
   }

   public void endRender() {
      this.r.end();
      this.rTex.end();
      this.r.render(this.drawContext.pose());
      GL.bindTexture(TEXTURE != null ? TEXTURE.getId() : 0);
      this.rTex.render(this.drawContext.pose());
      this.theme.textRenderer().begin(this.theme.scale(1.0));

      for (TextOperation text : this.texts) {
         if (!text.title) {
            text.run(this.textPool);
         }
      }

      this.theme.textRenderer().end(this.drawContext.pose());
      this.theme.textRenderer().begin(this.theme.scale(1.25));

      for (TextOperation textx : this.texts) {
         if (textx.title) {
            textx.run(this.textPool);
         }
      }

      this.theme.textRenderer().end(this.drawContext.pose());
      this.texts.clear();
      GL.bindTexture(0);
      GL.useProgram(0);
      GL.bindVertexArray(0);
      com.mojang.blaze3d.vertex.BufferUploader.reset();
   }

   public void scissorStart(double x, double y, double width, double height) {
      if (!this.scissorStack.isEmpty()) {
         Scissor parent = this.scissorStack.peek();
         double newX = Math.max(x, (double)parent.x);
         double newY = Math.max(y, (double)parent.y);
         double newX2 = Math.min(x + width, (double)(parent.x + parent.width));
         double newY2 = Math.min(y + height, (double)(parent.y + parent.height));
         x = newX;
         y = newY;
         width = Math.max(0.0, newX2 - newX);
         height = Math.max(0.0, newY2 - newY);

         parent.apply();
         this.endRender();
      }

      this.scissorStack.push(this.scissorPool.get().set(x, y, width, height));
      this.beginRender();
   }

   public void scissorEnd() {
      Scissor scissor = this.scissorStack.pop();
      scissor.apply();
      this.endRender();

      for (Runnable task : scissor.postTasks) {
         task.run();
      }

      if (!this.scissorStack.isEmpty()) {
         this.scissorStack.peek().apply();
         this.beginRender();
      }

      this.scissorPool.free(scissor);
   }

   public boolean renderTooltip(GuiGraphics drawContext, double mouseX, double mouseY, double delta) {
      this.tooltipAnimProgress = this.tooltipAnimProgress + (double)(this.tooltip != null ? 1 : -1) * delta * 14.0;
      this.tooltipAnimProgress = Mth.clamp(this.tooltipAnimProgress, 0.0, 1.0);
      boolean toReturn = false;
      if (this.tooltipAnimProgress > 0.0) {
         if (this.tooltip != null && !this.tooltip.equals(this.lastTooltip)) {
            this.tooltipWidget = this.theme.tooltip(this.tooltip);
            this.tooltipWidget.init();
         }

         this.tooltipWidget.move(-this.tooltipWidget.x + mouseX + 12.0, -this.tooltipWidget.y + mouseY + 12.0);
         this.setAlpha(this.tooltipAnimProgress);
         this.begin(drawContext);
         this.tooltipWidget.render(this, mouseX, mouseY, delta);
         this.end();
         this.setAlpha(1.0);
         this.lastTooltip = this.tooltip;
         toReturn = true;
      }

      this.tooltip = null;
      return toReturn;
   }

   public void setAlpha(double a) {
      this.r.setAlpha(a);
      this.rTex.setAlpha(a);
      this.theme.textRenderer().setAlpha(a);
   }

   public void tooltip(String text) {
      this.tooltip = text;
   }

   public void quad(double x, double y, double width, double height, Color cTopLeft, Color cTopRight, Color cBottomRight, Color cBottomLeft) {
      this.r.quad(x, y, width, height, cTopLeft, cTopRight, cBottomRight, cBottomLeft);
   }

   public void quad(double x, double y, double width, double height, Color colorLeft, Color colorRight) {
      this.quad(x, y, width, height, colorLeft, colorRight, colorRight, colorLeft);
   }

   public void quad(double x, double y, double width, double height, Color color) {
      this.quad(x, y, width, height, color, color);
   }

   public void quad(WWidget widget, Color color) {
      this.quad(widget.x, widget.y, widget.width, widget.height, color);
   }

   public void quad(double x, double y, double width, double height, GuiTexture texture, Color color) {
      this.rTex.texQuad(x, y, width, height, texture.get(width, height), color);
   }

   public void rotatedQuad(double x, double y, double width, double height, double rotation, GuiTexture texture, Color color) {
      this.rTex.texQuad(x, y, width, height, rotation, texture.get(width, height), color);
   }

   public void triangle(double x1, double y1, double x2, double y2, double x3, double y3, Color color) {
      this.r.triangle(x1, y1, x2, y2, x3, y3, color);
   }

   public void text(String text, double x, double y, Color color, boolean title) {
      this.texts.add(this.getOp(this.textPool, x, y, color).set(text, this.theme.textRenderer(), title));
   }

   public void texture(double x, double y, double width, double height, double rotation, Texture texture) {
      this.post(() -> {
         this.rTex.begin();
         this.rTex.texQuad(x, y, width, height, rotation, 0.0, 0.0, 1.0, 1.0, WHITE);
         this.rTex.end();
         texture.bind();
         this.rTex.render(this.drawContext.pose());
      });
   }

   public void post(Runnable task) {
      this.scissorStack.peek().postTasks.add(task);
   }

   public void item(ItemStack itemStack, int x, int y, float scale, boolean overlay) {
      RenderUtils.drawItem(this.drawContext, itemStack, x, y, scale, overlay);
   }

   public void absolutePost(Runnable task) {
      this.postTasks.add(task);
   }

   private <T extends GuiRenderOperation<T>> T getOp(Pool<T> pool, double x, double y, Color color) {
      T op = (T)pool.get();
      op.set(x, y, color);
      return op;
   }
}
