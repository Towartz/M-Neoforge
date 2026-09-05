package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import java.util.stream.Stream;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.chat.contents.PlainTextContents.LiteralContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({AbstractSignEditScreen.class})
public abstract class AbstractSignEditScreenMixin {
   @ModifyExpressionValue(
      method = {"<init>"},
      at = {@At(
         value = "INVOKE",
         target = "Ljava/util/stream/IntStream;mapToObj(Ljava/util/function/IntFunction;)Ljava/util/stream/Stream;"
      )}
   )
   private Stream<Component> modifyTranslatableText(Stream<Component> original) {
      return original.map(this::modifyText);
   }

   @Unique
   private Component modifyText(Component message) {
      MutableComponent modified = MutableComponent.create(message.getContents());
      if (message.getContents() instanceof KeybindContents content) {
         String key = content.getName();
         if (key.contains("meteor-client")) {
            modified = MutableComponent.create(new LiteralContents(key));
         }
      }

      if (message.getContents() instanceof TranslatableContents contentx) {
         String key = contentx.getKey();
         if (key.contains("meteor-client")) {
            modified = MutableComponent.create(new LiteralContents(key));
         }
      }

      modified.setStyle(message.getStyle());

      for (Component sibling : message.getSiblings()) {
         modified.append(this.modifyText(sibling));
      }

      return modified;
   }
}
