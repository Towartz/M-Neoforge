package meteordevelopment.meteorclient.gui.widgets;

import java.util.ArrayList;
import java.util.List;

public abstract class WMultiLabel extends WLabel {
   protected List<String> lines = new ArrayList<>(2);
   protected double maxWidth;

   public WMultiLabel(String text, boolean title, double maxWidth) {
      super(text, title);
      this.maxWidth = maxWidth;
   }

   @Override
   protected void onCalculateSize() {
      this.lines.clear();
      String[] words = this.text.split(" ");
      StringBuilder sb = new StringBuilder();
      double spaceWidth = this.theme.textWidth(" ", 1, this.title);
      double maxWidth = this.theme.scale(this.maxWidth);
      double lineWidth = 0.0;
      double maxLineWidth = 0.0;
      int iInLine = 0;

      for (int i = 0; i < words.length; i++) {
         double wordWidth = this.theme.textWidth(words[i], words[i].length(), this.title);
         double toAdd = wordWidth;
         if (iInLine > 0) {
            toAdd = wordWidth + spaceWidth;
         }

         if (lineWidth + toAdd > maxWidth) {
            this.lines.add(sb.toString());
            sb.setLength(0);
            lineWidth = 0.0;
            iInLine = 0;
            i--;
         } else {
            if (iInLine > 0) {
               sb.append(' ');
               lineWidth += spaceWidth;
            }

            sb.append(words[i]);
            lineWidth += wordWidth;
            maxLineWidth = Math.max(maxLineWidth, lineWidth);
            iInLine++;
         }
      }

      if (!sb.isEmpty()) {
         this.lines.add(sb.toString());
      }

      this.width = maxLineWidth;
      this.height = this.theme.textHeight(this.title) * (double)this.lines.size();
   }

   @Override
   public void set(String text) {
      if (!text.equals(this.text)) {
         this.invalidate();
      }

      this.text = text;
   }
}
