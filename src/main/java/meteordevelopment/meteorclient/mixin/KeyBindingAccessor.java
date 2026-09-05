package meteordevelopment.meteorclient.mixin;

import com.mojang.blaze3d.platform.InputConstants.Key;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({KeyMapping.class})
public interface KeyBindingAccessor {
   @Accessor("CATEGORY_SORT_ORDER")
   static Map<String, Integer> getCategoryOrderMap() {
      return null;
   }

   @Accessor("key")
   Key getKey();

   @Accessor("clickCount")
   int meteor$getTimesPressed();

   @Accessor("clickCount")
   void meteor$setTimesPressed(int var1);
}
