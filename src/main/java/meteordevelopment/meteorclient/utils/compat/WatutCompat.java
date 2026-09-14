package meteordevelopment.meteorclient.utils.compat;

import java.lang.reflect.Method;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.loading.LoadingModList;

public class WatutCompat {
    private static boolean initialized = false;
    private static boolean isWatutInstalled = false;
    private static Method getPlayerStatusManagerClientMethod;
    private static Method sendGuiStatusMethod;
    private static Method getStatusLocalMethod;
    private static Method setPlayerGuiStateMethod;
    private static Object noneState;

    public static volatile boolean isResetting = false;

    public static boolean isInstalled() {
        if (!initialized) {
            init();
        }
        return isWatutInstalled;
    }

    public static boolean isMeteorScreen(Screen screen) {
        if (screen == null) return false;
        return screen instanceof WidgetScreen || screen.getClass().getName().startsWith("baritone.");
    }

    public static boolean isFreecamSelfViewActive() {
        if (!isInstalled()) return false;
        if (Modules.get() == null) return false;
        Freecam freecam = Modules.get().get(Freecam.class);
        return freecam != null && freecam.isActive() && freecam.showWatut.get();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static synchronized void init() {
        if (initialized) return;
        initialized = true;

        try {
            if (LoadingModList.get() != null && LoadingModList.get().getModFileById("watut") != null) {
                Class<?> watutModClass = Class.forName("com.corosus.watut.WatutMod");
                Class<?> managerClass = Class.forName("com.corosus.watut.PlayerStatusManagerClient");
                Class<?> statusClass = Class.forName("com.corosus.watut.PlayerStatus");
                Class<?> stateEnum = Class.forName("com.corosus.watut.PlayerStatus$PlayerGuiState");

                getPlayerStatusManagerClientMethod = watutModClass.getMethod("getPlayerStatusManagerClient");
                sendGuiStatusMethod = managerClass.getMethod("sendGuiStatus", stateEnum, boolean.class);
                getStatusLocalMethod = managerClass.getMethod("getStatusLocal");
                setPlayerGuiStateMethod = statusClass.getMethod("setPlayerGuiState", stateEnum);

                noneState = Enum.valueOf((Class<Enum>) stateEnum, "NONE");
                isWatutInstalled = true;
            }
        } catch (Throwable ignored) {
            isWatutInstalled = false;
        }
    }

    public static void resetGuiStatus() {
        if (!isInstalled()) return;

        try {
            isResetting = true;
            Object manager = getPlayerStatusManagerClientMethod.invoke(null);
            if (manager != null) {
                Object localStatus = getStatusLocalMethod.invoke(manager);
                if (localStatus != null) {
                    setPlayerGuiStateMethod.invoke(localStatus, noneState);
                }
                sendGuiStatusMethod.invoke(manager, noneState, true);
            }
        } catch (Throwable ignored) {
        } finally {
            isResetting = false;
        }
    }
}
