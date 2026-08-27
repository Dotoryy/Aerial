package cc.aerial.client.packet.delay;

import cc.aerial.client.event.EventDispatcher;
import cc.aerial.client.event.impl.game.PostGameTickEvent;
import cc.aerial.client.event.impl.game.packet.ReceivePacketEvent;
import cc.aerial.client.event.impl.game.server.ServerDisconnectEvent;
import cc.aerial.client.event.subscriber.IEventSubscriber;
import cc.aerial.client.event.subscriber.Subscribe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class DelayManager implements IEventSubscriber {
    public static final DelayManager INSTANCE = new DelayManager();

    private final Deque<Packet<?>> delayedPackets = new ConcurrentLinkedDeque<>();

    private volatile DelayModules delayModule = DelayModules.NONE;

    private volatile long delay;

    private DelayManager() {
        EventDispatcher.subscribe(this);
    }

    @Override
    public boolean isHandlingEvents() {
        return true;
    }

    public DelayModules getDelayModule() {
        return this.delayModule;
    }

    public long getDelay() {
        return this.delay;
    }

    public void delay(DelayModules module) {
        this.delayModule = module;
    }

    public void offer(Packet<?> packet) {
        this.delayedPackets.offer(packet);
    }

    public boolean shouldDelay(Packet<?> packet) {
        if (this.delayModule == DelayModules.NONE) {
            return false;
        }
        if (packet instanceof ClientboundKeepAlivePacket) {
            return false;
        }
        if (packet instanceof ClientboundLoginPacket || packet instanceof ClientboundRespawnPacket) {
            this.setDelayState(false, this.delayModule);
            return false;
        }
        if (packet instanceof ClientboundHurtAnimationPacket hurt) {
            LocalPlayer self = Minecraft.getInstance().player;
            if (self != null && hurt.id() != self.getId()) {
                return false;
            }
        }
        this.delayedPackets.offer(packet);
        return true;
    }

    public boolean setDelayState(boolean state, DelayModules module) {
        if (state) {
            this.delay = 0L;
            this.delayModule = module;
            return true;
        }

        this.delayModule = DelayModules.NONE;
        Packet<?> packet;
        while ((packet = this.delayedPackets.poll()) != null) {
            dispatch(packet);
        }

        this.delayedPackets.clear();
        return false;
    }

    public void drop() {
        this.delayModule = DelayModules.NONE;
        this.delayedPackets.clear();
    }

    private static void dispatch(Packet<?> packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isSameThread()) {
            handle(mc, packet);
        } else {
            mc.execute(() -> handle(mc, packet));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void handle(Minecraft mc, Packet<?> packet) {
        PacketListener listener = mc.getConnection();
        if (listener != null) {
            ((Packet) packet).handle(listener);
        }
    }

    @Subscribe(priority = Integer.MAX_VALUE)
    public void onReceivePacket(ReceivePacketEvent event) {
        if (this.shouldDelay(event.getPacket())) {
            event.setCancelled();
        }
    }

    @Subscribe(priority = Integer.MIN_VALUE + 1)
    public void onPostGameTick(PostGameTickEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;

        if (player == null || !player.isAlive()) {
            this.setDelayState(false, this.delayModule);
            return;
        }
        if (this.delayModule != DelayModules.NONE) {
            this.delay++;
        }
    }

    @Subscribe
    public void onServerDisconnect(ServerDisconnectEvent event) {
        this.drop();
    }
}
