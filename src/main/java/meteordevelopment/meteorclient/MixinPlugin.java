package meteordevelopment.meteorclient;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import meteordevelopment.meteorclient.asm.Asm;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import sun.misc.Unsafe;

public class MixinPlugin implements IMixinConfigPlugin {
   private static final String mixinPackage = "meteordevelopment.meteorclient.mixin";
   private static boolean loaded;
   private static boolean isOriginsPresent;
   private static boolean isIndigoPresent;
   public static boolean isSodiumPresent;
   private static boolean isLithiumPresent;
   public static boolean isIrisPresent;
   private static boolean isVFPPresent;
   private static boolean isBaritonePresent;

   private static boolean doesClassExist(String className) {
      try {
         Class.forName(className, false, Thread.currentThread().getContextClassLoader());
         return true;
      } catch (Throwable t) {
         return false;
      }
   }

   private static boolean isModLoaded(String modId) {
      try {
         return net.neoforged.fml.loading.LoadingModList.get().getModFileById(modId) != null;
      } catch (Throwable t) {
         return false;
      }
   }

   private static Field findField(Class<?> clazz, String fieldName) {
      Class<?> current = clazz;
      while (current != null && current != Object.class) {
         try {
            Field f = current.getDeclaredField(fieldName);
            f.setAccessible(true);
            return f;
         } catch (NoSuchFieldException ignored) {
            current = current.getSuperclass();
         }
      }
      return null;
   }

   public void onLoad(String mixinPackage) {
      if (!loaded) {
         try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe) unsafeField.get(null);
            Asm.init();

            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Field delegateField = findField(classLoader.getClass(), "delegate");
            if (delegateField != null) {
               Object delegate = delegateField.get(classLoader);
               if (delegate != null) {
                  Field mixinTransformerField = findField(delegate.getClass(), "mixinTransformer");
                  if (mixinTransformerField != null) {
                     Asm.Transformer mixinTransformer = (Asm.Transformer) unsafe.allocateInstance(Asm.Transformer.class);
                     mixinTransformer.delegate = (IMixinTransformer) mixinTransformerField.get(delegate);
                     mixinTransformerField.set(delegate, mixinTransformer);
                     System.out.println("[Meteor] Successfully injected Asm.Transformer via sun.misc.Unsafe reflection!");
                  }
               }
            }
         } catch (Throwable t) {
            System.err.println("[Meteor] sun.misc.Unsafe ASM transformer reflection notice: " + t);
         }

         isIndigoPresent = false;
         isOriginsPresent = isModLoaded("origins");
         isSodiumPresent = isModLoaded("sodium") || isModLoaded("embeddium");
         isLithiumPresent = isModLoaded("lithium") || isModLoaded("radium");
         isIrisPresent = isModLoaded("iris") || isModLoaded("oculus");
         isVFPPresent = isModLoaded("viafabricplus") || isModLoaded("viaforge");
         isBaritonePresent = isModLoaded("baritone") || doesClassExist("baritone.api.BaritoneAPI");
         loaded = true;
      }
   }

   public String getRefMapperConfig() {
      return null;
   }

   public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
      if (!mixinClassName.startsWith("meteordevelopment.meteorclient.mixin")) {
         throw new RuntimeException("Mixin " + mixinClassName + " is not in the mixin package");
      } else if (mixinClassName.endsWith("PlayerEntityRendererMixin")) {
         return !isOriginsPresent;
      } else if (mixinClassName.startsWith("meteordevelopment.meteorclient.mixin.baritone")) {
         return isBaritonePresent;
      } else if (mixinClassName.startsWith("meteordevelopment.meteorclient.mixin.sodium")) {
         if (!isSodiumPresent) return false;
         if (mixinClassName.contains("FluidRendererImpl")) {
            return doesClassExist("net.caffeinemc.mods.sodium.fabric.render.FluidRendererImpl");
         }
         return true;
      } else if (mixinClassName.startsWith("meteordevelopment.meteorclient.mixin.indigo")) {
         return isIndigoPresent;
      } else if (mixinClassName.startsWith("meteordevelopment.meteorclient.mixin.lithium")) {
         return isLithiumPresent;
      } else if (mixinClassName.startsWith("meteordevelopment.meteorclient.mixin.compat")) {
         if (mixinClassName.contains("Ntgl")) {
            return isModLoaded("ntgl");
         }
         return true;
      } else {
         return mixinClassName.startsWith("meteordevelopment.meteorclient.mixin.viafabricplus") ? isVFPPresent : true;
      }
   }

   public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
   }

   public List<String> getMixins() {
      return null;
   }

   public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
   }

   public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
   }
}
