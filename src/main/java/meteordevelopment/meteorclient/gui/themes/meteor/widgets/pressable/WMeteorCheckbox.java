package meteordevelopment.meteorclient.gui.themes.meteor.widgets.pressable;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import net.minecraft.util.Mth;

public class WMeteorCheckbox extends WCheckbox implements MeteorWidget {
   private double animProgress;

   public WMeteorCheckbox(boolean checked) {
      super(checked);
      this.animProgress = checked ? 1.0 : 0.0;
   }

   @Override
   protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      MeteorGuiTheme theme = this.theme();
      this.animProgress = this.animProgress + (double)(this.checked ? 1 : -1) * delta * 14.0;
      this.animProgress = Mth.clamp(this.animProgress, 0.0, 1.0);
      this.renderBackground(renderer, this, this.pressed, this.mouseOver);
      if (this.animProgress > 0.0) {
         double cs = (this.width - theme.scale(2.0)) / 1.75 * this.animProgress;
         renderer.quad(this.x + (this.width - cs) / 2.0, this.y + (this.height - cs) / 2.0, cs, cs, theme.checkboxColor.get());
      }
   }
}
