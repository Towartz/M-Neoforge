package meteordevelopment.meteorclient.utils.misc.input;

import com.mojang.blaze3d.platform.InputConstants;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiKeyEvents;
import meteordevelopment.meteorclient.mixin.KeyBindingAccessor;
import meteordevelopment.meteorclient.utils.misc.CursorStyle;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class Input {
   private static final boolean[] keys = new boolean[512];
   private static final boolean[] buttons = new boolean[16];
   private static CursorStyle lastCursorStyle = CursorStyle.Default;

   private Input() {
   }

   public static void setKeyState(int key, boolean pressed) {
      if (key >= 0 && key < keys.length) {
         keys[key] = pressed;
      }
   }

   public static void setButtonState(int button, boolean pressed) {
      if (button >= 0 && button < buttons.length) {
         buttons[button] = pressed;
      }
   }

   public static int getKey(KeyMapping bind) {
      return ((KeyBindingAccessor)bind).getKey().getValue();
   }

   public static void setKeyState(KeyMapping bind, boolean pressed) {
      setKeyState(getKey(bind), pressed);
   }

   public static boolean isPressed(KeyMapping bind) {
      InputConstants.Key key = ((KeyBindingAccessor)bind).getKey();
      if (key.getType() == InputConstants.Type.MOUSE) {
         return isButtonPressed(key.getValue());
      }
      return isKeyPressed(key.getValue());
   }

   public static boolean isKeyPressed(int key) {
      if (!GuiKeyEvents.canUseKeys) {
         return false;
      } else if (key <= 0 || key >= keys.length) {
         return false;
      } else {
         if (keys[key]) {
            return true;
         }
         if (MeteorClient.mc != null && MeteorClient.mc.getWindow() != null) {
            long handle = MeteorClient.mc.getWindow().getWindow();
            if (handle != 0L && GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS) {
               keys[key] = true;
               return true;
            }
         }
         return false;
      }
   }

   public static boolean isButtonPressed(int button) {
      if (button < 0 || button >= buttons.length) {
         return false;
      } else {
         if (buttons[button]) {
            return true;
         }
         if (MeteorClient.mc != null && MeteorClient.mc.getWindow() != null) {
            long handle = MeteorClient.mc.getWindow().getWindow();
            if (handle != 0L && GLFW.glfwGetMouseButton(handle, button) == GLFW.GLFW_PRESS) {
               buttons[button] = true;
               return true;
            }
         }
         return false;
      }
   }

   public static void setCursorStyle(CursorStyle style) {
      if (lastCursorStyle != style) {
         GLFW.glfwSetCursor(MeteorClient.mc.getWindow().getWindow(), style.getGlfwCursor());
         lastCursorStyle = style;
      }
   }

   public static int getModifier(int key) {
      return switch (key) {
         case 340, 344 -> 1;
         case 341, 345 -> 2;
         case 342, 346 -> 4;
         case 343, 347 -> 8;
         default -> 0;
      };
   }
}
