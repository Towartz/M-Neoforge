package meteordevelopment.meteorclient.systems.hud.elements;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.world.plus.timer.TimerPlus;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public class TimerPlusCharge extends HudElement {
    public static final HudElementInfo<TimerPlusCharge> INFO = new HudElementInfo<>(
        Hud.GROUP,
        "timer+-charge",
        "Displays timer plus charge percentage.",
        TimerPlusCharge::new
    );

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<SettingColor> textColor = this.sgGeneral.add(new ColorSetting.Builder()
        .name("text-color")
        .description("Label color.")
        .defaultValue(new SettingColor(255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> chargeColor = this.sgGeneral.add(new ColorSetting.Builder()
        .name("charge-color")
        .description("Value color.")
        .defaultValue(new SettingColor(0, 220, 255))
        .build()
    );

    public TimerPlusCharge() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        double x = this.x;
        double y = this.y;

        String label = "Timer+: ";
        String value = String.format("%.1f%%", TimerPlus.chargePercent);

        double labelWidth = renderer.text(label, x, y, this.textColor.get(), true, 1.0);
        double totalWidth = renderer.text(value, x + labelWidth, y, this.chargeColor.get(), true, 1.0);

        this.setSize(labelWidth + totalWidth, renderer.textHeight(true, 1.0));
    }
}
