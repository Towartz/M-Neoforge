package meteordevelopment.meteorclient.mixin;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ClientboundSetEntityMotionPacket.class})
public interface EntityVelocityUpdateS2CPacketAccessor {
   @Mutable
   @Accessor("xa")
   void setX(int var1);

   @Mutable
   @Accessor("ya")
   void setY(int var1);

   @Mutable
   @Accessor("za")
   void setZ(int var1);
}
