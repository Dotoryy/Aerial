package cc.aerial.client.mixin;

import cc.aerial.client.screen.server.AerialReconfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.ServerReconfigScreen;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerReconfigScreen.class)
public abstract class ServerReconfigScreenMixin extends Screen {
    protected ServerReconfigScreenMixin(Component title) {
        super(title);
    }

    @Shadow
    @Final
    private Connection connection;

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void aerial$replaceReconfig(CallbackInfo ci) {
        ci.cancel();
        Minecraft.getInstance().setScreenAndShow(new AerialReconfigScreen(this.title, connection));
    }
}
