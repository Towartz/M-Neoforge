package meteordevelopment.meteorclient.utils.render;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.renderer.Fonts;
import meteordevelopment.meteorclient.renderer.text.BuiltinFontFace;
import meteordevelopment.meteorclient.renderer.text.FontFace;
import meteordevelopment.meteorclient.renderer.text.FontFamily;
import meteordevelopment.meteorclient.renderer.text.FontInfo;
import meteordevelopment.meteorclient.renderer.text.SystemFontFace;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.Util;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;

public class FontUtils {
   private FontUtils() {
   }

   public static FontInfo getSysFontInfo(File file) {
      return getFontInfo(stream(file));
   }

   public static FontInfo getBuiltinFontInfo(String builtin) {
      return getFontInfo(stream(builtin));
   }

   public static FontInfo getFontInfo(InputStream stream) {
      if (stream == null) {
         return null;
      } else {
         byte[] bytes = Utils.readBytes(stream);
         if (bytes.length < 5) {
            return null;
         } else if (bytes[0] == 0 && bytes[1] == 1 && bytes[2] == 0 && bytes[3] == 0 && bytes[4] == 0) {
            ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length).put(bytes).flip();
            STBTTFontinfo fontInfo = STBTTFontinfo.create();
            if (!STBTruetype.stbtt_InitFont(fontInfo, buffer)) {
               return null;
            } else {
               ByteBuffer nameBuffer = STBTruetype.stbtt_GetFontNameString(fontInfo, 3, 1, 1033, 1);
               ByteBuffer typeBuffer = STBTruetype.stbtt_GetFontNameString(fontInfo, 3, 1, 1033, 2);
               return typeBuffer != null && nameBuffer != null
                  ? new FontInfo(
                     StandardCharsets.UTF_16.decode(nameBuffer).toString(), FontInfo.Type.fromString(StandardCharsets.UTF_16.decode(typeBuffer).toString())
                  )
                  : null;
            }
         } else {
            return null;
         }
      }
   }

   public static Set<String> getSearchPaths() {
      Set<String> paths = new HashSet<>();
      paths.add(System.getProperty("java.home") + "/lib/fonts");

      for (File dir : getUFontDirs()) {
         if (dir.exists()) {
            paths.add(dir.getAbsolutePath());
         }
      }

      for (File dirx : getSFontDirs()) {
         if (dirx.exists()) {
            paths.add(dirx.getAbsolutePath());
         }
      }

      return paths;
   }

   public static List<File> getUFontDirs() {
      return switch (Util.getPlatform()) {
         case WINDOWS -> List.of(new File(System.getProperty("user.home") + "\\AppData\\Local\\Microsoft\\Windows\\Fonts"));
         case OSX -> List.of(new File(System.getProperty("user.home") + "/Library/Fonts/"));
         default -> List.of(new File(System.getProperty("user.home") + "/.local/share/fonts"), new File(System.getProperty("user.home") + "/.fonts"));
      };
   }

   public static List<File> getSFontDirs() {
      return switch (Util.getPlatform()) {
         case WINDOWS -> List.of(new File(System.getenv("SystemRoot") + "\\Fonts"));
         case OSX -> List.of(new File("/System/Library/Fonts/"));
         default -> List.of(new File("/usr/share/fonts/"));
      };
   }

   public static void loadBuiltin(List<FontFamily> fontList, String builtin) {
      FontInfo fontInfo = getBuiltinFontInfo(builtin);
      if (fontInfo != null) {
         FontFace fontFace = new BuiltinFontFace(fontInfo, builtin);
         if (!addFont(fontList, fontFace)) {
            MeteorClient.LOG.warn("Failed to load builtin font {}", fontFace);
         }
      }
   }

   public static void loadSystem(List<FontFamily> fontList, File dir) {
      if (dir.exists() && dir.isDirectory()) {
         File[] files = dir.listFiles(filex -> filex.isFile() && filex.getName().endsWith(".ttf") || filex.isDirectory());
         if (files != null) {
            for (File file : files) {
               if (file.isDirectory()) {
                  loadSystem(fontList, file);
               } else {
                  FontInfo fontInfo = getSysFontInfo(file);
                  if (fontInfo != null) {
                     boolean isBuiltin = false;

                     for (String builtinFont : Fonts.BUILTIN_FONTS) {
                        if (builtinFont.equals(fontInfo.family())) {
                           isBuiltin = true;
                           break;
                        }
                     }

                     if (!isBuiltin) {
                        FontFace fontFace = new SystemFontFace(fontInfo, file.toPath());
                        if (!addFont(fontList, fontFace)) {
                           MeteorClient.LOG.warn("Failed to load system font {}", fontFace);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public static boolean addFont(List<FontFamily> fontList, FontFace font) {
      if (font == null) {
         return false;
      } else {
         FontInfo info = font.info;
         FontFamily family = Fonts.getFamily(info.family());
         if (family == null) {
            family = new FontFamily(info.family());
            fontList.add(family);
         }

         return family.hasType(info.type()) ? false : family.addFont(font);
      }
   }

   public static InputStream stream(String builtin) {
      InputStream in = FontUtils.class.getResourceAsStream("/assets/meteor_client/fonts/" + builtin + ".ttf");
      if (in == null) {
         in = FontUtils.class.getResourceAsStream("/assets/meteor-client/fonts/" + builtin + ".ttf");
      }
      return in;
   }

   public static InputStream stream(File file) {
      try {
         return new FileInputStream(file);
      } catch (FileNotFoundException var2) {
         var2.printStackTrace();
         return null;
      }
   }
}
