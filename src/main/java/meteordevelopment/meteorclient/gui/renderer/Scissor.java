package meteordevelopment.meteorclient.gui.renderer;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.utils.Utils;
import org.lwjgl.opengl.GL11;

public class Scissor {
   public int x;
   public int y;
   public int width;
   public int height;
   public final List<Runnable> postTasks = new ArrayList<>();

   public Scissor set(double x, double y, double width, double height) {
      if (width < 0.0) {
         width = 0.0;
      }

      if (height < 0.0) {
         height = 0.0;
      }

      this.x = (int)Math.round(x);
      this.y = (int)Math.round(y);
      this.width = (int)Math.round(width);
      this.height = (int)Math.round(height);
      this.postTasks.clear();
      return this;
   }

   public void apply() {
      int windowWidth = Utils.getWindowWidth();
      int windowHeight = Utils.getWindowHeight();

      int clampedX = Math.max(0, Math.min(this.x, windowWidth));
      int clampedY = Math.max(0, Math.min(this.y, windowHeight));
      int clampedW = Math.max(0, Math.min(this.width, windowWidth - clampedX));
      int clampedH = Math.max(0, Math.min(this.height, windowHeight - clampedY));

      int glY = Math.max(0, windowHeight - clampedY - clampedH);
      GL11.glScissor(clampedX, glY, clampedW, clampedH);
   }
}
