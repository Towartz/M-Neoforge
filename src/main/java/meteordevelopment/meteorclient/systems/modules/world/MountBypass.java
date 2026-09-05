package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.mixininterface.IPlayerInteractEntityC2SPacket;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundInteractPacket.ActionType;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;

public class MountBypass extends Module {
   private boolean dontCancel;

   public MountBypass() {
      super(Categories.World, "mount-bypass", "Allows you to bypass the IllegalStacks plugin and put chests on entities.");
   }

   @EventHandler
   public void onSendPacket(PacketEvent.Send event) {
      if (this.dontCancel) {
         this.dontCancel = false;
      } else {
         if (event.packet instanceof IPlayerInteractEntityC2SPacket packet
            && packet.getType() == ActionType.INTERACT_AT
            && packet.getEntity() instanceof AbstractChestedHorse) {
            event.cancel();
         }
      }
   }
}
