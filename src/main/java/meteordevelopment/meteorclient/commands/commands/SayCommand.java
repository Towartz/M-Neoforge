package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.time.Instant;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.mixin.ClientPlayNetworkHandlerAccessor;
import meteordevelopment.meteorclient.utils.misc.MeteorStarscript;
import meteordevelopment.starscript.Script;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.SignedMessageBody;
import net.minecraft.network.chat.LastSeenMessagesTracker.Update;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.util.Crypt.SaltSupplier;

public class SayCommand extends Command {
   public SayCommand() {
      super("say", "Sends messages in chat.");
   }

   @Override
   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      builder.then(
         argument("message", StringArgumentType.greedyString())
            .executes(
               context -> {
                  String msg = (String)context.getArgument("message", String.class);
                  Script script = MeteorStarscript.compile(msg);
                  if (script != null) {
                     String message = MeteorStarscript.run(script);
                     if (message != null) {
                        Instant instant = Instant.now();
                        long l = SaltSupplier.getLong();
                        ClientPacketListener handler = mc.getConnection();
                        Update lastSeenMessages = ((ClientPlayNetworkHandlerAccessor)handler).getLastSeenMessagesCollector().generateAndApplyUpdate();
                        MessageSignature messageSignatureData = ((ClientPlayNetworkHandlerAccessor)handler)
                           .getMessagePacker()
                           .pack(new SignedMessageBody(message, instant, l, lastSeenMessages.lastSeen()));
                        handler.send(new ServerboundChatPacket(message, instant, l, messageSignatureData, lastSeenMessages.update()));
                     }
                  }

                  return 1;
               }
            )
      );
   }
}
