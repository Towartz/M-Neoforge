package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.mixin.AbstractSignEditScreenAccessor;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.world.level.block.entity.SignBlockEntity;

public class AutoSign extends Module {
   private String[] text;

   public AutoSign() {
      super(Categories.World, "auto-sign", "Automatically writes signs. The first sign's text will be used.");
   }

   @Override
   public void onDeactivate() {
      this.text = null;
   }

   @EventHandler
   private void onSendPacket(PacketEvent.Send event) {
      if (event.packet instanceof ServerboundSignUpdatePacket) {
         this.text = ((ServerboundSignUpdatePacket)event.packet).getLines();
      }
   }

   @EventHandler
   private void onOpenScreen(OpenScreenEvent event) {
      if (event.screen instanceof AbstractSignEditScreen && this.text != null) {
         SignBlockEntity sign = ((AbstractSignEditScreenAccessor)event.screen).getSign();
         this.mc.player.connection.send(new ServerboundSignUpdatePacket(sign.getBlockPos(), true, this.text[0], this.text[1], this.text[2], this.text[3]));
         event.cancel();
      }
   }
}
