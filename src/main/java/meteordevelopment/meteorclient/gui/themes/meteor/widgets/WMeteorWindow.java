package meteordevelopment.meteorclient.gui.themes.meteor.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorWidget;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;

public class WMeteorWindow extends WWindow implements MeteorWidget {
   public WMeteorWindow(WWidget icon, String title) {
      super(icon, title);
   }

   @Override
   protected WWindow.WHeader header(WWidget icon) {
      return new WMeteorWindow.WMeteorHeader(icon);
   }

   @Override
   protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      if (this.expanded || this.animProgress > 0.0) {
         renderer.quad(this.x, this.y + this.header.height, this.width, this.height - this.header.height, this.theme().backgroundColor.get());
      }
   }

   private class WMeteorHeader extends WWindow.WHeader {
      public WMeteorHeader(WWidget icon) {
         super(icon);
      }

      @Override
      protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
         renderer.quad(this, WMeteorWindow.this.theme().accentColor.get());
      }
   }
}
