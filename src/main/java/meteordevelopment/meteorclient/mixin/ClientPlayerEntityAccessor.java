package meteordevelopment.meteorclient.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({LocalPlayer.class})
public interface ClientPlayerEntityAccessor {
   @Accessor("jumpRidingScale")
   void setMountJumpStrength(float var1);

   @Accessor("positionReminder")
   void setTicksSinceLastPositionPacketSent(int var1);

   @Invoker("canStartSprinting")
   boolean invokeCanSprint();
}
