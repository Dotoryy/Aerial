package cc.aerial.client.packet.blockage;

import net.minecraft.network.protocol.Packet;

@FunctionalInterface
public interface PacketValidator {
    boolean isValid(Packet<?> packet);
}
