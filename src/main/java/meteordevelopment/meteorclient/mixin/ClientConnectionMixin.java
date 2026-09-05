package meteordevelopment.meteorclient.mixin;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.timeout.TimeoutException;
import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Iterator;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.ServerConnectEndEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AntiPacketKick;
import meteordevelopment.meteorclient.systems.modules.world.HighwayBuilder;
import meteordevelopment.meteorclient.systems.proxies.Proxies;
import meteordevelopment.meteorclient.systems.proxies.Proxy;
import meteordevelopment.meteorclient.systems.proxies.ProxyType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.BandwidthDebugMonitor;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.SkipPacketException;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Connection.class})
public abstract class ClientConnectionMixin {
   @Inject(
      method = {"channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onHandlePacket(ChannelHandlerContext channelHandlerContext, Packet<?> packet, CallbackInfo ci) {
      if (packet instanceof ClientboundBundlePacket bundle) {
         Iterator<Packet<? super ClientGamePacketListener>> it = bundle.subPackets().iterator();

         while (it.hasNext()) {
            if (MeteorClient.EVENT_BUS.post(new PacketEvent.Receive(it.next(), (Connection)(Object)this)).isCancelled()) {
               it.remove();
            }
         }
      } else if (MeteorClient.EVENT_BUS.post(new PacketEvent.Receive(packet, (Connection)(Object)this)).isCancelled()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"disconnect(Lnet/minecraft/network/chat/Component;)V"},
      at = {@At("HEAD")}
   )
   private void disconnect(Component disconnectReason, CallbackInfo ci) {
      if (Modules.get().get(HighwayBuilder.class).isActive()) {
         MutableComponent text = Component.literal(
            "%n%n%s[%sHighway Builder%s] Statistics:%n".formatted(ChatFormatting.GRAY, ChatFormatting.BLUE, ChatFormatting.GRAY)
         );
         text.append(Modules.get().get(HighwayBuilder.class).getStatsText());
         ((MutableComponent)disconnectReason).append(text);
      }
   }

   @Inject(
      method = {"connect(Ljava/net/InetSocketAddress;ZLnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;"},
      at = {@At("HEAD")}
   )
   private static void onConnect(InetSocketAddress address, boolean useEpoll, Connection connection, CallbackInfoReturnable<?> cir) {
      MeteorClient.EVENT_BUS.post(ServerConnectEndEvent.get(address));
   }

   @Inject(
      at = {@At("HEAD")},
      method = {"send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V"},
      cancellable = true
   )
   private void onSendPacketHead(Packet<?> packet, PacketSendListener callbacks, CallbackInfo ci) {
      if (MeteorClient.EVENT_BUS.post(new PacketEvent.Send(packet, (Connection)(Object)this)).isCancelled()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V"},
      at = {@At("TAIL")}
   )
   private void onSendPacketTail(Packet<?> packet, @Nullable PacketSendListener callbacks, CallbackInfo ci) {
      MeteorClient.EVENT_BUS.post(new PacketEvent.Sent(packet, (Connection)(Object)this));
   }

   @Inject(
      method = {"exceptionCaught(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Throwable;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void exceptionCaught(ChannelHandlerContext context, Throwable throwable, CallbackInfo ci) {
      AntiPacketKick apk = Modules.get().get(AntiPacketKick.class);
      if (!(throwable instanceof TimeoutException) && !(throwable instanceof SkipPacketException) && apk.catchExceptions()) {
         if (apk.logExceptions.get()) {
            apk.warning("Caught exception: %s", new Object[]{throwable});
         }

         ci.cancel();
      }
   }

   private static ChannelHandler createProxyHandler(Proxy proxy) {
      try {
         switch ((ProxyType)proxy.type.get()) {
            case Socks4: {
               Class<?> cls = Class.forName("io.netty.handler.proxy.Socks4ProxyHandler");
               Constructor<?> ctor = cls.getConstructor(SocketAddress.class, String.class);
               return (ChannelHandler)ctor.newInstance(new InetSocketAddress((String)proxy.address.get(), (Integer)proxy.port.get()), (String)proxy.username.get());
            }
            case Socks5: {
               Class<?> cls = Class.forName("io.netty.handler.proxy.Socks5ProxyHandler");
               Constructor<?> ctor = cls.getConstructor(SocketAddress.class, String.class, String.class);
               return (ChannelHandler)ctor.newInstance(new InetSocketAddress((String)proxy.address.get(), (Integer)proxy.port.get()), (String)proxy.username.get(), (String)proxy.password.get());
            }
         }
      } catch (Throwable t) {
         System.err.println("[Meteor] Could not instantiate proxy handler: " + t);
      }
      return null;
   }

   @Inject(
      method = {"addHandlers"},
      at = {@At("RETURN")}
   )
   private static void onAddHandlers(ChannelPipeline pipeline, PacketFlow side, boolean local, BandwidthDebugMonitor packetSizeLogger, CallbackInfo ci) {
      if (side == PacketFlow.CLIENTBOUND) {
         Proxy proxy = Proxies.get().getEnabled();
         if (proxy != null) {
            ChannelHandler handler = createProxyHandler(proxy);
            if (handler != null) {
               pipeline.addFirst(handler);
            }
         }
      }
   }
}
