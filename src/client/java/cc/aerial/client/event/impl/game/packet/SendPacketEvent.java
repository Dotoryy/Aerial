package cc.aerial.client.event.impl.game.packet;

import cc.aerial.client.event.EventCancellable;
import net.minecraft.network.protocol.Packet;

public final class SendPacketEvent extends EventCancellable {
    private final Packet<?> packet;

    public SendPacketEvent(Packet<?> packet) {
        this.packet = packet;
    }

    public Packet<?> getPacket() {
        return packet;
    }
}
