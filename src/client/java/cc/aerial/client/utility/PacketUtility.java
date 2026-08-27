package cc.aerial.client.utility;

import cc.aerial.client.mixin.ClientCommonPacketListenerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;

public final class PacketUtility {
    private PacketUtility() {
    }

    public static void sendNoEvent(Packet<?> packet) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return;
        }
        ((ClientCommonPacketListenerAccessor) connection).aerial$getConnection().send(packet, null, true);
    }
}
