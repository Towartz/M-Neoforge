package meteordevelopment.meteorclient.gui.widgets.pressable;

import meteordevelopment.meteorclient.gui.renderer.packer.GuiTexture;

public abstract class WConfirmedButton extends WButton {
    protected boolean pressedOnce = false;
    protected String confirmText;

    public WConfirmedButton(String text, String confirmText, GuiTexture texture) {
        super(text, texture);
        this.confirmText = confirmText;
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button, boolean used) {
        boolean pressed = super.onMouseClicked(mouseX, mouseY, button, used);
        if (!pressed) {
            this.pressedOnce = false;
            this.invalidate();
        }
        return pressed;
    }

    @Override
    public boolean onMouseReleased(double mouseX, double mouseY, int button) {
        if (this.pressed && this.pressedOnce) {
            super.onMouseReleased(mouseX, mouseY, button);
        }
        this.pressedOnce = this.pressed;
        this.invalidate();
        this.pressed = false;
        return false;
    }

    @Override
    public String getText() {
        return this.pressedOnce ? this.confirmText : this.text;
    }

    public void set(String text, String confirmText) {
        super.set(text);
        this.confirmText = confirmText;
    }
}
