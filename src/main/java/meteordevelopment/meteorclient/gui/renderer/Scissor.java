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
      GL11.glScissor(this.x, Utils.getWindowHeight() - this.y - this.height, this.width, this.height);
   }
}
