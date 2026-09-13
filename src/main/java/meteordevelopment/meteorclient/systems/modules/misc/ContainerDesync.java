package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;

public class ContainerDesync extends Module {
    public enum Mode {
        DesyncServer,
        SilentCloseClient,
        BlockClosePackets
    }

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Mode> mode = this.sgGeneral.add(
        new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("How container desynchronization is applied.")
            .defaultValue(Mode.DesyncServer)
            .build()
    );

    private final Setting<Keybind> desyncKey = this.sgGeneral.add(
        new KeybindSetting.Builder()
            .name("desync-key")
            .description("Keybind to instantly desync currently open container.")
            .action(this::desyncCurrentContainer)
            .build()
    );

    private final Setting<Keybind> silentCloseKey = this.sgGeneral.add(
        new KeybindSetting.Builder()
            .name("silent-close-key")
            .description("Keybind to close currently open container locally without packet.")
            .action(this::silentCloseCurrentContainer)
            .build()
    );

    private boolean suppressNextClosePacket = false;

    public ContainerDesync() {
        super(Categories.Misc, "container-desync", "Desynchronizes container GUI state between client and server for dupe and inspection exploits.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) {
            toggle();
            return;
        }

        switch (mode.get()) {
            case DesyncServer -> {
                desyncCurrentContainer();
                toggle();
            }
            case SilentCloseClient -> {
                silentCloseCurrentContainer();
                toggle();
            }
            case BlockClosePackets -> {
                info("Container close packets will be suppressed while active.");
            }
        }
    }

    public void desyncCurrentContainer() {
        if (mc.player == null || mc.getConnection() == null) {
            info("Cannot desync: Not connected to a world.");
            return;
        }

        if (mc.player.containerMenu == null || mc.player.containerMenu == mc.player.inventoryMenu) {
            info("Cannot desync: No open container found.");
            return;
        }

        mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
        info("Container desynced: Sent close packet to server while container GUI stays open.");
    }

    public void silentCloseCurrentContainer() {
        if (mc.player == null) {
            info("Cannot close container: Not connected to a world.");
            return;
        }

        if (mc.player.containerMenu == null || mc.player.containerMenu == mc.player.inventoryMenu) {
            info("Cannot close container: No open container found.");
            return;
        }

        suppressNextClosePacket = true;
        mc.setScreen(null);
        info("Container closed locally without notifying the server.");
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof ServerboundContainerClosePacket) {
            if (mode.get() == Mode.BlockClosePackets && isActive()) {
                event.cancel();
                info("Suppressed ServerboundContainerClosePacket.");
            } else if (suppressNextClosePacket) {
                event.cancel();
                suppressNextClosePacket = false;
                info("Suppressed ServerboundContainerClosePacket.");
            }
        }
    }

    @Override
    public String getInfoString() {
        return mode.get().name();
    }
}
