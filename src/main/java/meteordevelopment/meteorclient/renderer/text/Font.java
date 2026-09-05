package meteordevelopment.meteorclient.renderer.text;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import meteordevelopment.meteorclient.renderer.Mesh;
import meteordevelopment.meteorclient.utils.render.ByteTexture;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTTPackContext;
import org.lwjgl.stb.STBTTPackRange;
import org.lwjgl.stb.STBTTPackedchar;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.stb.STBTTPackedchar.Buffer;
import org.lwjgl.system.MemoryStack;

public class Font {
   public AbstractTexture texture;
   private final int height;
   private final float scale;
   private final float ascent;
   private final Int2ObjectOpenHashMap<Font.CharData> charMap = new Int2ObjectOpenHashMap();
   private static final int size = 2048;

   public Font(ByteBuffer buffer, int height) {
      this.height = height;
      STBTTFontinfo fontInfo = STBTTFontinfo.create();
      STBTruetype.stbtt_InitFont(fontInfo, buffer);
      ByteBuffer bitmap = BufferUtils.createByteBuffer(4194304);
      Buffer[] cdata = new Buffer[]{
         STBTTPackedchar.create(95),
         STBTTPackedchar.create(96),
         STBTTPackedchar.create(128),
         STBTTPackedchar.create(144),
         STBTTPackedchar.create(256),
         STBTTPackedchar.create(1)
      };
      STBTTPackContext packContext = STBTTPackContext.create();
      STBTruetype.stbtt_PackBegin(packContext, bitmap, 2048, 2048, 0, 1);
      org.lwjgl.stb.STBTTPackRange.Buffer packRange = STBTTPackRange.create(cdata.length);
      packRange.put(STBTTPackRange.create().set((float)height, 32, null, 95, cdata[0], (byte)2, (byte)2));
      packRange.put(STBTTPackRange.create().set((float)height, 160, null, 96, cdata[1], (byte)2, (byte)2));
      packRange.put(STBTTPackRange.create().set((float)height, 256, null, 128, cdata[2], (byte)2, (byte)2));
      packRange.put(STBTTPackRange.create().set((float)height, 880, null, 144, cdata[3], (byte)2, (byte)2));
      packRange.put(STBTTPackRange.create().set((float)height, 1024, null, 256, cdata[4], (byte)2, (byte)2));
      packRange.put(STBTTPackRange.create().set((float)height, 8734, null, 1, cdata[5], (byte)2, (byte)2));
      packRange.flip();
      STBTruetype.stbtt_PackFontRanges(packContext, buffer, 0, packRange);
      STBTruetype.stbtt_PackEnd(packContext);
      this.texture = new ByteTexture(2048, 2048, bitmap, ByteTexture.Format.A, ByteTexture.Filter.Linear, ByteTexture.Filter.Linear);
      this.scale = STBTruetype.stbtt_ScaleForPixelHeight(fontInfo, (float)height);
      MemoryStack stack = MemoryStack.stackPush();

      try {
         IntBuffer ascent = stack.mallocInt(1);
         STBTruetype.stbtt_GetFontVMetrics(fontInfo, ascent, null, null);
         this.ascent = (float)ascent.get(0);
      } catch (Throwable var16) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var15) {
               var16.addSuppressed(var15);
            }
         }

         throw var16;
      }

      if (stack != null) {
         stack.close();
      }

      for (int i = 0; i < cdata.length; i++) {
         Buffer cbuf = cdata[i];
         int offset = ((STBTTPackRange)packRange.get(i)).first_unicode_codepoint_in_range();

         for (int j = 0; j < cbuf.capacity(); j++) {
            STBTTPackedchar packedChar = (STBTTPackedchar)cbuf.get(j);
            float ipw = 4.8828125E-4F;
            float iph = 4.8828125E-4F;
            this.charMap
               .put(
                  j + offset,
                  new Font.CharData(
                     packedChar.xoff(),
                     packedChar.yoff(),
                     packedChar.xoff2(),
                     packedChar.yoff2(),
                     (float)packedChar.x0() * ipw,
                     (float)packedChar.y0() * iph,
                     (float)packedChar.x1() * ipw,
                     (float)packedChar.y1() * iph,
                     packedChar.xadvance()
                  )
               );
         }
      }
   }

   public double getWidth(String string, int length) {
      double width = 0.0;

      for (int i = 0; i < length; i++) {
         int cp = string.charAt(i);
         Font.CharData c = (Font.CharData)this.charMap.get(cp);
         if (c == null) {
            c = (Font.CharData)this.charMap.get(32);
         }

         width += (double)c.xAdvance;
      }

      return width;
   }

   public int getHeight() {
      return this.height;
   }

   public double render(Mesh mesh, String string, double x, double y, Color color, double scale) {
      y += (double)(this.ascent * this.scale) * scale;

      for (int i = 0; i < string.length(); i++) {
         int cp = string.charAt(i);
         Font.CharData c = (Font.CharData)this.charMap.get(cp);
         if (c == null) {
            c = (Font.CharData)this.charMap.get(32);
         }

         mesh.quad(
            mesh.vec2(x + (double)c.x0 * scale, y + (double)c.y0 * scale).vec2((double)c.u0, (double)c.v0).color(color).next(),
            mesh.vec2(x + (double)c.x0 * scale, y + (double)c.y1 * scale).vec2((double)c.u0, (double)c.v1).color(color).next(),
            mesh.vec2(x + (double)c.x1 * scale, y + (double)c.y1 * scale).vec2((double)c.u1, (double)c.v1).color(color).next(),
            mesh.vec2(x + (double)c.x1 * scale, y + (double)c.y0 * scale).vec2((double)c.u1, (double)c.v0).color(color).next()
         );
         x += (double)c.xAdvance * scale;
      }

      return x;
   }

   private static record CharData(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, float xAdvance) {
   }
}
