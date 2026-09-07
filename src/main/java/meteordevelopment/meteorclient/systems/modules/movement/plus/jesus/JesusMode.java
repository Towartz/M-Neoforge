package meteordevelopment.meteorclient.systems.modules.movement.plus.jesus;

import meteordevelopment.meteorclient.events.entity.player.CanWalkOnFluidEvent;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.CollisionShapeEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;

public class JesusMode {
    protected final Minecraft mc;
    protected final JesusPlus settings = (JesusPlus) Modules.get().get(JesusPlus.class);
    private final JesusModes type;

    public JesusMode(JesusModes type) {
        this.mc = Minecraft.getInstance();
        this.type = type;
    }

    public void onSendPacket(PacketEvent.Send event) {}
    public void onSentPacket(PacketEvent.Sent event) {}
    public void onCanWalkOnFluid(CanWalkOnFluidEvent event) {}
    public void onCollisionShape(CollisionShapeEvent event) {}
    public void onPlayerMoveEvent(PlayerMoveEvent event) {}
    public void onTickEventPre(TickEvent.Pre event) {}
    public void onTickEventPost(TickEvent.Post event) {}
    public void onActivate() {}
    public void onDeactivate() {}

    protected boolean isFluid(net.minecraft.core.BlockPos pos) {
        if (mc.level == null) return false;
        var state = mc.level.getBlockState(pos);
        if (state.is(net.minecraft.world.level.block.Blocks.WATER)) return true;
        if (this.settings != null && this.settings.lava.get() && (state.is(net.minecraft.world.level.block.Blocks.LAVA) || state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA))) return true;
        return false;
    }
}
