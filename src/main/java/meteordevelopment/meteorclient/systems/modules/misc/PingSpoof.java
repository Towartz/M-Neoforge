package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.*;

public class PingSpoof extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> delay = this.sgGeneral.add(
        new IntSetting.Builder()
            .name("delay-ms")
            .description("Latency added in milliseconds.")
            .defaultValue(250)
            .min(0)
            .max(1500)
            .sliderMax(1000)
            .build()
    );

    private final Setting<Boolean> keepAlive = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("keep-alive")
            .description("Delay server-bound keep alive packets.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> pingPong = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("ping-pong")
            .description("Delay server-bound pong packets.")
            .defaultValue(true)
            .build()
    );

    private final Set<Packet<?>> bypassPackets = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "PingSpoof-Scheduler");
        t.setDaemon(true);
        return t;
    });

    public PingSpoof() {
        super(Categories.Misc, "ping-spoof", "Adds artificial latency to your ping by delaying keep-alives and pings.");
    }

    @Override
    public void onDeactivate() {
        bypassPackets.clear();
    }

    @EventHandler(priority = 200)
    private void onPacketSend(PacketEvent.Send event) {
        if (!isActive() || mc.getConnection() == null) return;
        if (bypassPackets.remove(event.packet)) return;

        boolean isKeepAlive = keepAlive.get() && event.packet instanceof ServerboundKeepAlivePacket;
        boolean isPong = pingPong.get() && event.packet instanceof ServerboundPongPacket;

        if (isKeepAlive || isPong) {
            event.cancel();
            Packet<?> packet = event.packet;
            int delayMs = delay.get();

            scheduler.schedule(() -> {
                if (mc.getConnection() != null) {
                    bypassPackets.add(packet);
                    mc.getConnection().send(packet);
                }
            }, delayMs, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public String getInfoString() {
        return delay.get() + "ms";
    }
}
