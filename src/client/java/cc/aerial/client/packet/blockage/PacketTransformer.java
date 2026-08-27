package cc.aerial.client.packet.blockage;

import net.minecraft.network.protocol.Packet;

@FunctionalInterface
public interface PacketTransformer {
    Packet<?> transform(Packet<?> packet);
}
