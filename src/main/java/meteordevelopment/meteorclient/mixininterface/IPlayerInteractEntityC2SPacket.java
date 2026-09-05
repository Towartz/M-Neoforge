package meteordevelopment.meteorclient.mixininterface;

import net.minecraft.network.protocol.game.ServerboundInteractPacket.ActionType;
import net.minecraft.world.entity.Entity;

public interface IPlayerInteractEntityC2SPacket {
   ActionType getType();

   Entity getEntity();
}
