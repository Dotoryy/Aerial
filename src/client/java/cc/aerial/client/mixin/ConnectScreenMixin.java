package cc.aerial.client.mixin;

import cc.aerial.client.event.EventDispatcher;
import cc.aerial.client.screen.server.LoadingCard;
import cc.aerial.client.event.impl.game.server.ServerConnectEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import io.netty.channel.ChannelFuture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin extends Screen {
    protected ConnectScreenMixin(Component title) {
        super(title);
    }

    @Shadow
    private volatile boolean aborted;
    @Shadow
    private volatile Connection connection;
    @Shadow
    private ChannelFuture channelFuture;
    @Shadow
    @Final
    private Screen parent;
    @Shadow
    private Component status;

    private static final String CANCEL = "Cancel";

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void aerial$drawConnecting(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
                                        float partialTick, CallbackInfo ci) {
        ci.cancel();
        this.clearWidgets();
        LoadingCard.draw(extractor, width, height, "connecting",
                status == null ? null : status.getString(), -1.0f, CANCEL, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (LoadingCard.isActionHovered(width, height, event.x(), event.y(), CANCEL, true)) {
            aerial$abort();
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    private void aerial$abort() {
        aborted = true;
        if (channelFuture != null) {
            channelFuture.cancel(true);
            channelFuture = null;
        }
        if (connection != null) {
            connection.disconnect(ConnectScreen.ABORT_CONNECTION);
        }
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Inject(method = "startConnecting", at = @At("HEAD"), cancellable = true)
    private static void aerial$onConnect(Screen parent, Minecraft client, ServerAddress address,
                                          ServerData serverData, boolean bl, TransferState transferState,
                                          CallbackInfo ci) {
        ServerConnectEvent event = new ServerConnectEvent(address);
        EventDispatcher.dispatch(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }
}
