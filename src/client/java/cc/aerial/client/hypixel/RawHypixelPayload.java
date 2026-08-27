package cc.aerial.client.hypixel;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RawHypixelPayload(CustomPacketPayload.Type<RawHypixelPayload> type, byte[] data) implements CustomPacketPayload {
    public static StreamCodec<RegistryFriendlyByteBuf, RawHypixelPayload> codecFor(CustomPacketPayload.Type<RawHypixelPayload> type) {
        return CustomPacketPayload.codec(
                (payload, buf) -> buf.writeBytes(payload.data()),
                buf -> {
                    byte[] data = new byte[buf.readableBytes()];
                    buf.readBytes(data);
                    return new RawHypixelPayload(type, data);
                }
        );
    }
}
