package meteordevelopment.meteorclient.utils;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import meteordevelopment.meteorclient.addons.AddonManager;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import sun.misc.Unsafe;

public class ReflectInit {
   private static final List<Reflections> reflections = new ArrayList<>();

   public static void registerPackages() {
      try {
         reflections.add(new Reflections("meteordevelopment.meteorclient", Scanners.MethodsAnnotated));

         for (MeteorAddon var1 : AddonManager.ADDONS) {
            String var2 = var1.getPackage();
            if (var2 != null && !var2.isBlank()) {
               reflections.add(new Reflections(var2, Scanners.MethodsAnnotated));
            }
         }
      } catch (Throwable var3) {
         System.err.println("[Meteor-Patch] Reflections registerPackages warning: " + var3);
      }
   }

   public static <T extends Annotation> void init(Class<T> var0) {
      Set<Method> var1 = new HashSet<>();

      try {
         for (Reflections var3 : reflections) {
            var1.addAll(var3.getMethodsAnnotatedWith(var0));
         }
      } catch (Throwable var4) {
         System.err.println("[Meteor-Patch] Reflections getMethodsAnnotatedWith warning: " + var4);
      }

      if (var1.isEmpty()) {
         System.out.println("[Meteor-Patch] Reflections found 0 tasks for " + var0.getSimpleName() + ", using direct static fallback init!");
         runFallback(var0);
      } else {
         Map<Class<?>, List<Method>> var5 = var1.stream().collect(Collectors.groupingBy(Method::getDeclaringClass));

         while (!var1.isEmpty()) {
            Method var6 = (Method)var1.stream().findAny().orElseThrow();
            reflectInit(var6, var0, var1, var5);
         }
      }
   }

   private static void runFallback(Class<? extends Annotation> var0) {
      if (var0 == PreInit.class) {
         call("meteordevelopment.meteorclient.utils.Utils", "init");
         call("meteordevelopment.meteorclient.utils.network.MeteorExecutor", "init");
         call("meteordevelopment.meteorclient.utils.misc.CPSUtils", "init");
         call("meteordevelopment.meteorclient.utils.misc.Names", "init");
         call("meteordevelopment.meteorclient.utils.misc.MeteorStarscript", "init");
         call("meteordevelopment.meteorclient.utils.misc.FakeClientPlayer", "init");
         call("meteordevelopment.meteorclient.utils.network.Capes", "init");
         call("meteordevelopment.meteorclient.utils.player.Rotations", "init");
         call("meteordevelopment.meteorclient.utils.player.EChestMemory", "init");
         call("meteordevelopment.meteorclient.utils.world.BlockIterator", "init");
         call("meteordevelopment.meteorclient.utils.world.BlockUtils", "init");
         call("meteordevelopment.meteorclient.renderer.Shaders", "init");
         call("meteordevelopment.meteorclient.renderer.Fonts", "refresh");
         call("meteordevelopment.meteorclient.renderer.Renderer2D", "init");
         call("meteordevelopment.meteorclient.renderer.PostProcessRenderer", "init");
         call("meteordevelopment.meteorclient.utils.render.postprocess.PostProcessShaders", "init");
         call("meteordevelopment.meteorclient.gui.tabs.Tabs", "init");
         call("meteordevelopment.meteorclient.gui.GuiThemes", "init");
         call("meteordevelopment.meteorclient.pathing.PathManagers", "init");
      } else if (var0 == PostInit.class) {
         call("meteordevelopment.meteorclient.commands.Commands", "init");
         call("meteordevelopment.meteorclient.utils.player.ChatUtils", "init");
         call("meteordevelopment.meteorclient.utils.render.RenderUtils", "init");
         call("meteordevelopment.meteorclient.utils.render.PlayerHeadUtils", "init");
         call("meteordevelopment.meteorclient.utils.render.color.RainbowColors", "init");
         call("meteordevelopment.meteorclient.gui.renderer.GuiRenderer", "init");
         call("meteordevelopment.meteorclient.gui.GuiThemes", "postInit");
         call("meteordevelopment.meteorclient.utils.render.postprocess.ChamsShader", "load");
      }
   }

   private static void makeAccessible(Method m) {
      try {
         m.setAccessible(true);
      } catch (Throwable t) {
         try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe) unsafeField.get(null);
            Field overrideField = AccessibleObject.class.getDeclaredField("override");
            long offset = unsafe.objectFieldOffset(overrideField);
            unsafe.putBoolean(m, offset, true);
         } catch (Throwable ignored) {}
      }
   }

   private static void call(String var0, String var1) {
      try {
         Class var2 = Class.forName(var0);
         Method var3 = var2.getDeclaredMethod(var1);
         makeAccessible(var3);
         var3.invoke(null);
         System.out.println("[Meteor-Patch] Successfully executed " + var0 + "." + var1 + "()");
      } catch (Throwable var4) {
         System.err.println("[Meteor-Patch] Failed calling " + var0 + "." + var1 + "(): " + var4);
      }
   }

   private static <T extends Annotation> void reflectInit(Method var0, Class<T> var1, Set<Method> var2, Map<Class<?>, List<Method>> var3) {
      for (Class var7 : getDependencies(var0, var1)) {
         for (Method var9 : var3.getOrDefault(var7, Collections.emptyList())) {
            if (var2.contains(var9)) {
               reflectInit(var9, var1, var2, var3);
            }
         }
      }

      if (var2.remove(var0)) {
         try {
            makeAccessible(var0);
            var0.invoke(null);
         } catch (Throwable var10) {
            System.err.println("[Meteor-Patch] Error running @" + var1.getSimpleName() + " task: " + var10);
         }
      }
   }

   private static <T extends Annotation> Class<?>[] getDependencies(Method var0, Class<T> var1) {
      Annotation var2 = var0.getAnnotation(var1);
      if (var2 instanceof PreInit var3) {
         return var3.dependencies();
      } else {
         return var2 instanceof PostInit var4 ? var4.dependencies() : new Class[0];
      }
   }
}
