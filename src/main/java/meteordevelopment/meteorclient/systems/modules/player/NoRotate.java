package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.mixin.PlayerPositionLookS2CPacketAccessor;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;

public class NoRotate extends Module {
   public NoRotate() {
      super(Categories.Player, "no-rotate", "Attempts to block rotations sent from server to client.");
   }

   @EventHandler
   private void onReceivePacket(PacketEvent.Receive event) {
      if (event.packet instanceof ClientboundPlayerPositionPacket) {
         ((PlayerPositionLookS2CPacketAccessor)event.packet).setPitch(this.mc.player.getXRot());
         ((PlayerPositionLookS2CPacketAccessor)event.packet).setYaw(this.mc.player.getYRot());
      }
   }
}
