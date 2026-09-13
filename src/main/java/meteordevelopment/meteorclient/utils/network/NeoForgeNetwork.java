package meteordevelopment.meteorclient.utils.network;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Central NeoForge networking utility providing native packet distribution,
 * channel registration, connection attribute inspection, and recursion-safe packet dispatch.
 */
public final class NeoForgeNetwork {
    private static final ThreadLocal<Boolean> SILENT_SEND = ThreadLocal.withInitial(() -> false);
    private static final List<Consumer<RegisterPayloadHandlersEvent>> REGISTRATION_LISTENERS = new CopyOnWriteArrayList<>();

    private NeoForgeNetwork() {}

    /**
     * Checks whether the current thread is performing a silent packet dispatch.
     */
    public static boolean isSilentSend() {
        return SILENT_SEND.get();
    }

    /**
     * Sends a packet through the connection while suppressing PacketEvent.Send / Sent posting,
     * preventing recursive event firing when spoofing or replacing packets.
     */
    public static void sendSilently(Connection connection, Packet<?> packet, @Nullable PacketSendListener listener, boolean flush) {
        if (connection == null || packet == null) return;
        boolean prev = SILENT_SEND.get();
        try {
            SILENT_SEND.set(true);
            connection.send(packet, listener, flush);
        } finally {
            SILENT_SEND.set(prev);
        }
    }

    /**
     * Sends a packet through the connection silently with flush=true.
     */
    public static void sendSilently(Connection connection, Packet<?> packet) {
        sendSilently(connection, packet, null, true);
    }

    /**
     * Sends one or more custom payloads to the server using native NeoForge PacketDistributor.
     */
    public static void sendToServer(CustomPacketPayload payload, CustomPacketPayload... payloads) {
        if (payload == null) return;
        try {
            PacketDistributor.sendToServer(payload, payloads);
        } catch (Throwable t) {
            ClientPacketListener listener = Minecraft.getInstance().getConnection();
            if (listener != null) {
                listener.send(payload);
                for (CustomPacketPayload other : payloads) {
                    if (other != null) {
                        listener.send(other);
                    }
                }
            }
        }
    }

    /**
     * Sends a regular packet to the server via ClientPacketListener.
     */
    public static void sendToServer(Packet<?> packet) {
        if (packet == null) return;
        ClientPacketListener listener = Minecraft.getInstance().getConnection();
        if (listener != null) {
            listener.send(packet);
        }
    }

    /**
     * Retrieves the ConnectionType of the given connection (NEOFORGE or OTHER).
     */
    @Nullable
    public static ConnectionType getConnectionType(@Nullable Connection connection) {
        if (connection == null) return null;
        try {
            return ChannelAttributes.getConnectionType(connection);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Returns true if the remote server is detected as a NeoForge server.
     */
    public static boolean isNeoForge(@Nullable Connection connection) {
        ConnectionType type = getConnectionType(connection);
        return type != null && type.isNeoForge();
    }

    /**
     * Checks whether the current connection supports a specific network channel/payload ID.
     */
    public static boolean hasChannel(@Nullable Connection connection, @Nullable ResourceLocation channelId) {
        if (connection == null || channelId == null) return false;
        try {
            ClientPacketListener listener = Minecraft.getInstance().getConnection();
            if (listener != null && listener.getConnection() == connection) {
                return NetworkRegistry.hasChannel(listener, channelId);
            }
            return NetworkRegistry.hasChannel(connection, null, channelId);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Returns known ad-hoc channels registered on the connection.
     */
    public static Set<ResourceLocation> getAdHocChannels(@Nullable Connection connection) {
        if (connection == null) return Collections.emptySet();
        try {
            return ChannelAttributes.getOrCreateAdHocChannels(connection);
        } catch (Throwable t) {
            return Collections.emptySet();
        }
    }

    /**
     * Extracts the payload ResourceLocation ID from a CustomPacketPayload packet.
     */
    @Nullable
    public static ResourceLocation getPayloadId(@Nullable Packet<?> packet) {
        if (packet instanceof ServerboundCustomPayloadPacket s) {
            return s.payload() != null && s.payload().type() != null ? s.payload().type().id() : null;
        } else if (packet instanceof ClientboundCustomPayloadPacket c) {
            return c.payload() != null && c.payload().type() != null ? c.payload().type().id() : null;
        }
        return null;
    }

    /**
     * Extracts the CustomPacketPayload instance from a packet if present.
     */
    @Nullable
    public static CustomPacketPayload getPayload(@Nullable Packet<?> packet) {
        if (packet instanceof ServerboundCustomPayloadPacket s) {
            return s.payload();
        } else if (packet instanceof ClientboundCustomPayloadPacket c) {
            return c.payload();
        }
        return null;
    }

    /**
     * Checks if the packet contains a modded custom payload.
     */
    public static boolean isModdedPayload(@Nullable Packet<?> packet) {
        CustomPacketPayload payload = getPayload(packet);
        if (payload == null) return false;
        try {
            return NetworkRegistry.isModdedPayload(payload);
        } catch (Throwable t) {
            ResourceLocation id = payload.type() != null ? payload.type().id() : null;
            return id != null && !"minecraft".equals(id.getNamespace());
        }
    }

    /**
     * Registers a listener to receive the RegisterPayloadHandlersEvent during mod setup.
     */
    public static void registerPayloadListener(Consumer<RegisterPayloadHandlersEvent> listener) {
        REGISTRATION_LISTENERS.add(listener);
    }

    /**
     * Mod event bus handler for RegisterPayloadHandlersEvent.
     */
    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        for (Consumer<RegisterPayloadHandlersEvent> listener : REGISTRATION_LISTENERS) {
            try {
                listener.accept(event);
            } catch (Throwable t) {
                MeteorClient.LOG.error("Failed to execute payload registration listener", t);
            }
        }
    }
}
