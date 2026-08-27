package cc.aerial.client.event.impl.game.packet;

import cc.aerial.client.event.EventCancellable;
import net.minecraft.network.protocol.Packet;

public final class InstantaneousSendPacketEvent extends EventCancellable {
    private final Packet<?> packet;

    public InstantaneousSendPacketEvent(Packet<?> packet) {
        this.packet = packet;
    }

    public Packet<?> getPacket() {
        return packet;
    }
}
