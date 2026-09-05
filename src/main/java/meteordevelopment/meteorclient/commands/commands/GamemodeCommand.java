package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.world.level.GameType;

public class GamemodeCommand extends Command {
   public GamemodeCommand() {
      super("gamemode", "Changes your gamemode client-side.", "gm");
   }

   @Override
   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      for (GameType gameMode : GameType.values()) {
         builder.then(literal(gameMode.getName()).executes(context -> {
            mc.gameMode.setLocalMode(gameMode);
            return 1;
         }));
      }
   }
}
