package meteordevelopment.meteorclient.gui.widgets.pressable;

public abstract class WConfirmedMinus extends WMinus {
    protected boolean pressedOnce = false;

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button, boolean used) {
        boolean pressed = super.onMouseClicked(mouseX, mouseY, button, used);
        if (!pressed) {
            this.pressedOnce = false;
        }
        return pressed;
    }

    @Override
    public boolean onMouseReleased(double mouseX, double mouseY, int button) {
        if (this.pressed && this.pressedOnce) {
            super.onMouseReleased(mouseX, mouseY, button);
        }
        this.pressedOnce = this.pressed;
        this.pressed = false;
        return false;
    }
}
