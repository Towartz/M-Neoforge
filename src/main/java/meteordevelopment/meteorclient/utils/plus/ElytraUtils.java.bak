package meteordevelopment.meteorclient.utils.plus;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;

public class ElytraUtils {
    public static void startFly() {
        if (MeteorClient.mc.player != null && MeteorClient.mc.getConnection() != null) {
            MeteorClient.mc.getConnection().send(new ServerboundPlayerCommandPacket(MeteorClient.mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
        }
    }

    public static void fakeInventoryOpen(boolean open) {
        if (MeteorClient.mc.player != null && MeteorClient.mc.getConnection() != null) {
            if (open) {
                MeteorClient.mc.getConnection().send(new ServerboundPlayerCommandPacket(MeteorClient.mc.player, ServerboundPlayerCommandPacket.Action.OPEN_INVENTORY));
            } else {
                MeteorClient.mc.getConnection().send(new ServerboundContainerClosePacket(0));
            }
        }
    }
}
