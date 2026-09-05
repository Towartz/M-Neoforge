package meteordevelopment.meteorclient.utils.misc.input;

import com.mojang.blaze3d.platform.InputConstants.Type;
import java.util.Map;
import meteordevelopment.meteorclient.mixin.KeyBindingAccessor;
import net.minecraft.client.KeyMapping;

public class KeyBinds {
   private static final String CATEGORY = "Meteor Client";
   public static KeyMapping OPEN_GUI = new KeyMapping("key.meteor-client.open-gui", Type.KEYSYM, 344, "Meteor Client");
   public static KeyMapping OPEN_COMMANDS = new KeyMapping("key.meteor-client.open-commands", Type.KEYSYM, 46, "Meteor Client");

   private KeyBinds() {
   }

   public static KeyMapping[] apply(KeyMapping[] binds) {
      Map<String, Integer> categories = KeyBindingAccessor.getCategoryOrderMap();
      int highest = 0;

      for (int i : categories.values()) {
         if (i > highest) {
            highest = i;
         }
      }

      categories.put("Meteor Client", highest + 1);
      KeyMapping[] newBinds = new KeyMapping[binds.length + 2];
      System.arraycopy(binds, 0, newBinds, 0, binds.length);
      newBinds[binds.length] = OPEN_GUI;
      newBinds[binds.length + 1] = OPEN_COMMANDS;
      return newBinds;
   }
}
