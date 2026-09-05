package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.BetterChat;
import net.minecraft.util.StringUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin({StringUtil.class})
public abstract class StringHelperMixin {
   @ModifyArg(
      method = {"truncateChat"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/util/StringHelper;truncate(Ljava/lang/String;IZ)Ljava/lang/String;"
      ),
      index = 1
   )
   private static int injected(int maxLength) {
      return Modules.get().get(BetterChat.class).isInfiniteChatBox() ? Integer.MAX_VALUE : maxLength;
   }
}
