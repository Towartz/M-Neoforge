package baritone.api;

import baritone.api.cache.IWorldScanner;
import baritone.api.command.ICommandSystem;
import baritone.api.schematic.ISchematicSystem;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;

public interface IBaritoneProvider {
   IBaritone getPrimaryBaritone();

   List<IBaritone> getAllBaritones();

   default IBaritone getBaritoneForPlayer(LocalPlayer player) {
      for (IBaritone baritone : this.getAllBaritones()) {
         if (Objects.equals(player, baritone.getPlayerContext().player())) {
            return baritone;
         }
      }

      return null;
   }

   default IBaritone getBaritoneForMinecraft(Minecraft minecraft) {
      for (IBaritone baritone : this.getAllBaritones()) {
         if (Objects.equals(minecraft, baritone.getPlayerContext().minecraft())) {
            return baritone;
         }
      }

      return null;
   }

   default IBaritone getBaritoneForConnection(ClientPacketListener connection) {
      for (IBaritone baritone : this.getAllBaritones()) {
         LocalPlayer player = baritone.getPlayerContext().player();
         if (player != null && player.connection == connection) {
            return baritone;
         }
      }

      return null;
   }

   IBaritone createBaritone(Minecraft var1);

   boolean destroyBaritone(IBaritone var1);

   IWorldScanner getWorldScanner();

   ICommandSystem getCommandSystem();

   ISchematicSystem getSchematicSystem();
}
