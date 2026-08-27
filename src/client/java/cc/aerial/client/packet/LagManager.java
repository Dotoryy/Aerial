package cc.aerial.client.packet;

import cc.aerial.client.event.EventDispatcher;
import cc.aerial.client.event.impl.game.PostGameTickEvent;
import cc.aerial.client.event.impl.game.packet.InstantaneousSendPacketEvent;
import cc.aerial.client.event.impl.game.packet.SendPacketEvent;
import cc.aerial.client.event.impl.game.server.ServerConnectEvent;
import cc.aerial.client.event.impl.game.server.ServerDisconnectEvent;
import cc.aerial.client.event.subscriber.IEventSubscriber;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.utility.PacketUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.network.protocol.login.ServerboundKeyPacket;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import net.minecraft.network.protocol.status.ServerboundStatusRequestPacket;
import net.minecraft.world.phys.Vec3;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class LagManager implements IEventSubscriber {
    public static final LagManager INSTANCE = new LagManager();

    private final Deque<LagPacket> packetQueue = new ConcurrentLinkedDeque<>();
    private int tickDelay;
    private boolean flushing;
    private Vec3 lastPosition = Vec3.ZERO;

    private LagManager() {
        EventDispatcher.subscribe(this);
    }

    public void setDelay(int delay) {
        this.tickDelay = delay;
    }

    public int getDelay() {
        return tickDelay;
    }

    public Vec3 getLastPosition() {
        return lastPosition;
    }

    public boolean isFlushing() {
        return flushing;
    }

    public int getQueuedCount() {
        return packetQueue.size();
    }

    private void flushQueue() {
        if (Minecraft.getInstance().getConnection() == null) {
            packetQueue.clear();
            return;
        }
        flushing = true;

        for (; !packetQueue.isEmpty(); packetQueue.poll()) {
            LagPacket queued = packetQueue.peek();
            if (tickDelay > 0 && queued.delay <= tickDelay) {
                break;
            }
            PacketUtility.sendNoEvent(queued.packet);
            recordPosition(queued.packet);
        }
        flushing = false;
    }

    private void recordPosition(Packet<?> packet) {
        if (packet instanceof ServerboundMovePlayerPacket move && move.hasPosition()) {
            lastPosition = new Vec3(move.getX(0.0), move.getY(0.0), move.getZ(0.0));
        }
    }

    private static boolean isExempt(Packet<?> packet) {
        return packet instanceof ServerboundKeepAlivePacket || packet instanceof ServerboundChatPacket;
    }

    private static boolean isConnectionSetup(Packet<?> packet) {
        return packet instanceof ClientIntentionPacket
                || packet instanceof ServerboundHelloPacket
                || packet instanceof ServerboundKeyPacket
                || packet instanceof ServerboundStatusRequestPacket
                || packet instanceof ServerboundPingRequestPacket;
    }

    @Subscribe
    public void onSendPacket(InstantaneousSendPacketEvent event) {
        if (handleOutgoing(event.getPacket())) {
            event.setCancelled();
        }
    }

    @Subscribe
    public void onSendPacketWithListener(SendPacketEvent event) {
        if (handleOutgoing(event.getPacket())) {
            event.setCancelled();
        }
    }

    private boolean handleOutgoing(Packet<?> packet) {
        if (isConnectionSetup(packet)) {
            setDelay(0);
            return false;
        }

        if (Minecraft.getInstance().getConnection() == null) {
            reset();
            return false;
        }

        if (flushing) {
            return false;
        }

        flushQueue();

        if (isExempt(packet)) {
            return false;
        }
        if (tickDelay > 0) {
            packetQueue.offer(new LagPacket(packet));
            return true;
        }
        recordPosition(packet);
        return false;
    }

    @Subscribe
    public void onServerConnect(ServerConnectEvent event) {
        reset();
    }

    @Subscribe
    public void onServerDisconnect(ServerDisconnectEvent event) {
        reset();
    }

    private void reset() {
        packetQueue.clear();
        tickDelay = 0;
        lastPosition = Vec3.ZERO;
    }

    @Subscribe
    public void onPostGameTick(PostGameTickEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && !player.isAlive()) {
            setDelay(0);
        }
        packetQueue.forEach(queued -> queued.delay++);
        flushQueue();
    }

    private static final class LagPacket {
        private final Packet<?> packet;
        private int delay;

        private LagPacket(Packet<?> packet) {
            this.packet = packet;
        }
    }
}
