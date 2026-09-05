package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;

public class DismountCommand extends Command {
   public DismountCommand() {
      super("dismount", "Dismounts you from entity you are riding.");
   }

   @Override
   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      builder.executes(context -> {
         mc.getConnection().send(new ServerboundPlayerInputPacket(0.0F, 0.0F, false, true));
         return 1;
      });
   }
}
