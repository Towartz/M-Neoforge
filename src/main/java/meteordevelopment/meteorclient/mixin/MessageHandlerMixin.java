package meteordevelopment.meteorclient.mixin;

import com.mojang.authlib.GameProfile;
import java.time.Instant;
import meteordevelopment.meteorclient.mixininterface.IMessageHandler;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.chat.ChatType.Bound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ChatListener.class})
public abstract class MessageHandlerMixin implements IMessageHandler {
   @Unique
   private GameProfile sender;

   @Inject(
      method = {"processChatMessageInternal"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/hud/ChatHud;addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
         shift = Shift.BEFORE
      )}
   )
   private void onProcessChatMessageInternal_beforeAddMessage(
      Bound params,
      PlayerChatMessage message,
      Component decorated,
      GameProfile sender,
      boolean onlyShowSecureChat,
      Instant receptionTimestamp,
      CallbackInfoReturnable<Boolean> info
   ) {
      this.sender = sender;
   }

   @Inject(
      method = {"processChatMessageInternal"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/hud/ChatHud;addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
         shift = Shift.AFTER
      )}
   )
   private void onProcessChatMessageInternal_afterAddMessage(
      Bound params,
      PlayerChatMessage message,
      Component decorated,
      GameProfile sender,
      boolean onlyShowSecureChat,
      Instant receptionTimestamp,
      CallbackInfoReturnable<Boolean> info
   ) {
      this.sender = null;
   }

   @Override
   public GameProfile meteor$getSender() {
      return this.sender;
   }
}
