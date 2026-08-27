package cc.aerial.client.mixin;

import cc.aerial.client.screen.server.AerialDisconnectScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisconnectedScreen.class)
public abstract class DisconnectedScreenMixin extends Screen {
    protected DisconnectedScreenMixin(Component title) {
        super(title);
    }

    @Shadow
    @Final
    private Screen parent;
    @Shadow
    @Final
    private DisconnectionDetails details;

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void aerial$replaceDisconnected(CallbackInfo ci) {
        ci.cancel();
        Minecraft.getInstance().setScreenAndShow(
                new AerialDisconnectScreen(parent, this.title, details.reason().getString()));
    }
}
