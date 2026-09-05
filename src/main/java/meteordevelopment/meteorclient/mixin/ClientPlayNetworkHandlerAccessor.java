package meteordevelopment.meteorclient.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.RegistryAccess.Frozen;
import net.minecraft.network.chat.LastSeenMessagesTracker;
import net.minecraft.network.chat.SignedMessageChain.Encoder;
import net.minecraft.world.flag.FeatureFlagSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ClientPacketListener.class})
public interface ClientPlayNetworkHandlerAccessor {
   @Accessor("serverChunkRadius")
   int getChunkLoadDistance();

   @Accessor("signedMessageEncoder")
   Encoder getMessagePacker();

   @Accessor("lastSeenMessages")
   LastSeenMessagesTracker getLastSeenMessagesCollector();

   @Accessor("registryAccess")
   Frozen getCombinedDynamicRegistries();

   @Accessor("enabledFeatures")
   FeatureFlagSet getEnabledFeatures();
}
