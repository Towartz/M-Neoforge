package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly;

public class VClipCommand extends Command {
   public VClipCommand() {
      super("vclip", "Lets you clip through blocks vertically.");
   }

   @Override
   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      builder.then(argument("blocks", DoubleArgumentType.doubleArg()).executes(context -> {
         double blocks = (Double)context.getArgument("blocks", Double.class);
         int packetsRequired = (int)Math.ceil(Math.abs(blocks / 10.0));
         if (packetsRequired > 20) {
            packetsRequired = 1;
         }

         if (mc.player.isPassenger()) {
            for (int packetNumber = 0; packetNumber < packetsRequired - 1; packetNumber++) {
               mc.player.connection.send(new ServerboundMoveVehiclePacket(mc.player.getVehicle()));
            }

            mc.player.getVehicle().setPos(mc.player.getVehicle().getX(), mc.player.getVehicle().getY() + blocks, mc.player.getVehicle().getZ());
            mc.player.connection.send(new ServerboundMoveVehiclePacket(mc.player.getVehicle()));
         } else {
            for (int packetNumber = 0; packetNumber < packetsRequired - 1; packetNumber++) {
               mc.player.connection.send(new StatusOnly(true));
            }

            mc.player.connection.send(new Pos(mc.player.getX(), mc.player.getY() + blocks, mc.player.getZ(), true));
            mc.player.setPos(mc.player.getX(), mc.player.getY() + blocks, mc.player.getZ());
         }

         return 1;
      }));
   }
}
