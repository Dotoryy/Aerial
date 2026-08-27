package cc.aerial.client.mixin;

import cc.aerial.client.event.EventDispatcher;
import cc.aerial.client.event.impl.game.packet.InstantaneousSendPacketEvent;
import cc.aerial.client.event.impl.game.packet.ReceivePacketEvent;
import cc.aerial.client.event.impl.game.packet.SendPacketEvent;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @Unique
    private static boolean aerial$shouldDispatch() {
        return Minecraft.getInstance().player != null;
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void aerial$hookSendPacket(Packet<?> packet, CallbackInfo ci) {
        if (!aerial$shouldDispatch()) {
            return;
        }
        InstantaneousSendPacketEvent event = new InstantaneousSendPacketEvent(packet);
        EventDispatcher.dispatch(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void aerial$hookSendPacketListener(Packet<?> packet, @Nullable ChannelFutureListener listener,
                                                CallbackInfo ci) {
        if (!aerial$shouldDispatch()) {
            return;
        }
        SendPacketEvent event = new SendPacketEvent(packet);
        EventDispatcher.dispatch(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void aerial$hookReceivePacket(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        if (!aerial$shouldDispatch()) {
            return;
        }
        ReceivePacketEvent event = new ReceivePacketEvent(packet);
        EventDispatcher.dispatch(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }
}
