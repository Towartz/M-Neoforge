package meteordevelopment.meteorclient.systems.modules.combat.plus.velocity;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;

public class VelocityMode {
    protected final Minecraft mc = Minecraft.getInstance();
    protected final VelocityPlus settings = Modules.get().get(VelocityPlus.class);
    private final VelocityModes type;

    public VelocityMode(VelocityModes type) {
        this.type = type;
    }

    public void onReceivePacket(PacketEvent.Receive event) {}
    public void onSendPacket(PacketEvent.Send event) {}
    public void onSentPacket(PacketEvent.Sent event) {}
    public void onTickEventPre(TickEvent.Pre event) {}
    public void onTickEventPost(TickEvent.Post event) {}
    public void onActivate() {}
    public void onDeactivate() {}
}
