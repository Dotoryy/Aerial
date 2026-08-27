package cc.aerial.client.hypixel;

import cc.aerial.client.event.EventDispatcher;
import cc.aerial.client.event.impl.game.server.ServerDisconnectEvent;
import cc.aerial.client.event.subscriber.IEventSubscriber;
import cc.aerial.client.event.subscriber.Subscribe;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.HypixelModAPIImplementation;
import net.hypixel.modapi.packet.HypixelPacket;
import net.hypixel.modapi.packet.PacketRegistry;
import net.hypixel.modapi.serializer.PacketSerializer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

public final class AerialHypixelTransport implements HypixelModAPIImplementation, IEventSubscriber {
    public static final AerialHypixelTransport INSTANCE = new AerialHypixelTransport();

    private final Map<String, CustomPacketPayload.Type<RawHypixelPayload>> serverboundTypes = new HashMap<>();
    private volatile boolean connected;

    private AerialHypixelTransport() {
    }

    public void register() {
        HypixelModAPI api = HypixelModAPI.getInstance();
        PacketRegistry registry = api.getRegistry();

        for (String channel : registry.getClientboundIdentifiers()) {
            CustomPacketPayload.Type<RawHypixelPayload> type = new CustomPacketPayload.Type<>(Identifier.parse(channel));
            PayloadTypeRegistry.clientboundPlay().register(type, RawHypixelPayload.codecFor(type));
            ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> {
                connected = true;
                api.handle(channel, new PacketSerializer(Unpooled.wrappedBuffer(payload.data())));
            });
        }

        for (String channel : registry.getServerboundIdentifiers()) {
            CustomPacketPayload.Type<RawHypixelPayload> type = new CustomPacketPayload.Type<>(Identifier.parse(channel));
            PayloadTypeRegistry.serverboundPlay().register(type, RawHypixelPayload.codecFor(type));
            serverboundTypes.put(channel, type);
        }

        api.setModImplementation(this);
        EventDispatcher.subscribe(this);
    }

    @Override
    public void onInit() {
    }

    @Override
    public boolean sendPacket(HypixelPacket packet) {
        String channel = packet.getIdentifier();
        CustomPacketPayload.Type<RawHypixelPayload> type = channel == null ? null : serverboundTypes.get(channel);
        if (type == null) {
            return false;
        }

        ByteBuf buf = Unpooled.buffer();
        packet.write(new PacketSerializer(buf));
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);

        ClientPlayNetworking.send(new RawHypixelPayload(type, data));
        return true;
    }

    @Override
    public boolean isConnectedToHypixel() {
        return connected;
    }

    @Subscribe
    public void onServerDisconnect(ServerDisconnectEvent event) {
        connected = false;
    }
}
