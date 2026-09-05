package meteordevelopment.meteorclient.mixin;

import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ServerboundContainerClosePacket.class})
public interface CloseHandledScreenC2SPacketAccessor {
   @Accessor("containerId")
   int getSyncId();
}
