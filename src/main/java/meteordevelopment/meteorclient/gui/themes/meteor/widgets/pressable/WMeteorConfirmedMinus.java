package meteordevelopment.meteorclient.gui.themes.meteor.widgets.pressable;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WConfirmedMinus;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public class WMeteorConfirmedMinus extends WConfirmedMinus implements MeteorWidget {
    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        MeteorGuiTheme theme = this.theme();
        double pad = this.pad();
        double s = theme.scale(3.0);
        double bs = theme.scale(2.0);
        SettingColor outline = theme.outlineColor.get(this.pressed, this.mouseOver);
        Color fg = this.pressedOnce ? theme.backgroundColor.get(this.pressed, this.mouseOver) : theme.minusColor.get();
        Color bg = this.pressedOnce ? theme.minusColor.get() : theme.backgroundColor.get(this.pressed, this.mouseOver);

        renderer.quad(this.x + bs, this.y + bs, this.width - bs * 2.0, this.height - bs * 2.0, bg);
        renderer.quad(this.x, this.y, this.width, bs, outline);
        renderer.quad(this.x, this.y + this.height - bs, this.width, bs, outline);
        renderer.quad(this.x, this.y + bs, bs, this.height - bs * 2.0, outline);
        renderer.quad(this.x + this.width - bs, this.y + bs, bs, this.height - bs * 2.0, outline);

        renderer.quad(this.x + pad, this.y + this.height / 2.0 - s / 2.0, this.width - pad * 2.0, s, fg);
    }
}
