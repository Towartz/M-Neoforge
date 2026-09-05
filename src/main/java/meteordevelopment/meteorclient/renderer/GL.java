package meteordevelopment.meteorclient.renderer;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.BufferUploader;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.ArrayDeque;
import java.util.Deque;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.mixin.BufferRendererAccessor;
import meteordevelopment.meteorclient.mixininterface.ICapabilityTracker;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL32C;

public class GL {
   private static final FloatBuffer MAT = BufferUtils.createFloatBuffer(16);

   public static class DirectCapabilityTracker implements ICapabilityTracker {
      private final int cap;
      private final java.util.function.Consumer<Boolean> setter;

      public DirectCapabilityTracker(int cap, java.util.function.Consumer<Boolean> setter) {
         this.cap = cap;
         this.setter = setter;
      }

      @Override
      public boolean get() {
         return GL32C.glIsEnabled(this.cap);
      }

      @Override
      public void set(boolean enabled) {
         this.setter.accept(enabled);
      }
   }

   private static final class GlState {
      final boolean blend;
      final boolean depth;
      final boolean cull;
      final boolean scissor;

      GlState(boolean blend, boolean depth, boolean cull, boolean scissor) {
         this.blend = blend;
         this.depth = depth;
         this.cull = cull;
         this.scissor = scissor;
      }
   }

   private static final Deque<GlState> STATE_STACK = new ArrayDeque<>();

   private static final ICapabilityTracker DEPTH = new DirectCapabilityTracker(GL32C.GL_DEPTH_TEST, s -> {
      if (s) GlStateManager._enableDepthTest(); else GlStateManager._disableDepthTest();
   });
   private static final ICapabilityTracker BLEND = new DirectCapabilityTracker(GL32C.GL_BLEND, s -> {
      if (s) GlStateManager._enableBlend(); else GlStateManager._disableBlend();
   });
   private static final ICapabilityTracker CULL = new DirectCapabilityTracker(GL32C.GL_CULL_FACE, s -> {
      if (s) GlStateManager._enableCull(); else GlStateManager._disableCull();
   });
   private static final ICapabilityTracker SCISSOR = new DirectCapabilityTracker(GL32C.GL_SCISSOR_TEST, s -> {
      if (s) GlStateManager._enableScissorTest(); else GlStateManager._disableScissorTest();
   });

   private static boolean depthSaved;
   private static boolean blendSaved;
   private static boolean cullSaved;
   private static boolean scissorSaved;
   public static int CURRENT_IBO;
   private static int prevIbo;

   private GL() {
   }

   public static int genVertexArray() {
      return GlStateManager._glGenVertexArrays();
   }

   public static int genBuffer() {
      return GlStateManager._glGenBuffers();
   }

   public static int genTexture() {
      return GlStateManager._genTexture();
   }

   public static int genFramebuffer() {
      return GlStateManager.glGenFramebuffers();
   }

   public static void deleteBuffer(int buffer) {
      GlStateManager._glDeleteBuffers(buffer);
   }

   public static void deleteVertexArray(int vao) {
      GlStateManager._glDeleteVertexArrays(vao);
   }

   public static void deleteShader(int shader) {
      GlStateManager.glDeleteShader(shader);
   }

   public static void deleteTexture(int id) {
      GlStateManager._deleteTexture(id);
   }

   public static void deleteFramebuffer(int fbo) {
      GlStateManager._glDeleteFramebuffers(fbo);
   }

   public static void deleteProgram(int program) {
      GlStateManager.glDeleteProgram(program);
   }

   public static void bindVertexArray(int vao) {
      GlStateManager._glBindVertexArray(vao);
      BufferUploader.reset();
   }

   public static void bindVertexBuffer(int vbo) {
      GlStateManager._glBindBuffer(34962, vbo);
   }

   public static void bindIndexBuffer(int ibo) {
      GlStateManager._glBindBuffer(34963, ibo);
   }

   public static void bindFramebuffer(int fbo) {
      GlStateManager._glBindFramebuffer(36160, fbo);
   }

   public static void bufferData(int target, ByteBuffer data, int usage) {
      GlStateManager._glBufferData(target, data, usage);
   }

   public static void drawElements(int mode, int first, int type) {
      GlStateManager._drawElements(mode, first, type, 0L);
   }

   public static void enableVertexAttribute(int i) {
      GlStateManager._enableVertexAttribArray(i);
   }

   public static void vertexAttribute(int index, int size, int type, boolean normalized, int stride, long pointer) {
      GlStateManager._vertexAttribPointer(index, size, type, normalized, stride, pointer);
   }

   public static int createShader(int type) {
      return GlStateManager.glCreateShader(type);
   }

   public static void shaderSource(int shader, String source) {
      GlStateManager.glShaderSource(shader, List.of(source));
   }

   public static String compileShader(int shader) {
      GlStateManager.glCompileShader(shader);
      return GlStateManager.glGetShaderi(shader, 35713) == 0 ? GlStateManager.glGetShaderInfoLog(shader, 512) : null;
   }

   public static int createProgram() {
      return GlStateManager.glCreateProgram();
   }

   public static String linkProgram(int program, int vertShader, int fragShader) {
      GlStateManager.glAttachShader(program, vertShader);
      GlStateManager.glAttachShader(program, fragShader);
      GlStateManager.glLinkProgram(program);
      return GlStateManager.glGetProgrami(program, 35714) == 0 ? GlStateManager.glGetProgramInfoLog(program, 512) : null;
   }

   public static void useProgram(int program) {
      GlStateManager._glUseProgram(program);
   }

   public static void viewport(int x, int y, int width, int height) {
      GlStateManager._viewport(x, y, width, height);
   }

   public static int getUniformLocation(int program, String name) {
      return GlStateManager._glGetUniformLocation(program, name);
   }

   public static void uniformInt(int location, int v) {
      GlStateManager._glUniform1i(location, v);
   }

   public static void uniformFloat(int location, float v) {
      GL32C.glUniform1f(location, v);
   }

   public static void uniformFloat2(int location, float v1, float v2) {
      GL32C.glUniform2f(location, v1, v2);
   }

   public static void uniformFloat3(int location, float v1, float v2, float v3) {
      GL32C.glUniform3f(location, v1, v2, v3);
   }

   public static void uniformFloat4(int location, float v1, float v2, float v3, float v4) {
      GL32C.glUniform4f(location, v1, v2, v3, v4);
   }

   public static void uniformFloat3Array(int location, float[] v) {
      GL32C.glUniform3fv(location, v);
   }

   public static void uniformMatrix(int location, Matrix4f v) {
      MAT.clear();
      v.get(MAT);
      MAT.rewind();
      GlStateManager._glUniformMatrix4(location, false, MAT);
   }

   public static void pixelStore(int name, int param) {
      GlStateManager._pixelStore(name, param);
   }

   public static void textureParam(int target, int name, int param) {
      GlStateManager._texParameter(target, name, param);
   }

   public static void textureImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
      GL32C.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
   }

   public static void defaultPixelStore() {
      pixelStore(3312, 0);
      pixelStore(3313, 0);
      pixelStore(3314, 0);
      pixelStore(32878, 0);
      pixelStore(3315, 0);
      pixelStore(3316, 0);
      pixelStore(32877, 0);
      pixelStore(3317, 4);
   }

   public static void generateMipmap(int target) {
      GL32C.glGenerateMipmap(target);
   }

   public static void framebufferTexture2D(int target, int attachment, int textureTarget, int texture, int level) {
      GlStateManager._glFramebufferTexture2D(target, attachment, textureTarget, texture, level);
   }

   public static void clear(int mask) {
      GlStateManager._clearColor(0.0F, 0.0F, 0.0F, 1.0F);
      GlStateManager._clear(mask, false);
   }

   public static void saveState() {
      depthSaved = DEPTH.get();
      blendSaved = BLEND.get();
      cullSaved = CULL.get();
      scissorSaved = SCISSOR.get();
      STATE_STACK.push(new GlState(blendSaved, depthSaved, cullSaved, scissorSaved));
   }

   public static void restoreState() {
      if (!STATE_STACK.isEmpty()) {
         GlState state = STATE_STACK.pop();
         DEPTH.set(state.depth);
         BLEND.set(state.blend);
         CULL.set(state.cull);
         SCISSOR.set(state.scissor);
      } else {
         DEPTH.set(depthSaved);
         BLEND.set(blendSaved);
         CULL.set(cullSaved);
         SCISSOR.set(scissorSaved);
      }

      disableLineSmooth();
      GlStateManager._glUseProgram(0);
      GlStateManager._glBindVertexArray(0);
      BufferUploader.reset();
   }

   public static void enableDepth() {
      GlStateManager._enableDepthTest();
   }

   public static void disableDepth() {
      GlStateManager._disableDepthTest();
   }

   public static void enableBlend() {
      GlStateManager._enableBlend();
      GlStateManager._blendEquation(GL14.GL_FUNC_ADD);
      GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
   }

   public static void disableBlend() {
      GlStateManager._disableBlend();
   }

   public static void enableCull() {
      GlStateManager._enableCull();
   }

   public static void disableCull() {
      GlStateManager._disableCull();
   }

   public static void enableScissorTest() {
      GlStateManager._enableScissorTest();
   }

   public static void disableScissorTest() {
      GlStateManager._disableScissorTest();
   }

   public static void enableLineSmooth() {
      GL32C.glEnable(2848);
      GL32C.glLineWidth(1.0F);
   }

   public static void disableLineSmooth() {
      GL32C.glDisable(2848);
   }

   public static void bindTexture(ResourceLocation id) {
      GlStateManager._activeTexture(33984);
      MeteorClient.mc.getTextureManager().bindForSetup(id);
   }

   public static void bindTexture(int i, int slot) {
      GlStateManager._activeTexture(33984 + slot);
      GlStateManager._bindTexture(i);
   }

   public static void bindTexture(int i) {
      bindTexture(i, 0);
   }

   public static void resetTextureSlot() {
      GlStateManager._activeTexture(33984);
   }

   public static void unbindProgram() {
      useProgram(0);
   }

   public static void unbindShader() {
      useProgram(0);
   }

   public static void unbindTexture() {
      bindTexture(0, 0);
   }

   public static void unbindTexture(int slot) {
      bindTexture(0, slot);
   }

   public static ICapabilityTracker getTracker(String fieldName) {
      if (fieldName == null) {
         return createFallbackTracker();
      }
      return switch (fieldName.toUpperCase()) {
         case "DEPTH" -> DEPTH;
         case "BLEND" -> BLEND;
         case "CULL" -> CULL;
         case "SCISSOR" -> SCISSOR;
         default -> createFallbackTracker();
      };
   }

   public static ICapabilityTracker createFallbackTracker() {
      return new ICapabilityTracker() {
         private boolean val;
         @Override public boolean get() { return this.val; }
         @Override public void set(boolean s) { this.val = s; }
      };
   }
}
