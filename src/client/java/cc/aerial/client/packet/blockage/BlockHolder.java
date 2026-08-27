package cc.aerial.client.packet.blockage;

import cc.aerial.client.event.EventDispatcher;
import cc.aerial.client.event.impl.game.packet.InstantaneousSendPacketEvent;
import cc.aerial.client.event.impl.game.packet.ReceivePacketEvent;
import cc.aerial.client.event.impl.game.server.ServerDisconnectEvent;
import cc.aerial.client.event.subscriber.IEventSubscriber;
import cc.aerial.client.event.subscriber.Subscribe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;

import java.util.ArrayDeque;
import java.util.Queue;

public final class BlockHolder implements IEventSubscriber {
    private final NetworkDirection direction;
    private final Queue<Packet<?>> queue = new ArrayDeque<>();
    private PacketTransformer transformer;
    private PacketValidator validator;
    private boolean active;

    public BlockHolder(NetworkDirection direction) {
        this.direction = direction;
        EventDispatcher.subscribe(this);
    }

    @Override
    public boolean isHandlingEvents() {
        return active;
    }

    public void block(PacketTransformer transformer, PacketValidator validator) {
        this.transformer = transformer;
        this.validator = validator;
        this.active = true;
    }

    public void block(PacketTransformer transformer) {
        block(transformer, null);
    }

    public void block() {
        block(null, null);
    }

    public boolean isBlocking() {
        return active;
    }

    public void release() {
        active = false;
        Packet<?> packet;
        while ((packet = queue.poll()) != null) {
            Packet<?> toSend = transformer != null ? transformer.transform(packet) : packet;
            if (toSend != null) {
                dispatch(toSend);
            }
        }
        transformer = null;
        validator = null;
    }

    public void drop() {
        active = false;
        queue.clear();
        transformer = null;
        validator = null;
    }

    @Subscribe
    public void onServerDisconnect(ServerDisconnectEvent event) {
        drop();
    }

    public void flush() {
        PacketTransformer savedTransformer = transformer;
        PacketValidator savedValidator = validator;
        release();
        block(savedTransformer, savedValidator);
    }

    private void dispatch(Packet<?> packet) {
        Minecraft mc = Minecraft.getInstance();
        if (direction == NetworkDirection.OUTBOUND) {
            ClientPacketListener connection = mc.getConnection();
            if (connection != null) {
                connection.send(packet);
            }
            return;
        }

        if (mc.isSameThread()) {
            handleInbound(mc, packet);
        } else {
            mc.execute(() -> handleInbound(mc, packet));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void handleInbound(Minecraft mc, Packet<?> packet) {
        PacketListener listener = mc.getConnection();
        if (listener != null) {
            ((Packet) packet).handle(listener);
        }
    }

    @Subscribe
    public void onInstantaneousSendPacket(InstantaneousSendPacketEvent event) {
        if (direction != NetworkDirection.OUTBOUND) {
            return;
        }
        if (validator == null || validator.isValid(event.getPacket())) {
            queue.add(event.getPacket());
            event.setCancelled();
        }
    }

    @Subscribe
    public void onReceivePacket(ReceivePacketEvent event) {
        if (direction != NetworkDirection.INBOUND) {
            return;
        }
        if (validator == null || validator.isValid(event.getPacket())) {
            queue.add(event.getPacket());
            event.setCancelled();
        }
    }
}
