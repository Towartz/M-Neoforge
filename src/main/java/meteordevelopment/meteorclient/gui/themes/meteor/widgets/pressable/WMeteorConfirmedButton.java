package meteordevelopment.meteorclient.gui.themes.meteor.widgets.pressable;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.renderer.packer.GuiTexture;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WConfirmedButton;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public class WMeteorConfirmedButton extends WConfirmedButton implements MeteorWidget {
    public WMeteorConfirmedButton(String text, String confirmText, GuiTexture texture) {
        super(text, confirmText, texture);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        MeteorGuiTheme theme = this.theme();
        double pad = this.pad();
        double s = theme.scale(2.0);
        SettingColor outline = theme.outlineColor.get(this.pressed, this.mouseOver);
        Color fg = this.pressedOnce ? theme.backgroundColor.get(this.pressed, this.mouseOver) : theme.textColor.get();
        Color bg = this.pressedOnce ? theme.textColor.get() : theme.backgroundColor.get(this.pressed, this.mouseOver);

        renderer.quad(this.x + s, this.y + s, this.width - s * 2.0, this.height - s * 2.0, bg);
        renderer.quad(this.x, this.y, this.width, s, outline);
        renderer.quad(this.x, this.y + this.height - s, this.width, s, outline);
        renderer.quad(this.x, this.y + s, s, this.height - s * 2.0, outline);
        renderer.quad(this.x + this.width - s, this.y + s, s, this.height - s * 2.0, outline);

        String text = this.getText();
        if (text != null) {
            renderer.text(text, this.x + this.width / 2.0 - this.textWidth / 2.0, this.y + pad, fg, false);
        } else {
            double ts = theme.textHeight();
            renderer.quad(this.x + this.width / 2.0 - ts / 2.0, this.y + pad, ts, ts, this.texture, fg);
        }
    }
}
