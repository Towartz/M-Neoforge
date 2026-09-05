package meteordevelopment.meteorclient.utils.misc.text;

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;

@FunctionalInterface
public interface TextVisitor<T> {
   Optional<T> accept(Component var1, Style var2, String var3);

   static <T> Optional<T> visit(Component text, TextVisitor<T> visitor, Style baseStyle) {
      Queue<Component> queue = collectSiblings(text);
      return text.visit((style, string) -> visitor.accept(queue.remove(), style, string), baseStyle);
   }

   static ArrayDeque<Component> collectSiblings(Component text) {
      ArrayDeque<Component> queue = new ArrayDeque<>();
      collectSiblings(text, queue);
      return queue;
   }

   private static void collectSiblings(Component text, Queue<Component> queue) {
      if (!(text.getContents() instanceof PlainTextContents ptc) || !ptc.text().isEmpty()) {
         queue.add(text);
      }

      for (Component sibling : text.getSiblings()) {
         collectSiblings(sibling, queue);
      }
   }
}
