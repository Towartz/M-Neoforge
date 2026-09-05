package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.commands.arguments.PlayerArgumentType;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public class SpectateCommand extends Command {
   private final SpectateCommand.StaticListener shiftListener = new SpectateCommand.StaticListener();

   public SpectateCommand() {
      super("spectate", "Allows you to spectate nearby players");
   }

   @Override
   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      builder.then(literal("reset").executes(context -> {
         mc.setCameraEntity(mc.player);
         return 1;
      }));
      builder.then(argument("player", PlayerArgumentType.create()).executes(context -> {
         mc.setCameraEntity(PlayerArgumentType.get(context));
         mc.player.displayClientMessage(Component.literal("Sneak to un-spectate."), true);
         MeteorClient.EVENT_BUS.subscribe(this.shiftListener);
         return 1;
      }));
   }

   private static class StaticListener {
      @EventHandler
      private void onKey(KeyEvent event) {
         if (SpectateCommand.mc.options.keyShift.matches(event.key, 0) || SpectateCommand.mc.options.keyShift.matchesMouse(event.key)) {
            SpectateCommand.mc.setCameraEntity(SpectateCommand.mc.player);
            event.cancel();
            MeteorClient.EVENT_BUS.unsubscribe(this);
         }
      }
   }
}
