package meteordevelopment.meteorclient.events.packets;

import meteordevelopment.meteorclient.events.Cancellable;
import meteordevelopment.meteorclient.utils.network.NeoForgeNetwork;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.jetbrains.annotations.Nullable;

public class PacketEvent {
   public static class Receive extends Cancellable {
      public Packet<?> packet;
      public Connection connection;

      public Receive(Packet<?> packet, Connection connection) {
         this.setCancelled(false);
         this.packet = packet;
         this.connection = connection;
      }

      @Nullable
      public ResourceLocation getPayloadId() {
         return NeoForgeNetwork.getPayloadId(this.packet);
      }

      public boolean isCustomPayload() {
         return this.getPayloadId() != null;
      }

      public boolean isModdedPayload() {
         return NeoForgeNetwork.isModdedPayload(this.packet);
      }

      @Nullable
      public ConnectionType getConnectionType() {
         return NeoForgeNetwork.getConnectionType(this.connection);
      }

      public boolean isNeoForge() {
         return NeoForgeNetwork.isNeoForge(this.connection);
      }
   }

   public static class Send extends Cancellable {
      public Packet<?> packet;
      public Connection connection;

      public Send(Packet<?> packet, Connection connection) {
         this.setCancelled(false);
         this.packet = packet;
         this.connection = connection;
      }

      @Nullable
      public ResourceLocation getPayloadId() {
         return NeoForgeNetwork.getPayloadId(this.packet);
      }

      public boolean isCustomPayload() {
         return this.getPayloadId() != null;
      }

      public boolean isModdedPayload() {
         return NeoForgeNetwork.isModdedPayload(this.packet);
      }

      @Nullable
      public ConnectionType getConnectionType() {
         return NeoForgeNetwork.getConnectionType(this.connection);
      }

      public boolean isNeoForge() {
         return NeoForgeNetwork.isNeoForge(this.connection);
      }
   }

   public static class Sent {
      public Packet<?> packet;
      public Connection connection;

      public Sent(Packet<?> packet, Connection connection) {
         this.packet = packet;
         this.connection = connection;
      }

      @Nullable
      public ResourceLocation getPayloadId() {
         return NeoForgeNetwork.getPayloadId(this.packet);
      }

      public boolean isCustomPayload() {
         return this.getPayloadId() != null;
      }

      public boolean isModdedPayload() {
         return NeoForgeNetwork.isModdedPayload(this.packet);
      }

      @Nullable
      public ConnectionType getConnectionType() {
         return NeoForgeNetwork.getConnectionType(this.connection);
      }

      public boolean isNeoForge() {
         return NeoForgeNetwork.isNeoForge(this.connection);
      }
   }
}
