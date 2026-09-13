package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class FakeCoords extends Module {
    public enum Mode {
        Offset,
        Static
    }

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Mode> mode = this.sgGeneral.add(
        new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("How the coordinates should be spoofed.")
            .defaultValue(Mode.Offset)
            .build()
    );

    private final Setting<Integer> offsetX = this.sgGeneral.add(
        new IntSetting.Builder()
            .name("offset-x")
            .description("X coordinate offset.")
            .defaultValue(50000)
            .visible(() -> mode.get() == Mode.Offset)
            .build()
    );

    private final Setting<Integer> offsetY = this.sgGeneral.add(
        new IntSetting.Builder()
            .name("offset-y")
            .description("Y coordinate offset.")
            .defaultValue(0)
            .visible(() -> mode.get() == Mode.Offset)
            .build()
    );

    private final Setting<Integer> offsetZ = this.sgGeneral.add(
        new IntSetting.Builder()
            .name("offset-z")
            .description("Z coordinate offset.")
            .defaultValue(50000)
            .visible(() -> mode.get() == Mode.Offset)
            .build()
    );

    private final Setting<Integer> staticX = this.sgGeneral.add(
        new IntSetting.Builder()
            .name("static-x")
            .description("Static X coordinate to display.")
            .defaultValue(0)
            .visible(() -> mode.get() == Mode.Static)
            .build()
    );

    private final Setting<Integer> staticY = this.sgGeneral.add(
        new IntSetting.Builder()
            .name("static-y")
            .description("Static Y coordinate to display.")
            .defaultValue(64)
            .visible(() -> mode.get() == Mode.Static)
            .build()
    );

    private final Setting<Integer> staticZ = this.sgGeneral.add(
        new IntSetting.Builder()
            .name("static-z")
            .description("Static Z coordinate to display.")
            .defaultValue(0)
            .visible(() -> mode.get() == Mode.Static)
            .build()
    );

    public FakeCoords() {
        super(Categories.Render, "fake-coords", "Spoofs displayed coordinates in HUD and F3 to protect base coordinates on stream.");
    }

    public static double getSpoofedX(double realX) {
        FakeCoords fc = Modules.get().get(FakeCoords.class);
        if (fc == null || !fc.isActive()) return realX;

        return fc.mode.get() == Mode.Static ? fc.staticX.get() : realX + fc.offsetX.get();
    }

    public static double getSpoofedY(double realY) {
        FakeCoords fc = Modules.get().get(FakeCoords.class);
        if (fc == null || !fc.isActive()) return realY;

        return fc.mode.get() == Mode.Static ? fc.staticY.get() : realY + fc.offsetY.get();
    }

    public static double getSpoofedZ(double realZ) {
        FakeCoords fc = Modules.get().get(FakeCoords.class);
        if (fc == null || !fc.isActive()) return realZ;

        return fc.mode.get() == Mode.Static ? fc.staticZ.get() : realZ + fc.offsetZ.get();
    }

    @Override
    public String getInfoString() {
        if (mode.get() == Mode.Static) {
            return String.format("%d, %d, %d", staticX.get(), staticY.get(), staticZ.get());
        }
        return String.format("+%d, +%d, +%d", offsetX.get(), offsetY.get(), offsetZ.get());
    }
}
